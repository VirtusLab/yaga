package yaga.k8sservice

import sttp.tapir.*

object TestEndpoints:
  val stringEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get
      .in("string")
      .out(stringBody)

  val bytesEndpoint: PublicEndpoint[Unit, Unit, Array[Byte], Any] =
    endpoint.post
      .in("bytes")
      .out(byteArrayBody)
