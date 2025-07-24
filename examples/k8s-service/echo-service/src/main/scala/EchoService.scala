package example.echo

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import io.circe.generic.auto.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import yaga.k8sservice.NettyFutureServerApp
import besom.json.*

case class ServerConfig(
  myConfigValue: String
) derives JsonReader

object EchoService extends NettyFutureServerApp[ServerConfig]:
  override def serverEndpoints(config: ServerConfig): List[ServerEndpoint] =
    val echoServerEndpoint = EchoEndpoints.echoEndpoint.serverLogic { msg =>
      Future.successful(Right(msg.toUpperCase)) // Echo back the input in upper case
    }

    List(echoServerEndpoint)
