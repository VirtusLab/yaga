import sttp.tapir._
import sttp.tapir.server.netty._
import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.json.circe._
import io.circe.generic.auto._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Server {
  // Define the request and response data models
  case class Message(text: String)

  // Define the Tapir endpoint
  val echoEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.post
      .in("echo")
      .in(stringBody)
      .out(stringBody)

  def main(args: Array[String]): Unit = {
    // Define the server logic
    val echoServerEndpoint = echoEndpoint.serverLogic { msg =>
      Future.successful(Right(msg)) // Echo back the input
    }

    // Start the server on port 8080
    val binding = NettyFutureServer()
      .addEndpoint(echoServerEndpoint)
      .port(8080)
      .host("0.0.0.0")
      .start()

    // After starting, block main thread to prevent exit
    binding.foreach { _ =>
      println("🚀 Echo server running on http://localhost:8080/echo !!!!!")
    }
  }
}
