package yaga.wasmservice.server

import sttp.tapir.capabilities.NoStreams
import sttp.tapir.{RawBodyType, RawPart}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.{RawValue, RequestBody}

class WasmRequestBody extends RequestBody[sttp.shared.Identity, NoStreams]:
  override val streams: NoStreams = NoStreams

  override def toRaw[R](
      serverRequest: ServerRequest,
      bodyType: RawBodyType[R],
      maxBytes: Option[Long]
  ): RawValue[R] =
    val wasmRequest = serverRequest.underlying.asInstanceOf[WasmServerRequest]
    val bodyBytes = wasmRequest.bodyBytes

    maxBytes.foreach { max =>
      if bodyBytes.length > max then
        throw new IllegalArgumentException(
          s"Request body too large: ${bodyBytes.length} bytes, max allowed: $max bytes"
        )
    }

    val rawValue: R = bodyType match
      case RawBodyType.StringBody(charset) =>
        new String(bodyBytes, charset)
      case RawBodyType.ByteArrayBody =>
        bodyBytes
      case RawBodyType.ByteBufferBody =>
        throw new UnsupportedOperationException("ByteBuffer body type not supported in WASM environment")
      case RawBodyType.InputStreamBody =>
        throw new UnsupportedOperationException("InputStream body type not supported in WASM environment")
      case RawBodyType.InputStreamRangeBody =>
        throw new UnsupportedOperationException("InputStreamRange body type not supported in WASM environment")
      case RawBodyType.FileBody =>
        throw new UnsupportedOperationException("File body type not supported in WASM environment")
      case RawBodyType.MultipartBody(_, _) =>
        throw new UnsupportedOperationException("Multipart body type not supported in WASM environment")

    RawValue(rawValue)

  override def toStream(
      serverRequest: ServerRequest,
      maxBytes: Option[Long]
  ): streams.BinaryStream =
    throw new UnsupportedOperationException("Streaming not supported in WASM environment")
