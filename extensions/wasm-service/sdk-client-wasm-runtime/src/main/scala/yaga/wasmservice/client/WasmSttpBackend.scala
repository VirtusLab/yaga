package yaga.wasmservice.client

// Library extraction of `modules/wasm-demo-sttp/src/main/scala/CustomBackend.scala`.
// Speaks `wasi:http/outgoing-handler@0.2.0` so that a WASM service can call another
// WASM/HTTP service from inside `wasmtime serve`. The carrier is the WASI HTTP API,
// not netty/JVM TCP, so this only works inside a WASM component runtime that imports
// `wasi:http/outgoing-handler@0.2.0`.

import sttp.client4.*
import sttp.model.*
import sttp.capabilities.Effect
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

import scala.scalajs.wit
import scala.scalajs.WitUtils.toEither
import scala.scalajs.wasi.http.outgoing_handler
import scala.scalajs.wasi.http.types.{Fields, Method, OutgoingBody, OutgoingRequest, Scheme}

import java.util.Optional

abstract class WasmSttpBackendBase extends GenericBackend[Identity, Any] with Backend[Identity]:

  type R = Any & Effect[Identity]

  override def send[T](request: GenericRequest[T, R]): Identity[Response[T]] =
    sendRegular(request)

  protected def sendRegular[T](request: GenericRequest[T, R]): Identity[Response[T]] =
    val methodStr = request.method.method
    val uri = request.uri
    val authority =
      uri.authority.map(a => s"${a.host}${a.port.map(p => s":$p").getOrElse("")}").getOrElse("")
    val pathWithQuery = uri.pathSegments.toString() + uri.params.toString(true)

    val headers: List[(String, String)] = request.headers.map(h => (h.name, h.value)).toList

    val bodyStr: String = request.body match
      case StringBody(s, _, _)   => s
      case NoBody                => ""
      case ByteArrayBody(_, _) =>
        throw new UnsupportedOperationException(
          s"WasmSttpBackend: ByteArrayBody requests are not supported (URI=$uri, method=$methodStr)"
        )
      case other =>
        throw new UnsupportedOperationException(
          s"WasmSttpBackend: request body type ${other.getClass.getName} not supported (URI=$uri, method=$methodStr)"
        )

    // Build the outgoing WASI request inline. Splitting this out into a helper buys
    // narrative clarity at the cost of obscuring the data flow — keep it open here.
    val outgoing = OutgoingRequest(
      headers = toEither(
        Fields.fromList(
          headers.map { case (k, v) => wit.Tuple2(k, stringToBytes(v)) }.toArray
        )
      ).toOption.get
    )

    outgoing.setMethod(
      methodStr match
        case "GET"     => Method.Get
        case "POST"    => Method.Post
        case "PUT"     => Method.Put
        case "DELETE"  => Method.Delete
        case "PATCH"   => Method.Patch
        case "HEAD"    => Method.Head
        case "OPTIONS" => Method.Options
        case other =>
          throw new UnsupportedOperationException(
            s"WasmSttpBackend: HTTP method '$other' not supported (URI=$uri)"
          )
    )

    outgoing.setPathWithQuery(Optional.of(pathWithQuery))
    outgoing.setScheme(Optional.of(Scheme.Http))
    outgoing.setAuthority(Optional.of(authority))

    if bodyStr.nonEmpty then
      outgoing.body() match
        case ok: wit.Ok[?] =>
          val body = ok.value
          body.write() match
            case ok2: wit.Ok[?] =>
              val os = ok2.value
              os.blockingWriteAndFlush(stringToBytes(bodyStr))
              os.close()
              OutgoingBody.finish(body, Optional.empty())
            case _: wit.Err[?] =>
              throw new RuntimeException(
                s"WasmSttpBackend WASI error: outgoing body write() failed (URI=$uri, method=$methodStr)"
              )
        case _: wit.Err[?] =>
          throw new RuntimeException(
            s"WasmSttpBackend WASI error: outgoing body() failed (URI=$uri, method=$methodStr)"
          )

    val resFutIncRes = outgoing_handler.handle(
      request = outgoing,
      options = Optional.empty()
    )

    val (statusCodeInt, responseHeadersRaw, responseBodyStr): (Int, List[(String, String)], String) =
      resFutIncRes match
        case ok: wit.Ok[?] =>
          val futIncRes = ok.value
          val pollable = futIncRes.subscribe()
          pollable.block()

          val resResIncRes = futIncRes.get().orElseThrow()
          resResIncRes match
            case ok2: wit.Ok[?] =>
              val resIncRes = ok2.value
              resIncRes match
                case ok3: wit.Ok[?] =>
                  val incRes = ok3.value
                  val status: Int = incRes.status().toInt
                  val resHeaders =
                    incRes.headers().entries().map { tp2 =>
                      val k = tp2._1
                      val v = tp2._2
                      k -> bytesToString(v)
                    }.toList

                  incRes.consume() match
                    case ok4: wit.Ok[?] =>
                      val incBody = ok4.value
                      incBody.stream() match
                        case ok5: wit.Ok[?] =>
                          val is = ok5.value
                          val sb = new StringBuilder
                          var running = true
                          while running do
                            is.blockingRead(1024) match
                              case ok6: wit.Ok[?] =>
                                sb.append(bytesToString(ok6.value))
                              case _: wit.Err[?] =>
                                running = false
                          (status, resHeaders, sb.toString)
                        case err: wit.Err[?] =>
                          throw new RuntimeException(
                            s"WasmSttpBackend WASI error: incoming body stream() failed (URI=$uri, method=$methodStr): ${err.value}"
                          )
                    case err: wit.Err[?] =>
                      throw new RuntimeException(
                        s"WasmSttpBackend WASI error: incoming response consume() failed (URI=$uri, method=$methodStr): ${err.value}"
                      )
                case err: wit.Err[?] =>
                  throw new RuntimeException(
                    s"WasmSttpBackend WASI error: incoming response error (URI=$uri, method=$methodStr): ${err.value}"
                  )
            case err: wit.Err[?] =>
              throw new RuntimeException(
                s"WasmSttpBackend WASI error: future response invariant violation (URI=$uri, method=$methodStr): ${err.value}"
              )
        case err: wit.Err[?] =>
          throw new RuntimeException(
            s"WasmSttpBackend WASI error: outgoing_handler.handle failed (URI=$uri, method=$methodStr): ${err.value}"
          )

    val statusCode = StatusCode(statusCodeInt)
    val responseHeaders = responseHeadersRaw.map { case (n, v) => Header(n, v) }
    val meta = ResponseMetadata(statusCode, statusCode.toString, responseHeaders)

    val responseBody = bodyFromResponseAs(request.response.delegate, responseBodyStr, meta)

    Response(
      body = responseBody,
      code = statusCode,
      statusText = statusCode.toString,
      headers = responseHeaders,
      history = List.empty,
      request = request.onlyMetadata
    )

  private def bodyFromResponseAs[T](
      responseAs: GenericResponseAs[T, ?],
      bodyString: String,
      meta: ResponseMetadata
  ): T =
    responseAs match
      case IgnoreResponse =>
        ().asInstanceOf[T]

      case ResponseAsByteArray =>
        bodyString.getBytes("UTF-8").asInstanceOf[T]

      case MappedResponseAs(raw, g, _) =>
        val baseResult = bodyFromResponseAs(raw, bodyString, meta)
        g(baseResult, meta).asInstanceOf[T]

      case rfm: ResponseAsFromMetadata[T, ?] @unchecked =>
        val selectedResponseAs = rfm(meta)
        bodyFromResponseAs(selectedResponseAs, bodyString, meta)

      case ResponseAsBoth(l, r) =>
        val leftResult = bodyFromResponseAs(l, bodyString, meta)
        val rightResult =
          try Some(bodyFromResponseAs(r, bodyString, meta))
          catch { case _: Exception => None }
        (leftResult, rightResult).asInstanceOf[T]

      case other =>
        throw new UnsupportedOperationException(s"Response type not supported: ${other.getClass.getName}")

  override def close(): Identity[Unit] = ()

  override def monad: MonadError[Identity] = IdentityMonad

  private def bytesToString(bytes: Array[Byte]): String = new String(bytes, "UTF-8")
  private def stringToBytes(str: String): Array[Byte]   = str.getBytes("UTF-8")

end WasmSttpBackendBase

class WasmSttpBackend extends WasmSttpBackendBase with SyncBackend:
  override def close(): Unit = ()
