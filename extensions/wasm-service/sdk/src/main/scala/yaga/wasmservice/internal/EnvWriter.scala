// TODO: Maybe reuse implementation from aws-lambda?

package yaga.wasmservice.internal

import scala.util.Try

import yaga.json.JsonWriter

object EnvWriter:
  val defaultEnvVariableName = "YAGA_WASM_SERVICE_CONFIG"

  def write[A : JsonWriter](value: A): Map[String, String] =
    val jsonStr = summon[JsonWriter[A]].write(value)
    Map(
      defaultEnvVariableName -> jsonStr
    )
