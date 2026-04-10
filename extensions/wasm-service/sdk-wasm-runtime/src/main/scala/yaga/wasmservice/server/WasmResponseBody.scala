package yaga.wasmservice.server

import sttp.capabilities.WebSockets
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.Charset

case class WasmResponseBody(contentBytes: Array[Byte], contentType: Option[String])

class WasmToResponseBody extends ToResponseBody[WasmResponseBody, NoStreams]:
  override val streams: NoStreams = NoStreams

  override def fromRawValue[R](
      v: R,
      headers: HasHeaders,
      format: CodecFormat,
      bodyType: RawBodyType[R]
  ): WasmResponseBody =
    val contentType = format.mediaType.toString()

    val bytes: Array[Byte] = bodyType match
      case RawBodyType.StringBody(charset) =>
        v.asInstanceOf[String].getBytes(charset)
      case RawBodyType.ByteArrayBody =>
        v.asInstanceOf[Array[Byte]]
      case RawBodyType.ByteBufferBody =>
        val buffer = v.asInstanceOf[java.nio.ByteBuffer]
        val bytes = new Array[Byte](buffer.remaining())
        buffer.get(bytes)
        bytes
      case RawBodyType.InputStreamBody =>
        val is = v.asInstanceOf[java.io.InputStream]
        try is.readAllBytes()
        finally is.close()
      case RawBodyType.InputStreamRangeBody =>
        val is = v.asInstanceOf[java.io.InputStream]
        try is.readAllBytes()
        finally is.close()
      case RawBodyType.FileBody =>
        throw new UnsupportedOperationException("File body type not supported in WASM environment")
      case RawBodyType.MultipartBody(_, _) =>
        throw new UnsupportedOperationException("Multipart body type not supported in WASM environment")

    WasmResponseBody(bytes, Some(contentType))

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): WasmResponseBody =
    throw new UnsupportedOperationException("Streaming not supported in WASM environment")

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, ?, NoStreams]
  ): WasmResponseBody =
    throw new UnsupportedOperationException("WebSockets not supported in WASM environment")
