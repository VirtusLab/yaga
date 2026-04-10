package testservice

import yaga.wasmservice.WasmServiceApp
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*
import sttp.shared.Identity

case class MyConfig(greeting: String, port: Int)
case class Book(title: String, author: String)

object TestService extends WasmServiceApp[MyConfig]:
  def serviceName: String = "test-service"
  def serviceVersion: String = "0.0.1"

  def serverEndpoints(config: MyConfig): List[Endpoint] =
    List(
      endpoint.get
        .in("books")
        .out(jsonBody[List[Book]])
        .serverLogicSuccess[Identity](_ => List.empty)
    )
