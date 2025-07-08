package yaga.k8sservice

import yaga.json.JsonReader
import yaga.k8sservice.internal.EnvReader


trait ServiceApp[C : JsonReader]:
  def runService(runConfig: C): Unit

  final def main(args: Array[String]): Unit =
    val config = EnvReader.read[C](sys.env) match
      case Right(c) => c
      case Left(e) =>
        System.err.println(s"Failed to read config from environment: ${sys.env}")
        throw e

    runService(config)
