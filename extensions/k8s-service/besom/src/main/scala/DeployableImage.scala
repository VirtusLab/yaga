package yaga.k8sservice

import besom.*

// TODO Reintroduce type bound?
trait DeployableImage[A /* <: ServiceApp */ ]:
  def imageReference: Output[String]

object DeployableImage:
  extension [A <: DeployableImage[?]](image: Output[A]) def imageReference: Output[String] = image.flatMap(_.imageReference)
