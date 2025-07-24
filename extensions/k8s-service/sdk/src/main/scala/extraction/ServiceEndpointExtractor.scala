package yaga.k8sservice

import scala.quoted.*
import sttp.tapir.Endpoint

trait ServiceEndpointExtractor[A]:
  def allEndpoints(a: A): List[Endpoint[?, ?, ?, ?, ?]]

object ServiceEndpointExtractor:

  inline def derived[A]: ServiceEndpointExtractor[A] = ${ derivedImpl[A] }

  private def derivedImpl[A : Type](using Quotes): Expr[ServiceEndpointExtractor[A]] = {
    import quotes.reflect.*
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]
    val tpe = TypeRepr.of[A]
    val endpoints = tpe.typeSymbol.fieldMembers.collect {
      case sym if sym.termRef <:< endpointTpe =>
        sym
    }
    '{
      new ServiceEndpointExtractor[A] {
        def allEndpoints(a: A): List[Endpoint[?, ?, ?, ?, ?]] = {
          ${
            val endpointExprs = endpoints.map { sym =>
              ('a).asTerm.select(sym).asExprOf[Endpoint[?, ?, ?, ?, ?]]
            }
            Expr.ofList(endpointExprs)
          }
        }
      }
    }
  }