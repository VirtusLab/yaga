// IMPORTANT: this file is forked from
//   extensions/k8s-service/sdk-openapi-client/src/main/scala/OpenApiServiceReference.scala
// and mirrored verbatim under
//   extensions/wasm-service/sdk-client-wasm-runtime/src/main/scala/yaga/wasmservice/OpenApiServiceReference.scala
// Differences vs the k8s-service source:
//   - package is `yaga.wasmservice` (extends the wasm-service ServiceReference marker)
//   - JsonFormat (besom-json) replaced with circe Codec to match the rest of wasm-service.
// Keep the three copies in sync (k8s-service, wasm-service JVM, wasm-service WASM).
package yaga.wasmservice

import io.circe.{Codec, Decoder, Encoder, Json}
import sttp.tapir.{PublicEndpoint, Endpoint}
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.client4.*
import sttp.model.Uri
import scala.quoted.*
import scala.Selectable
import scala.language.implicitConversions
import sttp.tapir.DecodeResult

/*
 * This type is specific to business logic applications. Infrastructural code should use [[yaga.wasmservice.ServiceRef]] instead.
 */
case class OpenApiServiceReference[E](
    uri: String
) extends ServiceReference[E]:
  def toRequest(using interpreter: SttpClientInterpreter): ToRequestSelectable[E] =
    ToRequestSelectable[E](uri, interpreter)

  def toRequestThrowDecodeFailures(using interpreter: SttpClientInterpreter): ToRequestThrowDecodeFailuresSelectable[E] =
    ToRequestThrowDecodeFailuresSelectable[E](uri, interpreter)

  def toRequestThrowErrors(using interpreter: SttpClientInterpreter): ToRequestThrowErrorsSelectable[E] =
    ToRequestThrowErrorsSelectable[E](uri, interpreter)

object OpenApiServiceReference:
  given codec[A]: Codec[OpenApiServiceReference[A]] = Codec.from(
    Decoder.instance(c => c.get[String]("uri").map(OpenApiServiceReference[A](_))),
    Encoder.instance(r => Json.obj("uri" -> Json.fromString(r.uri)))
  )

trait RefinedToRequest[E]:
  type Refined

object RefinedToRequest:
  transparent inline given refinedToRequest[E]: RefinedToRequest[E] =
    ${ refinedToRequestImpl[E] }

  def refinedToRequestImpl[E: Type](using quotes: Quotes) =
    import quotes.reflect.*

    val tpe = TypeRepr.of[E]
    val publicEndpointTpe = TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]

    // Find all endpoint fields
    val endpointFields = tpe.typeSymbol.fieldMembers.collect {
      case sym if sym.termRef <:< publicEndpointTpe || sym.termRef <:< endpointTpe =>
        (sym.name, tpe.memberType(sym))
    }

    // Build refined type by adding each endpoint as a refinement
    val baseType = TypeRepr.of[ToRequestSelectable[E]]
    val refinedType = endpointFields.foldLeft(baseType) { case (acc, (fieldName, fieldType)) =>
      fieldType.asType match {
        case '[PublicEndpoint[i, e, o, c]] =>
          val functionType = TypeRepr.of[i => Request[DecodeResult[Either[e, o]]]]
          Refinement(acc, fieldName, functionType)
        case _ =>
          // Skip non-public endpoints for now
          acc
      }
    }

    refinedType.asType match {
      case '[t] => '{ new RefinedToRequest[E] { type Refined = t } }
    }

