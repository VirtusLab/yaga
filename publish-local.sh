#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULES_DIR="$SCRIPT_DIR/modules"

# The special version we'll use for all locally published artifacts
VERSION="${1:-0.0.1-wasm-SNAPSHOT}"

echo "Publishing all modules locally with version: $VERSION"

# Helper: replace a val assignment in a file
# Usage: replace_val <file> <val_name> <new_value>
replace_val() {
  local file="$1" val_name="$2" new_value="$3"
  # Matches: val name = "..." or val name: String = "..."
  # Anchors on val_name followed by non-word char (space/colon/=) to avoid partial matches like circe vs circeRefined
  sed -i.bak -E "s|(val ${val_name})([^A-Za-z0-9_].*= *)\"[^\"]*\"|\1\2\"${new_value}\"|" "$file"
  rm -f "${file}.bak"
}

# Helper: run sbt in a module directory
# GPG_TTY is set so gpg-agent can use the cached passphrase without pinentry
# Usage: run_sbt <module_dir> <sbt_commands>
run_sbt() {
  local dir="$1"; shift
  echo ""
  echo "=============================="
  echo "  Publishing: $(basename "$dir")"
  echo "=============================="
  (cd "$dir" && GPG_TTY=$(tty) sbt "$@")
}

# ─── 1. cats (no deps) ───
run_sbt "$MODULES_DIR/wasm-cats" \
  "++3.3" "set ThisBuild / version := \"$VERSION\"" \
  "catsJS/publishLocal" "catsJVM/publishLocal"

# ─── 2. jawn (no deps) ───
run_sbt "$MODULES_DIR/wasm-jawn" \
  "++3.3" "set ThisBuild / version := \"$VERSION\"" \
  "rootJS/publishLocal" "rootJVM/publishLocal"

# ─── 3. circe (depends on cats, jawn) ───
CIRCE_BUILD="$MODULES_DIR/wasm-circe/build.sbt"
cp "$CIRCE_BUILD" "$CIRCE_BUILD.orig"
replace_val "$CIRCE_BUILD" "catsVersion" "$VERSION"
replace_val "$CIRCE_BUILD" "jawnVersion" "$VERSION"
run_sbt "$MODULES_DIR/wasm-circe" \
  "++3.3" "set ThisBuild / version := \"$VERSION\"" \
  "rootJS/publishLocal" "rootJVM/publishLocal"
mv "$CIRCE_BUILD.orig" "$CIRCE_BUILD"

# ─── 4. sttp-model (no deps) ───
run_sbt "$MODULES_DIR/wasm-sttp-model" \
  "set ThisBuild / version := \"$VERSION\"" \
  "coreJS3/publishLocal" "core3/publishLocal"

# ─── 5. sttp-shared (depends on sttp-model) ───
STTP_SHARED_BUILD="$MODULES_DIR/wasm-sttp-shared/build.sbt"
cp "$STTP_SHARED_BUILD" "$STTP_SHARED_BUILD.orig"
replace_val "$STTP_SHARED_BUILD" "sttpModelVersion" "$VERSION"
run_sbt "$MODULES_DIR/wasm-sttp-shared" \
  "set ThisBuild / version := \"$VERSION\"" \
  "coreJS3/publishLocal" "wsJS3/publishLocal" \
  "core3/publishLocal" "ws3/publishLocal"
mv "$STTP_SHARED_BUILD.orig" "$STTP_SHARED_BUILD"

# ─── 6. sttp-apispec (depends on circe) ───
STTP_APISPEC_BUILD="$MODULES_DIR/wasm-sttp-apispec/build.sbt"
cp "$STTP_APISPEC_BUILD" "$STTP_APISPEC_BUILD.orig"
replace_val "$STTP_APISPEC_BUILD" "circeVersion" "$VERSION"
run_sbt "$MODULES_DIR/wasm-sttp-apispec" \
  "set ThisBuild / version := \"$VERSION\"" \
  "openapiModelJS3/publishLocal" "asyncapiModelJS3/publishLocal" "apispecModelJS3/publishLocal" \
  "openapiCirceJS3/publishLocal" "jsonSchemaCirceJS3/publishLocal" \
  "openapiModel3/publishLocal" "asyncapiModel3/publishLocal" "apispecModel3/publishLocal" \
  "openapiCirce3/publishLocal" "jsonSchemaCirce3/publishLocal"
