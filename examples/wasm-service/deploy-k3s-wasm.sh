#!/usr/bin/env bash
#
# deploy-k3s-wasm.sh - Set up a k3s cluster with WASM support using the patched runwasi shim
#
# This script:
# 1. Creates/starts a docker VM (for building/pushing images)
# 2. Creates/starts a k3s VM with containerd (for WASM workloads)
# 3. Builds the patched runwasi shim with wasmtime 43 (required for Scala-WASM gc/exceptions)
# 4. Installs the shim and configures containerd with registry mirror
# 5. Creates the RuntimeClass for WASM workloads
# 6. Starts a local Docker registry
# 7. Deploys the WASM services with Pulumi
#
# Prerequisites:
# - colima (brew install colima)
# - kubectl (brew install kubectl)
# - pulumi (brew install pulumi)
# - sbt (for building Scala WASM services)
#
# Usage:
#   ./deploy-k3s-wasm.sh [--rebuild] [--no-deploy] [--embedded]
#
# Options:
#   --rebuild     Force rebuild of the runwasi shim even if already installed
#   --no-deploy   Skip Pulumi deployment (only set up infrastructure)
#   --embedded    Use embedded wasmtime runtime (wasmtime-in-container) instead of RuntimeClass

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAGA_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RUNWASI_DIR="$YAGA_ROOT/vendor/runwasi"
WASM_SERVICE_DIR="$SCRIPT_DIR"
VM_PROFILE="k3s-wasm"
DOCKER_PROFILE="docker"
VM_CPUS=4
VM_MEMORY=8
VM_DISK=25

# Use docker from the docker profile
export DOCKER_HOST="unix://$HOME/.colima/$DOCKER_PROFILE/docker.sock"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
die() { error "$*"; exit 1; }

# Get VM's external IP from colima (socket_vmnet address)
get_vm_ip() {
    local profile="$1"
    colima list | awk -v p="$profile" '$1 == p { print $NF }'
}

# Check prerequisites
check_prereqs() {
    log "Checking prerequisites..."
    command -v colima >/dev/null 2>&1 || die "colima is required (brew install colima)"
    command -v kubectl >/dev/null 2>&1 || die "kubectl is required (brew install kubectl)"
    command -v docker >/dev/null 2>&1 || die "docker is required"
    [[ -d "$RUNWASI_DIR" ]] || die "runwasi not found at $RUNWASI_DIR"
}

# Start or create the docker VM (for building/pushing images)
start_docker_vm() {
    log "Starting docker VM '$DOCKER_PROFILE'..."

    if colima list | grep -q "^$DOCKER_PROFILE "; then
        if colima list | grep "^$DOCKER_PROFILE " | grep -q "Running"; then
            log "Docker VM '$DOCKER_PROFILE' is already running"
        else
            colima start -p "$DOCKER_PROFILE"
        fi
    else
        log "Creating new docker VM '$DOCKER_PROFILE'..."
        colima start -p "$DOCKER_PROFILE" \
            --cpu "$VM_CPUS" \
            --memory "$VM_MEMORY" \
            --disk "$VM_DISK" \
            --vm-type qemu \
            --network-address
    fi
}

# Start or create the k3s VM with containerd (for WASM workloads)
start_vm() {
    log "Starting k3s VM '$VM_PROFILE'..."

    if colima list | grep -q "$VM_PROFILE"; then
        if colima list | grep "$VM_PROFILE" | grep -q "Running"; then
            log "VM '$VM_PROFILE' is already running"
        else
            colima start -p "$VM_PROFILE"
        fi
    else
        log "Creating new VM '$VM_PROFILE' with containerd+k3s..."
        colima start -p "$VM_PROFILE" \
            --cpu "$VM_CPUS" \
            --memory "$VM_MEMORY" \
            --disk "$VM_DISK" \
            --vm-type qemu \
            --network-address \
            --runtime containerd \
            --kubernetes \
            --kubernetes-disable=traefik
    fi

    # Ensure k3s is running
    if ! vm_sudo "systemctl is-active k3s" >/dev/null 2>&1; then
        log "Starting k3s..."
        vm_sudo "systemctl start k3s"
    fi

    # Wait for k3s to be ready
    log "Waiting for k3s to be ready..."
    local retries=30
    while ! kubectl get nodes >/dev/null 2>&1; do
        retries=$((retries - 1))
        [[ $retries -gt 0 ]] || die "k3s failed to start"
        sleep 2
    done
    kubectl wait --for=condition=Ready node --all --timeout=120s
    log "k3s is ready"
}

