package yaga.k8sservice

import yaga.json.JsonReader
import yaga.k8sservice.internal.EnvReader

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.shared.Identity

trait NettySyncServerApp[C: JsonReader] extends OpenAPIServiceApp[C]:

  type ServerEndpoint = sttp.tapir.server.ServerEndpoint[Any, Identity]

  final override def main(args: Array[String]): Unit =
    val config = EnvReader.read[C](sys.env) match
      case Right(c) => c
      case Left(e) =>
        System.err.println(s"Failed to read config from environment: ${sys.env}")
        throw e

    val endpoints = serverEndpoints(config)
    val server = prepareServer(config)
    val serverWithEndpoints = server.addEndpoints(endpoints)

    serverWithEndpoints.startAndWait()

  def prepareServer(config: C): NettySyncServer =
    NettySyncServer()
      .port(8080)
      .host("0.0.0.0")
