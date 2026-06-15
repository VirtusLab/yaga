package example.books

import scala.scalajs.wit.annotation.WitImplementation
import scala.scalajs.wasi.http.types.{IncomingRequest, ResponseOutparam}
import componentmodel.exports.wasi.http.IncomingHandler

import io.circe.parser.decode
import yaga.wasmservice.WasmEnvReader
import yaga.wasmservice.server.WasmServer

// `@WitImplementation` on the component entry point is the hook that scala-wasm
// uses to generate the `wasi:http/incoming-handler@0.2.0` export glue. Without
// this annotation there is no HTTP entry into the component.
@WitImplementation
object Server extends IncomingHandler:
  override def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit =
    val configJson = WasmEnvReader.configJson()
      .getOrElse(sys.error(s"Missing env var ${WasmEnvReader.configEnvVar}"))

    val config = decode[BooksConfig](configJson).fold(throw _, identity)

    WasmServer.handleRequest(request, outParam, BooksService.serverEndpoints(config))