# Run command in VM
vm_run() {
    colima -p "$VM_PROFILE" ssh -- bash -c "$*"
}

# Run command in VM as root
vm_sudo() {
    colima -p "$VM_PROFILE" ssh -- sudo bash -c "$*"
}

# Install build dependencies in VM
install_deps() {
    log "Installing build dependencies in VM..."
    vm_sudo "apt-get update -qq && apt-get install -y -qq build-essential pkg-config libseccomp-dev protobuf-compiler curl git" || \
        warn "Some packages may have failed to install, continuing..."

    # Check if Rust is installed
    if ! vm_run "source ~/.cargo/env 2>/dev/null; command -v rustc >/dev/null 2>&1"; then
        log "Installing Rust in VM..."
        vm_run "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain none"
    fi
    log "Build dependencies installed"
}

# Copy runwasi source to VM and build
build_shim() {
    local force_rebuild="${1:-false}"

    # Check if shim is already installed with correct version
    if [[ "$force_rebuild" != "true" ]]; then
        if vm_run "test -f /usr/local/bin/containerd-shim-wasmtime-v1" 2>/dev/null; then
            # Check if our patched version by looking for wasmtime 43 support
            # The old shim (wasmtime 36) would fail on our test modules
            log "Shim already installed, skipping build (use --rebuild to force)"
            return 0
        fi
    fi

    log "Packaging runwasi source..."
    local tarball="/tmp/runwasi-$$.tar.gz"
    tar czf "$tarball" -C "$YAGA_ROOT/vendor" --exclude='runwasi/target' runwasi

    log "Copying runwasi source to VM..."
    cat "$tarball" | colima -p "$VM_PROFILE" ssh -- bash -c 'cat > /tmp/runwasi.tar.gz'
    rm -f "$tarball"

    log "Extracting in VM..."
    # Extract as root, then chown to current user
    local vm_user
    vm_user=$(colima -p "$VM_PROFILE" ssh -- whoami)
    vm_sudo "rm -rf /opt/runwasi && tar xzf /tmp/runwasi.tar.gz -C /opt 2>/dev/null; chown -R $vm_user:$vm_user /opt/runwasi"

    log "Building runwasi shim (this may take several minutes)..."
    # Source cargo env and build
    vm_run "source ~/.cargo/env && cd /opt/runwasi && cargo build --release -p containerd-shim-wasmtime 2>&1" | \
        grep -E '(Compiling containerd-shim-wasmtime|Finished|error|warning:.*generated)' || true

    # Verify build succeeded
    vm_run "test -f /opt/runwasi/target/release/containerd-shim-wasmtime-v1" || \
        die "Build failed - shim binary not found"

    log "Shim built successfully"
}

