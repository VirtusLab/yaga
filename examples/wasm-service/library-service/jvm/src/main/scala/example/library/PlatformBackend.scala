package example.library

import sttp.client4.SyncBackend
import sttp.client4.httpclient.HttpClientSyncBackend

// JVM-side backend — used only for local REPL exploration / JVM-side unit tests
// of `LibraryService.serverEndpoints`. The shipped WASM binary never loads this
// class; the `js/` source set supplies a WASI-HTTP backend with the same name.
object PlatformBackend:
  def sync: SyncBackend = HttpClientSyncBackend()
