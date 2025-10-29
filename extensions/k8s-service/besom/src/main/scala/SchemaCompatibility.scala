package yaga.k8sservice

import scala.quoted.*

import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe.openAPIDecoder
import sttp.apispec.openapi.validation.OpenAPIComparator

class SchemaCompatibility[A, B]

object SchemaCompatibility:
  inline given compatibility[S1 <: String, S2 <: String]: SchemaCompatibility[S1, S2] = ${ compatibilityImpl[S1, S2] }

  // TODO Handle errors
  private def openapiFromYaml(yaml: String): OpenAPI =
    io.circe.yaml.parser.parse(yaml).flatMap(_.as[OpenAPI]) match {
      case Right(openapi) => openapi
      case Left(error)    => throw new IllegalArgumentException("Failed to parse OpenAPI YAML specification", error)
    }

  private def compatibilityImpl[S1 <: String: Type, S2 <: String: Type](using Quotes): Expr[SchemaCompatibility[S1, S2]] =
    import quotes.reflect.*

    val actualSchema = Type.valueOfConstant[S1].getOrElse(report.errorAndAbort(s"Type ${Type.show[S1]} is not a string literal type"))
    val expectedSchema = Type.valueOfConstant[S2].getOrElse(report.errorAndAbort(s"Type ${Type.show[S2]} is not a string literal type"))

    val serverOpenApi = openapiFromYaml(actualSchema)
    val clientOpenApi = openapiFromYaml(expectedSchema)

    val compatibilityIssues = OpenAPIComparator(clientOpenAPI = clientOpenApi, serverOpenAPI = serverOpenApi).compare()

    if compatibilityIssues.isEmpty then
      '{
        new SchemaCompatibility[S1, S2]
      }
    else
      val displayableCompatibilityIssues =
        compatibilityIssues.map(issue => s"  * ${fansi.Color.Red(issue.toString)}").mkString("\n")
      val displayableServerSchema = actualSchema.linesIterator.map(line => s"  ${line}").mkString("\n")
      val displayableClientSchema = expectedSchema.linesIterator.map(line => s"  ${line}").mkString("\n")
      val errorMessage = "The OpenAPI schemas are not compatible.\n" +
        s"Compatibility issues:\n${displayableCompatibilityIssues}\n\n" +
        s"Server schema:\n${displayableServerSchema}\n\n" +
        s"Client schema:\n${displayableClientSchema}\n\n"

      report.errorAndAbort(errorMessage)
