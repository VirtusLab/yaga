package example.greeting

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import yaga.wasmservice.ExtractEndpoints

object GreetingEndpoints derives ExtractEndpoints:

  val greet: PublicEndpoint[Option[String], String, Greeting, Any] =
    endpoint.get
      .in("greet")
      .in(query[Option[String]]("name"))
      .errorOut(stringBody)
      .out(jsonBody[Greeting])