# Install the shim and configure containerd
install_shim() {
    log "Installing shim..."

    # Temporarily disable strict error checking for cleanup commands
    set +e

    # Stop k3s and containerd
    log "Stopping k3s and containerd..."
    vm_sudo "systemctl stop k3s 2>/dev/null"
    vm_sudo "systemctl stop containerd 2>/dev/null"
    log "Services stopped"

    # Kill any lingering shim processes (ignore if none found)
    log "Killing old shims..."
    vm_sudo "pkill -9 -f containerd-shim-wasmtime 2>/dev/null"
    sleep 1
    log "Shims killed"

    # Re-enable strict error checking
    set -e

    # Copy the binary
    log "Copying shim binary..."
    vm_sudo "cp /opt/runwasi/target/release/containerd-shim-wasmtime-v1 /usr/local/bin/"
    vm_sudo "chmod +x /usr/local/bin/containerd-shim-wasmtime-v1"

    # Configure containerd (v2.0 with config version 3)
    # With colima --runtime containerd, containerd is a separate systemd service
    log "Configuring containerd for WASM..."
    vm_sudo "mkdir -p /etc/containerd"

    # Generate default config and add wasmtime runtime
    vm_sudo "/usr/local/bin/containerd config default > /etc/containerd/config.toml"

    # Add wasmtime runtime to containerd config
    vm_sudo "cat >> /etc/containerd/config.toml << 'CONTAINERD_EOF'

# Wasmtime runtime for WASM workloads
[plugins.'io.containerd.cri.v1.runtime'.containerd.runtimes.wasmtime]
  runtime_type = '/usr/local/bin/containerd-shim-wasmtime-v1'
  [plugins.'io.containerd.cri.v1.runtime'.containerd.runtimes.wasmtime.options]
CONTAINERD_EOF"

    # Configure registry mirror for docker VM
    # k3s-wasm needs to pull from the docker VM's registry via socket_vmnet
    log "Configuring registry mirror..."
    local docker_ip
    docker_ip=$(get_vm_ip "$DOCKER_PROFILE")

    # Update config_path in the registry section
    vm_sudo "sed -i \"s|config_path = ''|config_path = '/etc/containerd/certs.d'|\" /etc/containerd/config.toml"

    # Create registry mirror config for the docker VM's registry
    vm_sudo "mkdir -p /etc/containerd/certs.d/${docker_ip}:5000"
    vm_sudo "cat > /etc/containerd/certs.d/${docker_ip}:5000/hosts.toml << HOSTS_EOF
server = \"http://${docker_ip}:5000\"

[host.\"http://${docker_ip}:5000\"]
  capabilities = [\"pull\", \"resolve\"]
  skip_verify = true
HOSTS_EOF"

    # Restart containerd to pick up the new config
    log "Restarting containerd..."
    vm_sudo "systemctl restart containerd"
    sleep 2

    # Start k3s
    log "Starting k3s..."
    vm_sudo "systemctl start k3s"

    # Wait for k3s to be ready
    log "Waiting for k3s to be ready..."
    sleep 5
    local retries=30
    while ! kubectl get nodes >/dev/null 2>&1; do
        retries=$((retries - 1))
        [[ $retries -gt 0 ]] || die "k3s failed to restart"
        sleep 2
    done
    kubectl wait --for=condition=Ready node --all --timeout=120s

    log "Shim installed and k3s restarted"
}

# Create RuntimeClass for WASM workloads
create_runtime_class() {
    log "Creating RuntimeClass 'wasmtime'..."

    kubectl apply -f - <<'EOF'
apiVersion: node.k8s.io/v1
kind: RuntimeClass
metadata:
  name: wasmtime
handler: wasmtime
EOF

    log "RuntimeClass created"
}

# Start local docker registry (needed for pushing WASM images)
start_registry() {
    log "Ensuring local docker registry is running..."

    # Get docker VM's IP for insecure registry config
    local docker_ip
    docker_ip=$(get_vm_ip "$DOCKER_PROFILE")

    # Configure docker to allow insecure registry at its own IP
    log "Configuring docker for insecure registry..."
    colima -p "$DOCKER_PROFILE" ssh -- sudo bash -c "cat > /etc/docker/daemon.json << EOF
{
  \"exec-opts\": [\"native.cgroupdriver=cgroupfs\"],
  \"features\": {\"buildkit\": true},
  \"insecure-registries\": [\"${docker_ip}:5000\", \"localhost:5000\"]
}
EOF"
    colima -p "$DOCKER_PROFILE" ssh -- sudo systemctl restart docker
    sleep 2

    # Check if registry container exists
    if docker ps -a --format '{{.Names}}' | grep -q '^registry$'; then
        if ! docker ps --format '{{.Names}}' | grep -q '^registry$'; then
            docker start registry
        fi
    else
        docker run -d -p 5000:5000 --restart=always --name registry registry:2
    fi

    log "Registry running at $docker_ip:5000"
}

# Deploy WASM services with Pulumi
# Args: $1 = wasm_runtime ("embedded" or "runtimeclass")
deploy_services() {
    local wasm_runtime="$1"
    local docker_ip
    docker_ip=$(get_vm_ip "$DOCKER_PROFILE")

    log "Deploying WASM services with Pulumi (runtime: $wasm_runtime)..."

    # Set up environment for Pulumi
    export DOCKER_HOST="unix://$HOME/.colima/$DOCKER_PROFILE/docker.sock"
    export KUBECONFIG="$HOME/.kube/config"
    export REGISTRY_HOST="$docker_ip:5000"
    export PULUMI_CONFIG_PASSPHRASE="${PULUMI_CONFIG_PASSPHRASE:-}"
    export BESOM_SBT_MODULE=infra
    export WASM_RUNTIME="$wasm_runtime"

    cd "$WASM_SERVICE_DIR"

    # Clean codegen output to force regeneration with current WASM_RUNTIME setting.
    # The sbt codegen caching doesn't account for env var changes.
    log "Cleaning codegen output to ensure correct runtime mode..."
    rm -rf infra/target/scala-*/src_managed/main/yaga-wasm-service-codegen
    rm -rf target/yaga-wasm-docker-context

    # Check if stack exists, create if not (upsert)
    if ! pulumi stack ls 2>/dev/null | grep -qE "^dev[* ]"; then
        log "Creating Pulumi stack 'dev'..."
        pulumi stack init dev
    else
        log "Using existing Pulumi stack 'dev'..."
        pulumi stack select dev
    fi

    # Deploy
    log "Running pulumi up..."
    pulumi up --yes

    log "WASM services deployed successfully"
}

# Print summary
# Args: $1 = wasm_runtime ("embedded" or "runtimeclass")
print_summary() {
    local wasm_runtime="$1"
    local k3s_ip docker_ip
    k3s_ip=$(get_vm_ip "$VM_PROFILE")
    docker_ip=$(get_vm_ip "$DOCKER_PROFILE")

    echo
    log "=== Setup Complete ==="
    echo
    echo "Docker VM:      $DOCKER_PROFILE (IP: $docker_ip)"
    echo "K3s VM:         $VM_PROFILE (IP: $k3s_ip)"
    echo "Registry:       $docker_ip:5000"
    if [[ "$wasm_runtime" == "embedded" ]]; then
        echo "WASM Runtime:   embedded (wasmtime-in-container)"
    else
        echo "WASM Runtime:   runtimeclass (k8s RuntimeClass: wasmtime)"
    fi
    echo
    echo "To use this cluster:"
    echo "  export DOCKER_HOST=unix://\$HOME/.colima/$DOCKER_PROFILE/docker.sock"
    echo "  export KUBECONFIG=\$HOME/.kube/config"
    echo "  export REGISTRY_HOST=$docker_ip:5000"
    echo
    echo "Services:"
    echo "  books-service:   http://$k3s_ip:\$(kubectl -n wasm-demo get svc books-app-service -o jsonpath='{.spec.ports[0].nodePort}')/books"
    echo "  library-service: http://$k3s_ip:\$(kubectl -n wasm-demo get svc library-app-service -o jsonpath='{.spec.ports[0].nodePort}')/library/summary"
    echo
}

# Main
main() {
    local force_rebuild=false
    local skip_deploy=false
    local wasm_runtime="runtimeclass"

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --rebuild)
                force_rebuild=true
                shift
                ;;
            --no-deploy)
                skip_deploy=true
                shift
                ;;
            --embedded)
                wasm_runtime="embedded"
                shift
                ;;
            -h|--help)
                echo "Usage: $0 [--rebuild] [--no-deploy] [--embedded]"
                echo
                echo "Options:"
                echo "  --rebuild     Force rebuild of the runwasi shim"
                echo "  --no-deploy   Skip Pulumi deployment (only set up infrastructure)"
                echo "  --embedded    Use embedded wasmtime (wasmtime-in-container) instead of RuntimeClass"
                exit 0
                ;;
            *)
                die "Unknown option: $1"
                ;;
        esac
    done

    check_prereqs
    start_docker_vm
    start_vm
    install_deps
    build_shim "$force_rebuild"
    install_shim
    create_runtime_class
    start_registry

    if [[ "$skip_deploy" != "true" ]]; then
        deploy_services "$wasm_runtime"
    fi

    print_summary "$wasm_runtime"
}

main "$@"
