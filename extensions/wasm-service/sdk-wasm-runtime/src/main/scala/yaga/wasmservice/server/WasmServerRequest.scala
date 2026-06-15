package yaga.wasmservice.server

import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.{AttributeKey, AttributeMap}

case class WasmServerRequest(
    method: Method,
    uri: Uri,
    _headers: Seq[Header],
    bodyBytes: Array[Byte],
    attributes: AttributeMap = AttributeMap.Empty
) extends ServerRequest:
  override def headers = scala.collection.immutable.Seq.apply(_headers*)
  override def protocol: String = "HTTP/1.1"
  override def connectionInfo: ConnectionInfo = ConnectionInfo.NoInfo
  override def underlying: Any = this

  override def pathSegments: List[String] =
    uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList

  override def queryParameters: QueryParams = uri.params

  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)

  override def attribute[T](k: AttributeKey[T], v: T): ServerRequest =
    copy(attributes = attributes.put(k, v))

  override def withUnderlying(underlying: Any): ServerRequest =
    require(underlying.isInstanceOf[WasmServerRequest])
    underlying.asInstanceOf[WasmServerRequest]

object WasmServerRequest:
  def fromRaw(
      method: String,
      pathWithQuery: String,
      headers: List[(String, String)],
      body: String
  ): WasmServerRequest =
    val parsedMethod = Method.unsafeApply(method)
    val parsedUri = Uri.unsafeParse(pathWithQuery)
    val parsedHeaders = headers.map { case (name, value) => Header.unsafeApply(name, value) }
    val bodyBytes = body.getBytes("UTF-8")
    WasmServerRequest(parsedMethod, parsedUri, parsedHeaders, bodyBytes)
