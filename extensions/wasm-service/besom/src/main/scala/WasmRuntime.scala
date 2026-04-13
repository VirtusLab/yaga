package yaga.wasmservice

/** Specifies how WASM services are executed in Kubernetes.
  *
  * @see
  *   [[WasmRuntime.EmbeddedWasmtime]] for clusters without native WASM support
  * @see
  *   [[WasmRuntime.RuntimeClass]] for clusters with containerd WASM shims
  */
enum WasmRuntime:
  /** Embedded wasmtime runtime inside a debian container.
    *
    * Produces a standard OCI container image with wasmtime installed. Works on any Kubernetes cluster without special
    * configuration. The container runs `wasmtime serve` as its entrypoint.
    *
    * This is the default and most compatible option.
    */
  case EmbeddedWasmtime

  /** Native WASM runtime via Kubernetes RuntimeClass.
    *
    * Produces a minimal `FROM scratch` OCI image containing only the WASM binary. Requires the target Kubernetes
    * cluster to have:
    *   - containerd with a WASM shim (e.g., `containerd-shim-wasmtime-v1` from runwasi)
    *   - A `RuntimeClass` resource pointing to the WASM handler
    *
    * Example cluster setup:
    * {{{
    * apiVersion: node.k8s.io/v1
    * kind: RuntimeClass
    * metadata:
    *   name: wasmtime
    * handler: wasm
    * }}}
    *
    * @param className
    *   The name of the Kubernetes RuntimeClass (e.g., "wasmtime", "wasmedge", "spin")
    */
  case RuntimeClass(className: String)

object WasmRuntime:
  /** Default runtime: embedded wasmtime for maximum compatibility. */
  val default: WasmRuntime = EmbeddedWasmtime
