// IMPORTANT: this file is forked from
//   extensions/k8s-service/sdk-openapi/src/main/scala/ExtractEndpoints.scala
// and mirrored verbatim under
//   extensions/wasm-service/sdk-wasm-runtime/src/main/scala/yaga/wasmservice/ExtractEndpoints.scala
// Differences vs the k8s-service source:
//   - package is `yaga.wasmservice`
//   - emits OpenAPI as JSON (not YAML) — matches the rest of wasm-service
//     and avoids pulling `openapi-circe-yaml` onto the WASM classpath.
// Keep the three copies in sync.
package yaga.wasmservice

import scala.quoted.*
import sttp.tapir.Endpoint

trait ExtractEndpoints[A]:
  def allEndpoints(): List[Endpoint[?, ?, ?, ?, ?]]

  def endpointsOpenApiSpecJson(): String =
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.apispec.openapi.circe.*
    import io.circe.syntax.*
    val endpoints = allEndpoints()
    val openApi = OpenAPIDocsInterpreter().toOpenAPI(
      endpoints,
      "Yaga service API",
      ""
    )
    openApi.asJson.spaces2

object ExtractEndpoints:

  inline def derived[A]: ExtractEndpoints[A] = ${ derivedImpl[A] }

  // TODO handle exported members
  private def derivedImpl[A: Type](using Quotes): Expr[ExtractEndpoints[A]] = {
    import quotes.reflect.*
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]
    val tpe = TypeRepr.of[A]

    val module = Ref(tpe.typeSymbol.companionModule)

    val endpoints = tpe.typeSymbol.fieldMembers.collect {
      case sym if sym.termRef <:< endpointTpe =>
        sym
    }

    '{
      new ExtractEndpoints[A] {
        def allEndpoints(): List[Endpoint[?, ?, ?, ?, ?]] = {
          ${
            val endpointExprs = endpoints.map { sym =>
              module.select(sym).asExprOf[Endpoint[?, ?, ?, ?, ?]]
            }
            Expr.ofList(endpointExprs)
          }
        }
      }
    }
  }
