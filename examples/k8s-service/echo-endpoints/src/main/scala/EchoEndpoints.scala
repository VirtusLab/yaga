package example.echo

import sttp.tapir.*
import yaga.k8sservice.ExtractEndpoints

object EchoEndpoints derives ExtractEndpoints:
  val echoEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("echo")
      .in(stringBody)
      .out(stringBody)

// For debugging
object EchoEndpoints123 derives ExtractEndpoints:
  val echoEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("echo123")
      .in(stringBody)
      .out(stringBody)

  // given extractEndpoints: ExtractEndpoints[EchoEndpoints] = ExtractEndpoints.derived

// object EchoEndpoints extends EchoEndpoints
