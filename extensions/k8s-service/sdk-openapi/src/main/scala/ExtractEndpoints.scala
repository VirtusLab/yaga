package yaga.k8sservice

import scala.quoted.*
import sttp.tapir.Endpoint

trait ExtractEndpoints[A]:
  def allEndpoints(): List[Endpoint[?, ?, ?, ?, ?]]

  def endpointsOpenApiSpecYaml(): String =
    import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
    import sttp.apispec.openapi.circe.yaml.*
    val endpoints = allEndpoints()
    val openApi = OpenAPIDocsInterpreter().toOpenAPI(
      endpoints,
      "Yaga service API",
      ""
    )
    openApi.toYaml

object ExtractEndpoints:

  inline def derived[A]: ExtractEndpoints[A] = ${ derivedImpl[A] }


  // TODO handle exported members
  private def derivedImpl[A : Type](using Quotes): Expr[ExtractEndpoints[A]] = {
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