package yaga.wasmservice

import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

trait WasmServiceApp[C]:
  type Endpoint = ServerEndpoint[Any, Identity]

  def serviceName: String
  def serviceVersion: String

  /** Build the server endpoints for this service.
    *
    * IMPORTANT: this method is ALSO called with `null.asInstanceOf[C]` at
    * build time by `serverEndpointsOpenapiSpecJson` so the codegen extractor
    * can derive an OpenAPI spec via reflection without a real config.
    *
    * Do NOT read fields from `config` outside of `serverLogic` / request
    * handling blocks — any field access during endpoint construction will
    * NPE when extraction runs. Treat `config` as usable only at request time.
    */
  def serverEndpoints(config: C): List[Endpoint]

  def serverEndpointsOpenapiSpecJson(): String =
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.apispec.openapi.circe.*
    import io.circe.syntax.*

    val dummyConfig = null.asInstanceOf[C]
    val endpoints = serverEndpoints(dummyConfig).map(_.endpoint)
    val openApi = OpenAPIDocsInterpreter().toOpenAPI(endpoints, serviceName, serviceVersion)
    openApi.asJson.spaces2