# NOTE: openapi-circe-yaml NOT published — wasm codegen uses JSON, not YAML,
# and circe-yaml-common:0.16.1 transitively pulls in circe-core:0.14.13 which
# evicts 0.0.1-wasm-SNAPSHOT. The project is JVM-only upstream anyway.
mv "$STTP_APISPEC_BUILD.orig" "$STTP_APISPEC_BUILD"

# ─── 7. sttp (depends on circe, sttp-model, sttp-shared) ───
STTP_BUILD="$MODULES_DIR/wasm-sttp/build.sbt"
cp "$STTP_BUILD" "$STTP_BUILD.orig"
replace_val "$STTP_BUILD" "circeVersion" "$VERSION"
replace_val "$STTP_BUILD" "sttpModelVersion" "$VERSION"
replace_val "$STTP_BUILD" "sttpSharedVersion" "$VERSION"
run_sbt "$MODULES_DIR/wasm-sttp" \
  "set ThisBuild / version := \"$VERSION\"" \
  "coreJS3/publishLocal" "jsonCommonJS3/publishLocal" "circeJS3/publishLocal" \
  "core3/publishLocal" "jsonCommon3/publishLocal" "circe3/publishLocal"
mv "$STTP_BUILD.orig" "$STTP_BUILD"

# ─── 8. tapir (depends on circe, sttp-model, sttp-shared, sttp-apispec) ───
#
# wasm-tapir has two sharp edges we work around to publish `tapir-sttp-client4`:
#
#  (a) The `sttpClient4` ProjectMatrix declares Test/Optional deps on
#      `sttp.client4 {fs2,zio,pekko-http-backend}` and `sttp.shared {fs2,zio,pekko,akka}`
#      — none of which exist in our WASM-forked publishes. sbt's `update` task resolves
#      those transitively even for `publishLocal`, so we strip them from the JVM + JS
#      platform blocks of sttpClient4 in `build.sbt` before publishing.
#
#  (b) The source tree under `client/sttp-client4/src/main/{scalajvm,scalajs}/.../ws/`
#      contains fs2/zio/pekko-http WebSocket helpers that reference
#      `sttp.capabilities.{fs2,zio,pekko}` — types our WASM-forked sttp-shared does not
#      expose. sbt's source scanner picks them up even if the dep block above is empty,
#      so we physically move those directories out of the project tree during publish
#      and restore them after.
#
# A trap handler restores all patched files and stashed dirs on any exit (success or
# failure) — idempotent, so it's safe even if the tapir step succeeds and a later step
# fails.

TAPIR_MODULE="$MODULES_DIR/wasm-tapir"
TAPIR_VERSIONS="$TAPIR_MODULE/project/Versions.scala"
TAPIR_BUILD="$TAPIR_MODULE/build.sbt"
TAPIR_WS_STASH="$(mktemp -d -t wasm-tapir-ws-stash.XXXXXX)"

# Source dirs under $TAPIR_MODULE that must be stashed before publishing sttpClient4.
# Each dir is moved into $TAPIR_WS_STASH keyed by a slash→underscore sanitized path,
# so scalajvm/.../ws/fs2 and scalajs/.../ws/fs2 don't collide.
TAPIR_WS_DIRS=(
  "client/sttp-client4/src/main/scalajvm/sttp/tapir/client/sttp4/ws/fs2"
  "client/sttp-client4/src/main/scalajvm/sttp/tapir/client/sttp4/ws/zio"
  "client/sttp-client4/src/main/scalajvm/sttp/tapir/client/sttp4/ws/pekkohttp"
  "client/sttp-client4/src/main/scalajs/sttp/tapir/client/sttp4/ws/fs2"
  "client/sttp-client4/src/main/scalajs/sttp/tapir/client/sttp4/ws/zio"
)

stash_key() { echo "$1" | tr '/' '_'; }

stash_tapir_ws_dir() {
  local rel="$1"
  local src="$TAPIR_MODULE/$rel"
  local dest="$TAPIR_WS_STASH/$(stash_key "$rel")"
  if [ -d "$src" ]; then
    mv "$src" "$dest"
  fi
}

