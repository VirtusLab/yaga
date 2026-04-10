package yaga.wasmservice.server

import scala.collection.mutable
import scala.util.Try

import scala.scalajs.wasi.http.types.*
import scala.scalajs.wit
import scala.scalajs.WitUtils.toEither

import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.{Defaults, TapirFile}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, FilterServerEndpoints, ServerInterpreter}

case class WasmResponse(
    statusCode: Int,
    headers: Map[String, String],
    body: String
)

object WasmServer:
  private val requestBody = new WasmRequestBody()
  private val responseBody = new WasmToResponseBody()

  private given BodyListener[Identity, WasmResponseBody] =
    new BodyListener[Identity, WasmResponseBody]:
      override def onComplete(body: WasmResponseBody)(
          cb: Try[Unit] => Identity[Unit]
      ): Identity[WasmResponseBody] =
        cb(Try(()))
        body

  private val deleteFile: TapirFile => Identity[Unit] = _ => ()

  private implicit val identityMonad: sttp.monad.MonadError[Identity] = sttp.monad.IdentityMonad

  def handleRequest(
      request: IncomingRequest,
      outParam: ResponseOutparam,
      endpoints: List[ServerEndpoint[Any, Identity]]
  ): Unit =
    val method = request.method() match
      case other: Method.Other => other.value.toUpperCase
      case m => m.toString().toUpperCase

    val pathWithQuery = request.pathWithQuery().orElse("/")

    val requestHeaders: List[(String, String)] = request.headers().entries().map { entry =>
      entry._1 -> new String(entry._2, "UTF-8")
    }.toList

    val inputBody = (for
      body <- toEither(request.consume())
      inputStream <- toEither(body.stream())
    yield
      var eof = false
      val in = mutable.ArrayBuffer.empty[Byte]
      while !eof do
        toEither(inputStream.blockingRead(1024L)) match
          case Right(bytes) =>
            if bytes.length == 0 then eof = true
            else in ++= bytes
          case Left(_) =>
            eof = true
      new String(in.toArray, "UTF-8")
    ).getOrElse("")

    val serverRequest = WasmServerRequest.fromRaw(method, pathWithQuery, requestHeaders, inputBody)

    val filteredEndpoints = FilterServerEndpoints[Any, Identity](endpoints)
    val serverInterpreter = new ServerInterpreter[Any, Identity, WasmResponseBody, NoStreams](
      filteredEndpoints,
      requestBody,
      responseBody,
      List.empty,
      deleteFile
    )

    val wasmResponse = serverInterpreter(serverRequest) match
      case RequestResult.Response(resp, _) =>
        val headersMap = resp.headers.map(h => h.name -> h.value).toMap
        val bodyString = resp.body match
          case Some(wasmBodyValue) => new String(wasmBodyValue.contentBytes, "UTF-8")
          case None => ""
        WasmResponse(statusCode = resp.code.code, headers = headersMap, body = bodyString)
      case RequestResult.Failure(failuresList) =>
        WasmResponse(
          statusCode = 404,
          headers = Map("Content-Type" -> "text/plain"),
          body = s"Not Found: ${failuresList.headOption.map(_.failure.toString).getOrElse("No matching endpoint")}"
        )

    val responseHeaders = Fields.fromList(
      wasmResponse.headers.map { case (k, v) =>
        wit.Tuple2(k, v.getBytes("UTF-8"))
      }.toArray
    )
    val headers: Headers = toEither(responseHeaders).getOrElse(Fields())

    val resp = OutgoingResponse(headers)
    resp.setStatusCode(wasmResponse.statusCode.toShort)

    val body: OutgoingBody =
      toEither(resp.body()).getOrElse(throw new Error("failed to obtain outgoing response"))

    ResponseOutparam.set(outParam, new wit.Ok(resp))

    val out = toEither(body.write()).getOrElse(throw new Error("failed to get outgoing stream"))
    out.blockingWriteAndFlush(wasmResponse.body.getBytes("UTF-8"))
    out.close()

    toEither(OutgoingBody.finish(body, java.util.Optional.empty[Trailers]())).getOrElse(
      throw new Error("failed to finish outgoing body"))
