package yaga.sbt.k8sservice

/** Specifies how WASM services are executed in Kubernetes.
  *
  * This is a mirror of `yaga.wasmservice.WasmRuntime` for use in the sbt plugin
  * (which is compiled with Scala 2.12 for sbt 1.x compatibility).
  */
sealed trait WasmRuntime

object WasmRuntime {

  /** Embedded wasmtime runtime inside a debian container.
    *
    * Produces a standard OCI container image with wasmtime installed. Works on any Kubernetes cluster without special
    * configuration. The container runs `wasmtime serve` as its entrypoint.
    *
    * This is the default and most compatible option.
    */
  case object EmbeddedWasmtime extends WasmRuntime

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
  case class RuntimeClass(className: String) extends WasmRuntime

  /** Default runtime: embedded wasmtime for maximum compatibility. */
  val default: WasmRuntime = EmbeddedWasmtime
}