unstash_tapir_ws_dir() {
  local rel="$1"
  local src="$TAPIR_MODULE/$rel"
  local stashed="$TAPIR_WS_STASH/$(stash_key "$rel")"
  if [ -d "$stashed" ]; then
    mkdir -p "$(dirname "$src")"
    mv "$stashed" "$src"
  fi
}

restore_tapir_state() {
  # Restore Versions.scala and build.sbt from their .orig backups.
  [ -f "$TAPIR_VERSIONS.orig" ] && mv "$TAPIR_VERSIONS.orig" "$TAPIR_VERSIONS" || true
  [ -f "$TAPIR_BUILD.orig" ]    && mv "$TAPIR_BUILD.orig" "$TAPIR_BUILD"       || true

  # Restore any still-stashed WebSocket dirs.
  local rel
  for rel in "${TAPIR_WS_DIRS[@]}"; do
    unstash_tapir_ws_dir "$rel"
  done

  # Clean up an empty stash dir.
  if [ -d "$TAPIR_WS_STASH" ]; then
    rmdir "$TAPIR_WS_STASH" 2>/dev/null || true
  fi
}
trap restore_tapir_state EXIT

# Patch Versions.scala to point every WASM-forked lib at $VERSION.
cp "$TAPIR_VERSIONS" "$TAPIR_VERSIONS.orig"
replace_val "$TAPIR_VERSIONS" "circe" "$VERSION"
replace_val "$TAPIR_VERSIONS" "sttpModel" "$VERSION"
replace_val "$TAPIR_VERSIONS" "sttpShared" "$VERSION"
replace_val "$TAPIR_VERSIONS" "sttpApispec" "$VERSION"
# `sttp4` powers `tapir-sttp-client4` — pin to the WASM-forked sttp4 publish.
replace_val "$TAPIR_VERSIONS" "sttp4" "$VERSION"

# Patch build.sbt: strip the sttpClient4 JVM + JS Test/Optional dep blocks.
# The sed range addresses pick out exactly the 7-line JVM block and 4-line JS block
# inside the sttpClient4 ProjectMatrix. Both start anchors are unique in the file
# (sttp.client4 fs2 %% Test vs %%% Test), and the first end anchor match after each
# start is the corresponding block's last line.
cp "$TAPIR_BUILD" "$TAPIR_BUILD.orig"
sed -i.bak -E \
  '/"com\.softwaremill\.sttp\.client4" %% "fs2" % Versions\.sttp4 % Test,/,/"org\.apache\.pekko" %% "pekko-stream" % Versions\.pekkoStreams % Optional/d' \
  "$TAPIR_BUILD"
sed -i.bak -E \
  '/"com\.softwaremill\.sttp\.client4" %%% "fs2" % Versions\.sttp4 % Test,/,/"com\.softwaremill\.sttp\.shared" %%% "zio" % Versions\.sttpShared % Optional/d' \
  "$TAPIR_BUILD"
rm -f "$TAPIR_BUILD.bak"

# Stash WebSocket source dirs.
for rel in "${TAPIR_WS_DIRS[@]}"; do
  stash_tapir_ws_dir "$rel"
done

# Publish. `clientCore` is a prerequisite of `sttpClient4` (dependsOn).
run_sbt "$TAPIR_MODULE" \
  "set ThisBuild / version := \"$VERSION\"" \
  "coreJS3/publishLocal" "serverCoreJS3/publishLocal" "circeJsonJS3/publishLocal" \
  "apispecDocsJS3/publishLocal" "openapiDocsJS3/publishLocal" \
  "clientCoreJS3/publishLocal" "sttpClient4JS3/publishLocal" \
  "core3/publishLocal" "serverCore3/publishLocal" "circeJson3/publishLocal" \
  "apispecDocs3/publishLocal" "openapiDocs3/publishLocal" \
  "clientCore3/publishLocal" "sttpClient43/publishLocal"

# restore_tapir_state runs on EXIT trap; nothing else to do here.

echo ""
echo "All modules published locally with version: $VERSION"

# ═══════════════════════════════════════════
#  Demo projects: compile + fastLinkJS
# ═══════════════════════════════════════════

