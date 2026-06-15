package yaga.k8sservice

import yaga.json.JsonReader
import yaga.k8sservice.internal.EnvReader

trait OpenAPIServiceApp[C: JsonReader]:

  type ServerEndpoint <: sttp.tapir.server.ServerEndpoint[?, ?]

  def serviceName: String
  def serviceVersion: String

  def serverEndpoints(config: C): List[ServerEndpoint]

  def serverEndpointsOpenapiSpecYaml(): String =
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.apispec.openapi.circe.yaml.*

    // TODO needs improvement:
    // We assume that the endpoints' schema should not rely on the actual config available when running the server application
    val dummyConfig = null.asInstanceOf[C]
    val endpoints = serverEndpoints(dummyConfig).map(_.endpoint)

    val openApi = OpenAPIDocsInterpreter().toOpenAPI(
      endpoints,
      serviceName,
      serviceVersion
    )

    openApi.toYaml

  def main(args: Array[String]): Unit
