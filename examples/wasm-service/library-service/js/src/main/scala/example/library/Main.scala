package example.library

import scala.scalajs.wit.annotation.WitImplementation
import scala.scalajs.wasi.http.types.{IncomingRequest, ResponseOutparam}
import componentmodel.exports.wasi.http.IncomingHandler

import io.circe.parser.decode
import yaga.wasmservice.WasmEnvReader
import yaga.wasmservice.server.WasmServer

@WitImplementation
object Server extends IncomingHandler:
  override def handle(request: IncomingRequest, outParam: ResponseOutparam): Unit =
    val configJson = WasmEnvReader.configJson()
      .getOrElse(sys.error(s"Missing env var ${WasmEnvReader.configEnvVar}"))

    val config = decode[LibraryConfig](configJson).fold(throw _, identity)

    WasmServer.handleRequest(request, outParam, LibraryService.serverEndpoints(config))