class ToRequestSelectable[E](uri: String, interpreter: SttpClientInterpreter) extends Selectable:
  transparent inline def selectDynamic(inline name: String): Any =
    ${ ToRequestSelectable.selectDynamicImpl[E]('name, 'uri, 'interpreter) }

object ToRequestSelectable:
  implicit inline def refineToRequest[E](selectable: ToRequestSelectable[E])(using rtr: RefinedToRequest[E]): rtr.Refined =
    selectable.asInstanceOf[rtr.Refined]
  def selectDynamicImpl[E: Type](
      nameExpr: Expr[String],
      uriExpr: Expr[String],
      interpreterExpr: Expr[SttpClientInterpreter]
  )(using Quotes): Expr[Any] =
    import quotes.reflect.*

    val name = nameExpr.valueOrAbort
    val publicEndpointTpe = TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]
    val tpe = TypeRepr.of[E]
    val module = Ref(tpe.typeSymbol.companionModule)

    // Find all endpoint fields matching the name
    val matchingEndpoints = tpe.typeSymbol.fieldMembers.collect {
      case sym if (sym.termRef <:< publicEndpointTpe || sym.termRef <:< endpointTpe) && sym.name == name =>
        sym
    }

    matchingEndpoints.headOption match {
      case Some(endpointSym) =>
        val endpointSelect = module.select(endpointSym)
        val endpointTpe = endpointSelect.tpe.widen

        // Check if it's a PublicEndpoint or secure Endpoint
        if (endpointTpe <:< TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]) {
          endpointTpe.asType match {
            case '[PublicEndpoint[i, e, o, c]] =>
              '{
                val endpoint = ${ endpointSelect.asExprOf[PublicEndpoint[i, e, o, Any]] }
                val baseUri = Uri.unsafeParse($uriExpr)
                $interpreterExpr.toRequest(endpoint, Some(baseUri))
              }
            case _ =>
              report.errorAndAbort(s"Unexpected public endpoint type: ${endpointTpe.show}")
          }
        } else {
          report.errorAndAbort(s"Secure endpoints not yet supported. Found: ${endpointTpe.show}")
        }

      case None =>
        report.errorAndAbort(s"No endpoint named '$name' found in ${tpe.show}")
    }

trait RefinedToRequestThrowDecodeFailures[E]:
  type Refined

object RefinedToRequestThrowDecodeFailures:
  transparent inline given refinedToRequestThrowDecodeFailures[E]: RefinedToRequestThrowDecodeFailures[E] =
    ${ refinedToRequestThrowDecodeFailuresImpl[E] }

  def refinedToRequestThrowDecodeFailuresImpl[E: Type](using quotes: Quotes) =
    import quotes.reflect.*

    val tpe = TypeRepr.of[E]
    val publicEndpointTpe = TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]

    // Find all endpoint fields
    val endpointFields = tpe.typeSymbol.fieldMembers.collect {
      case sym if sym.termRef <:< publicEndpointTpe || sym.termRef <:< endpointTpe =>
        (sym.name, tpe.memberType(sym))
    }

    // Build refined type by adding each endpoint as a refinement
    val baseType = TypeRepr.of[ToRequestThrowDecodeFailuresSelectable[E]]
    val refinedType = endpointFields.foldLeft(baseType) { case (acc, (fieldName, fieldType)) =>
      fieldType.asType match {
        case '[PublicEndpoint[i, e, o, c]] =>
          val functionType = TypeRepr.of[i => Request[Either[e, o]]]
          Refinement(acc, fieldName, functionType)
        case _ =>
          acc
      }
    }

    refinedType.asType match {
      case '[t] => '{ new RefinedToRequestThrowDecodeFailures[E] { type Refined = t } }
    }

class ToRequestThrowDecodeFailuresSelectable[E](uri: String, interpreter: SttpClientInterpreter) extends Selectable:
  transparent inline def selectDynamic(inline name: String): Any =
    ${ ToRequestThrowDecodeFailuresSelectable.selectDynamicImpl[E]('name, 'uri, 'interpreter) }

object ToRequestThrowDecodeFailuresSelectable:
  implicit inline def refineToRequestThrowDecodeFailures[E](selectable: ToRequestThrowDecodeFailuresSelectable[E])(using
      rtrtdf: RefinedToRequestThrowDecodeFailures[E]
  ): rtrtdf.Refined =
    selectable.asInstanceOf[rtrtdf.Refined]
  def selectDynamicImpl[E: Type](
      nameExpr: Expr[String],
      uriExpr: Expr[String],
      interpreterExpr: Expr[SttpClientInterpreter]
  )(using Quotes): Expr[Any] =
    import quotes.reflect.*

    val name = nameExpr.valueOrAbort
    val publicEndpointTpe = TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]
    val tpe = TypeRepr.of[E]
    val module = Ref(tpe.typeSymbol.companionModule)

    // Find all endpoint fields matching the name
    val matchingEndpoints = tpe.typeSymbol.fieldMembers.collect {
      case sym if (sym.termRef <:< publicEndpointTpe || sym.termRef <:< endpointTpe) && sym.name == name =>
        sym
    }

    matchingEndpoints.headOption match {
      case Some(endpointSym) =>
        val endpointSelect = module.select(endpointSym)
        val endpointTpe = endpointSelect.tpe.widen

        // Check if it's a PublicEndpoint or secure Endpoint
        if (endpointTpe <:< TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]) {
          endpointTpe.asType match {
            case '[PublicEndpoint[i, e, o, c]] =>
              '{
                val endpoint = ${ endpointSelect.asExprOf[PublicEndpoint[i, e, o, Any]] }
                val baseUri = Uri.unsafeParse($uriExpr)
                $interpreterExpr.toRequestThrowDecodeFailures(endpoint, Some(baseUri))
              }
            case _ =>
              report.errorAndAbort(s"Unexpected public endpoint type: ${endpointTpe.show}")
          }
        } else {
          report.errorAndAbort(s"Secure endpoints not yet supported. Found: ${endpointTpe.show}")
        }

      case None =>
        report.errorAndAbort(s"No endpoint named '$name' found in ${tpe.show}")
    }

