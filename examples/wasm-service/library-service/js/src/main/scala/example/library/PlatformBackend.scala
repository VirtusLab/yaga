package example.library

import sttp.client4.SyncBackend
import yaga.wasmservice.client.WasmSttpBackend

// JS/WASM-side backend — speaks `wasi:http/outgoing-handler@0.2.0`, so
// library-service can call books-service from inside wasmtime serve without
// going through a JVM TCP stack. The corresponding import must be present
// in `library-service/js/wit/world.wit`.
object PlatformBackend:
  def sync: SyncBackend = new WasmSttpBackend()
