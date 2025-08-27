package example.echo

import sttp.tapir.*

trait EchoEndpoints:
  val echoEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("echo")
      .in(stringBody)
      .out(stringBody)

  // For debugging
  val echo1Endpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("echo123")
      .in(stringBody)
      .out(stringBody)

object EchoEndpoints extends EchoEndpoints
