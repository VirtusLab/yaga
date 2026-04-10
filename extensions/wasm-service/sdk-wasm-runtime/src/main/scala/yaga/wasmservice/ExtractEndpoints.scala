// IMPORTANT: this file is the WASM-runtime mirror of
//   extensions/wasm-service/sdk/src/main/scala/yaga/wasmservice/ExtractEndpoints.scala
// Keep the two copies in sync (same package, same shape, same JSON output).
// See header comment in the JVM copy for the rationale.
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