trait RefinedToRequestThrowErrors[E]:
  type Refined

object RefinedToRequestThrowErrors:
  transparent inline given refinedToRequestThrowErrors[E]: RefinedToRequestThrowErrors[E] =
    ${ refinedToRequestThrowErrorsImpl[E] }

  def refinedToRequestThrowErrorsImpl[E: Type](using quotes: Quotes) =
    import quotes.reflect.*

    val tpe = TypeRepr.of[E]
    val publicEndpointTpe = TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]

    // Find all endpoint fields
    val endpointFields = tpe.typeSymbol.fieldMembers.collect {
      case sym if sym.termRef <:< publicEndpointTpe || sym.termRef <:< endpointTpe =>
        (sym.name, tpe.memberType(sym))
    }

    // Build refined type by adding each endpoint as a refinement
    val baseType = TypeRepr.of[ToRequestThrowErrorsSelectable[E]]
    val refinedType = endpointFields.foldLeft(baseType) { case (acc, (fieldName, fieldType)) =>
      fieldType.asType match {
        case '[PublicEndpoint[i, e, o, c]] =>
          val functionType = TypeRepr.of[i => Request[o]]
          Refinement(acc, fieldName, functionType)
        case _ =>
          acc
      }
    }

    refinedType.asType match {
      case '[t] => '{ new RefinedToRequestThrowErrors[E] { type Refined = t } }
    }

class ToRequestThrowErrorsSelectable[E](uri: String, interpreter: SttpClientInterpreter) extends Selectable:
  transparent inline def selectDynamic(inline name: String): Any =
    ${ ToRequestThrowErrorsSelectable.selectDynamicImpl[E]('name, 'uri, 'interpreter) }

object ToRequestThrowErrorsSelectable:
  implicit inline def refineToRequestThrowErrors[E](selectable: ToRequestThrowErrorsSelectable[E])(using
      rtrte: RefinedToRequestThrowErrors[E]
  ): rtrte.Refined =
    selectable.asInstanceOf[rtrte.Refined]
  def selectDynamicImpl[E: Type](
      nameExpr: Expr[String],
      uriExpr: Expr[String],
      interpreterExpr: Expr[SttpClientInterpreter]
  )(using Quotes): Expr[Any] =
    import quotes.reflect.*

    val name = nameExpr.valueOrAbort
    val publicEndpointTpe = TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]
    val endpointTpe = TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]
    val tpe = TypeRepr.of[E]
    val module = Ref(tpe.typeSymbol.companionModule)

    // Find all endpoint fields matching the name
    val matchingEndpoints = tpe.typeSymbol.fieldMembers.collect {
      case sym if (sym.termRef <:< publicEndpointTpe || sym.termRef <:< endpointTpe) && sym.name == name =>
        sym
    }

    matchingEndpoints.headOption match {
      case Some(endpointSym) =>
        val endpointSelect = module.select(endpointSym)
        val endpointTpe = endpointSelect.tpe.widen

        // Check if it's a PublicEndpoint or secure Endpoint
        if (endpointTpe <:< TypeRepr.of[PublicEndpoint[?, ?, ?, ?]]) {
          endpointTpe.asType match {
            case '[PublicEndpoint[i, e, o, c]] =>
              '{
                val endpoint = ${ endpointSelect.asExprOf[PublicEndpoint[i, e, o, Any]] }
                val baseUri = Uri.unsafeParse($uriExpr)
                $interpreterExpr.toRequestThrowErrors(endpoint, Some(baseUri))
              }
            case _ =>
              report.errorAndAbort(s"Unexpected public endpoint type: ${endpointTpe.show}")
          }
        } else {
          report.errorAndAbort(s"Secure endpoints not yet supported. Found: ${endpointTpe.show}")
        }

      case None =>
        report.errorAndAbort(s"No endpoint named '$name' found in ${tpe.show}")
    }
