package yaga.wasmservice

import scala.quoted.*

import besom.types.Output

import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe.openAPIDecoder
import sttp.apispec.openapi.validation.OpenAPIComparator

trait Service:
  def serviceName: Output[Option[String]] // TODO Can we enforce just Output[String]?

  def asServiceRef[A](using thisSchema: ServerApiSchema[this.type], expectedSchema: ClientApiSchema[A])(using schemaCompatibility: SchemaCompatibility[thisSchema.Schema, expectedSchema.Schema]): Output[ServiceRef[A]] =
    for
      serviceName <- this.serviceName.map(_.getOrElse(throw new Exception("Service name is not defined"))) // TODO Better error handling
    yield ServiceRef[A](uri = s"http://${serviceName}:8080") // TODO Don't hardcode port number?


object Service:
  extension [S <: Service](service: Output[S])(using thisSchema: ServerApiSchema[S])
    def asServiceRef[A](using expectedSchema: ClientApiSchema[A])(using schemaCompatibility: SchemaCompatibility[thisSchema.Schema, expectedSchema.Schema]): Output[ServiceRef[A]] =
      // TODO Share implementation with unwrapped variant:
          // service.flatMap(_.asServiceRef[A])
      for
        s <- service
        serviceName <- s.serviceName.map(_.getOrElse(throw new Exception("Service name is not defined"))) // TODO Better error handling
      yield ServiceRef[A](uri = s"http://${serviceName}:8080") // TODO Don't hardcode port number?
