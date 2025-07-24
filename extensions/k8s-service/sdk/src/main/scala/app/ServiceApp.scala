package yaga.k8sservice

import yaga.json.JsonReader
import yaga.k8sservice.internal.EnvReader


trait ServiceApp[C : JsonReader]:
  type ServerEndpoint <: sttp.tapir.server.ServerEndpoint[?, ?]

  def serverEndpoints(config: C): List[ServerEndpoint]

  def serverEndpointsOpenapiSpecYaml(): String =
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.apispec.openapi.circe.yaml.*

    val dummyConfig = null.asInstanceOf[C] // We assume that the endpoints' schema should not rely on the actual config available when running the server application
    val endpoints = serverEndpoints(dummyConfig).map(_.endpoint)

    val openApi = OpenAPIDocsInterpreter().toOpenAPI(
      endpoints, // your list of endpoints
      "Yaga service API",
      "" // dummy empty version
    )

    openApi.toYaml


  def main(args: Array[String]): Unit
