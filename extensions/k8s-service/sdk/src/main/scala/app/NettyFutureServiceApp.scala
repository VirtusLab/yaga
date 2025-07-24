package yaga.k8sservice

import yaga.json.JsonReader
import yaga.k8sservice.internal.EnvReader

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.server.netty.NettyFutureServerBinding

trait NettyFutureServerApp[C : JsonReader](using ec: ExecutionContext)/* (using FutureServerEndpointExtractor[A]) */ extends ServiceApp[C]:
  type ServerEndpoint = sttp.tapir.server.ServerEndpoint[Any, Future]



  final override def main(args: Array[String]): Unit =
    val config = EnvReader.read[C](sys.env) match
      case Right(c) => c
      case Left(e) =>
        System.err.println(s"Failed to read config from environment: ${sys.env}")
        throw e

    // val endpoints = prepareEndpoints(config)
    // val extractedEndpoints: List[ServerEndpoint[Any, Future]] = summon[FutureServerEndpointExtractor[A]].allEndpoints(endpoints)
    val endpoints = serverEndpoints(config)
    val server = prepareServer(config)
    val serverWithEndpoints = server.addEndpoints(endpoints)

    val binding = serverWithEndpoints.start()
    handleBinding(binding)
  
  def prepareServer(config: C): NettyFutureServer =
    given ec: ExecutionContext = ExecutionContext.global

    NettyFutureServer()
      .port(8080)
      .host("0.0.0.0")

  def handleBinding(binding: Future[NettyFutureServerBinding]): Unit =
    given ec: ExecutionContext = ExecutionContext.global
    
    binding.foreach { _ =>
      println("Server running ...")
    }

    import scala.concurrent.duration.Duration
    import scala.concurrent.Await
    Await.result(binding, Duration.Inf)
