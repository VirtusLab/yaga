package yaga.k8sservice

import scala.quoted.*
import sttp.tapir.Endpoint

trait EndpointExtractor[A]:
  def allEndpoints(a: A): List[Endpoint[?, ?, ?, ?, ?]]

object EndpointExtractor:

  inline def derived[A]: EndpointExtractor[A] = ${ derivedImpl[A] }

  private def derivedImpl[A : Type](using Quotes): Expr[EndpointExtractor[A]] = {
    import quotes.reflect.*
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]
    val tpe = TypeRepr.of[A]
    val endpoints = tpe.typeSymbol.fieldMembers.collect {
      case sym if sym.termRef <:< endpointTpe =>
        sym
    }
    '{
      new EndpointExtractor[A] {
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

  // private def memberEndpointsList(owner: Expr[?], members: List[Symbol])(using Quotes): Expr[List[Endpoint[?, ?, ?, ?, ?]]] = {
  //   import quotes.reflect.*
  //   // val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]
  //   val endpoints = members.map(x => x.termName).map(x => Expr(x.toString)).toList
  //   '{
  //     ${ endpoints }
  //   }
  // }

  inline def debug[A]: String = ${ debugImpl[A] }

  def debugImpl[A : Type](using Quotes): Expr[String] = {
    import quotes.reflect.*

    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]

    val tpe = TypeRepr.of[A]
    val tpeSym = tpe.typeSymbol
    val endpoints = tpeSym.fieldMembers.map { sym =>
      val ttpe = tpe.memberType(sym)//.termRef//.baseType()
      // val ttpe = sym.termRef//.baseType()
      // tpeSym.memberType(sym)
      // sym.tpe
      (sym, ttpe)
      // ttpe <:< endpointTpe
    }.filter(_._2 <:< endpointTpe)
    val endpointTpe1 = TypeRepr.of[Endpoint[Unit, Unit, Unit, Unit, String]]
    val result = endpoints.map((x, y) => (x, y.widen.dealias.simplified.show)).toString
    Expr(result)
  }