# Helper: replace a literal dependency version string in a file
# Usage: replace_dep <file> <old_version> <new_version>
replace_dep() {
  local file="$1" old_version="$2" new_version="$3"
  sed -i.bak "s|${old_version}|${new_version}|g" "$file"
  rm -f "${file}.bak"
}

# ─── Demo 1: circe ───
DEMO_CIRCE_BUILD="$MODULES_DIR/wasm-demo-circe/build.sbt"
cp "$DEMO_CIRCE_BUILD" "$DEMO_CIRCE_BUILD.orig"
replace_val "$DEMO_CIRCE_BUILD" "circeVersion" "$VERSION"
run_sbt "$MODULES_DIR/wasm-demo-circe" "compile" "fastLinkJS"
mv "$DEMO_CIRCE_BUILD.orig" "$DEMO_CIRCE_BUILD"

# ─── Demo 2: sttp ───
DEMO_STTP_BUILD="$MODULES_DIR/wasm-demo-sttp/build.sbt"
cp "$DEMO_STTP_BUILD" "$DEMO_STTP_BUILD.orig"
replace_val "$DEMO_STTP_BUILD" "circeVersion" "$VERSION"
# sttp client and sttp-shared versions are inline — replace the specific snapshot strings
replace_dep "$DEMO_STTP_BUILD" "4.0.15+8-368e4fe1+20260225-1209-SNAPSHOT" "$VERSION"
replace_dep "$DEMO_STTP_BUILD" "1.5.0+70-e1c9b0e0+20260212-1925-SNAPSHOT" "$VERSION"
run_sbt "$MODULES_DIR/wasm-demo-sttp" "compile" "fastLinkJS"
mv "$DEMO_STTP_BUILD.orig" "$DEMO_STTP_BUILD"

# ─── Demo 3: tapir ───
DEMO_TAPIR_BUILD="$MODULES_DIR/wasm-demo-tapir/build.sbt"
cp "$DEMO_TAPIR_BUILD" "$DEMO_TAPIR_BUILD.orig"
replace_val "$DEMO_TAPIR_BUILD" "circeVersion" "$VERSION"
replace_val "$DEMO_TAPIR_BUILD" "tapirVersion" "$VERSION"
# openapi-circe version is inline
replace_dep "$DEMO_TAPIR_BUILD" "0.11.10+4-29ef900a+20260319-2248-SNAPSHOT" "$VERSION"
run_sbt "$MODULES_DIR/wasm-demo-tapir" "compile" "fastLinkJS"
mv "$DEMO_TAPIR_BUILD.orig" "$DEMO_TAPIR_BUILD"

## ─── wasm-service runtime SDKs (standalone scala-wasm builds, not in main yaga build) ───

# sdk-client-wasm-runtime: WasmSttpBackend + OpenApiServiceReference[E] for the WASM runtime.
# Standalone build because it requires the scala-wasm fork + sbt-scalajs 1.20.2-wasm.1-SNAPSHOT,
# which are incompatible with yaga's main build (Ground Rules 1, 4 in YAGA_WASM.md).
SDK_CLIENT_WASM_RUNTIME_DIR="$SCRIPT_DIR/extensions/wasm-service/sdk-client-wasm-runtime"
if [ -d "$SDK_CLIENT_WASM_RUNTIME_DIR" ]; then
  run_sbt "$SDK_CLIENT_WASM_RUNTIME_DIR" "publishLocal"
fi

echo ""
echo "All demos compiled. Run them with:"
echo "  wasmtime -Wgc,function-references,exceptions modules/wasm-demo-circe/target/scala-3.8.3-RC1-wasm-bin-SNAPSHOT/circe-demo-fastopt/main.wasm"
echo "  wasmtime -Wgc,function-references,exceptions -Shttp modules/wasm-demo-sttp/target/scala-3.8.3-RC1-wasm-bin-SNAPSHOT/zzz2-demo-fastopt/main.wasm"
echo "  wasmtime serve modules/wasm-demo-tapir/target/scala-3.8.3-RC1-wasm-bin-SNAPSHOT/zzz3-demo-fastopt/main.wasm -Wgc,function-references,exceptions"
