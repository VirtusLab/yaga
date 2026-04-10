package yaga.wasmservice

import scala.scalajs.wasi.cli.environment
import scala.util.Try

object WasmEnvReader:
  val configEnvVar = "YAGA_WASM_SERVICE_CONFIG"

  def envAsMap(): Map[String, String] =
    environment.getEnvironment().map(tp => tp._1 -> tp._2).toMap

  def configJson(): Option[String] =
    envAsMap().get(configEnvVar)
