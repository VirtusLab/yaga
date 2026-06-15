package yaga.wasmservice

import besom.json.*

/*
 * This type is infrastructure specific. Application code should use [[yaga.wasmservice.ServiceReference]] instead.
 */

// TODO Use separate types for infrastructure and application code?
case class ServiceRef[A](
  uri: String
) /* derives JsonFormat */

object ServiceRef:
  // private case class ServiceRefStruct(
  //   url: String,
  // ) derives JsonFormat

  given jsonFormat[A]: JsonFormat[ServiceRef[A]] = JsonFormat.derived // JsonFormat.derived[ServiceRefStruct]//.contramap(_.uri)
