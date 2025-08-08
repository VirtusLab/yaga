package example.proxy

import sttp.tapir._
import sttp.tapir.server.netty._
import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.client3.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import yaga.k8sservice.NettyFutureServerApp
import besom.json.*

import example.echo.EchoEndpoints

object ProxyService extends NettyFutureServerApp[Unit]:
  override def serverEndpoints(config: Unit): List[ServerEndpoint] =
    val echoServiceUrl = "http://echo-app-service:8080" // TODO Don't hardcode

    val backend = HttpURLConnectionBackend()

    val proxyEndpoint: PublicEndpoint[String, Unit, String, Any] =
      endpoint.post
        .in("proxy")
        .in(stringBody)
        .out(stringBody)

    val proxyServerEndpoint = proxyEndpoint.serverLogicSuccess { msg =>
      val request = SttpClientInterpreter()
        .toRequestThrowErrors(EchoEndpoints.echoEndpoint, Some(uri"$echoServiceUrl"))
        .apply(msg)

      Future {
        val response = request.send(backend)
        response.body
      }
    }

    List(proxyServerEndpoint)
