package example.proxy

import sttp.tapir._
import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.client4.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import besom.json.*
import yaga.k8sservice.NettyFutureServerApp
import yaga.k8sservice.ServiceReference

import example.echo.EchoEndpoints

case class ServerConfig(
    myConfigValue: String,
    echoService: ServiceReference[EchoEndpoints.type]
) derives JsonReader

object ProxyService extends NettyFutureServerApp[ServerConfig]:
  override def serverEndpoints(config: ServerConfig): List[ServerEndpoint] =
    lazy val echoServiceUrl = config.echoService.uri // lazy not to enforce evaluation of config in a dry run

    val backend: SyncBackend = DefaultSyncBackend()

    val proxyEndpoint: PublicEndpoint[String, Unit, String, Any] =
      endpoint.post
        .in("proxy")
        .in(stringBody)
        .out(stringBody)

    val proxyServerEndpoint = proxyEndpoint.serverLogicSuccess { msg =>

      // TODO refer to endpoints bundled with URL in a typesafe way

      val request = SttpClientInterpreter()
        .toRequestThrowErrors(EchoEndpoints.echoEndpoint, Some(uri"$echoServiceUrl"))
        .apply(msg)

      // val client: Int = SttpClientInterpreter()
      //   .toClientThrowErrors(EchoServiceEndpoints.echoEndpoint, Some(uri"$echoServiceUrl"), backend)

      Future {
        val response = request.send(backend)
        response.body
      }
    }

    List(proxyServerEndpoint)
