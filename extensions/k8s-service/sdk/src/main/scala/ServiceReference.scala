package yaga.k8sservice

import besom.json.*

/*
 * This type is specific to business logic applications. Infrastructural code should use [[yaga.k8sservice.ServiceRef]] instead.
 */


case class ServiceReference[E /* <: Endpoints */](
  uri: String
) /* derives JsonFormat */

object ServiceReference:
  // private case class ServiceReferenceStruct(
  //   url: String,
  // ) derives JsonFormat

  given jsonFormat[A]: JsonFormat[ServiceReference[A]] = JsonFormat.derived // JsonFormat.derived[ServiceReferenceStruct]//.contramap(_.uri)