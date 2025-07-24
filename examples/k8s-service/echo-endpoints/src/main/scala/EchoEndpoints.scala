package example.echo

import sttp.tapir.*

object EchoEndpoints:
  val echoEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("echo")
      .in(stringBody)
      .out(stringBody)