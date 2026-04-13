#!/usr/bin/env bash
#
# teardown-k3s-wasm.sh - Tear down the k3s WASM environment
#
# This script:
# 1. Destroys Pulumi resources
# 2. Removes the Pulumi stack
# 3. Stops and deletes the docker VM
# 4. Stops and deletes the k3s-wasm VM
#
# Usage:
#   ./teardown-k3s-wasm.sh [--keep-stack]
#
# Options:
#   --keep-stack   Keep the Pulumi stack (only destroy resources)

set -euo pipefail

DOCKER_PROFILE="docker"
VM_PROFILE="k3s-wasm"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# Destroy Pulumi resources
destroy_pulumi() {
    local keep_stack="$1"

    log "Destroying Pulumi resources..."

    # Set up environment for Pulumi
    export DOCKER_HOST="unix://$HOME/.colima/$DOCKER_PROFILE/docker.sock"
    export KUBECONFIG="$HOME/.kube/config"
    export PULUMI_CONFIG_PASSPHRASE="${PULUMI_CONFIG_PASSPHRASE:-}"
    export BESOM_SBT_MODULE=infra

    # Check if stack exists (dev or dev* for current stack)
    if pulumi stack ls 2>/dev/null | grep -qE "^dev[* ]"; then
        pulumi stack select dev 2>/dev/null || true
        pulumi destroy --yes || warn "Pulumi destroy failed (resources may already be gone)"

        if [[ "$keep_stack" != "true" ]]; then
            log "Removing Pulumi stack..."
            pulumi stack rm dev --yes --force 2>/dev/null || warn "Failed to remove stack"
        fi
    else
        log "No Pulumi stack found, skipping..."
    fi
}

# Delete docker VM
delete_docker_vm() {
    if colima list 2>/dev/null | grep -q "^$DOCKER_PROFILE "; then
        log "Stopping and deleting docker VM..."
        colima stop -p "$DOCKER_PROFILE" 2>/dev/null || true
        colima delete -p "$DOCKER_PROFILE" --force 2>/dev/null || warn "Failed to delete docker VM"
    else
        log "Docker VM not found, skipping..."
    fi
}

# Delete k3s-wasm VM
delete_k3s_vm() {
    if colima list 2>/dev/null | grep -q "^$VM_PROFILE "; then
        log "Stopping and deleting k3s-wasm VM..."
        colima stop -p "$VM_PROFILE" 2>/dev/null || true
        colima delete -p "$VM_PROFILE" --force 2>/dev/null || warn "Failed to delete k3s-wasm VM"
    else
        log "K3s-wasm VM not found, skipping..."
    fi
}

# Main
main() {
    local keep_stack=false

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --keep-stack)
                keep_stack=true
                shift
                ;;
            -h|--help)
                echo "Usage: $0 [--keep-stack]"
                echo
                echo "Options:"
                echo "  --keep-stack   Keep the Pulumi stack (only destroy resources)"
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    destroy_pulumi "$keep_stack"
    delete_docker_vm
    delete_k3s_vm

    log "Teardown complete!"
}

main "$@"
