package yaga.k8sservice

import munit.FunSuite
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.client4.*
import sttp.model.Uri

class ServiceReferenceMacroTest extends FunSuite:

  given clientInterpreter: SttpClientInterpreter = SttpClientInterpreter()

  val testServiceRef = OpenApiServiceReference[TestEndpoints.type](uri = "http://localhost:8080")

  test("toRequest should return a selectable that can access stringEndpoint") {
    val selectable = testServiceRef.toRequest
    val requestFn = selectable.stringEndpoint
    val request = requestFn(())
    assert(request.uri.toString.contains("localhost:8080"))
    assert(request.uri.toString.contains("string"))
  }

  test("toRequest should return a selectable that can access bytesEndpoint") {
    val selectable = testServiceRef.toRequest
    val requestFn = selectable.bytesEndpoint
    val request = requestFn(())
    assert(request.uri.toString.contains("localhost:8080"))
    assert(request.uri.toString.contains("bytes"))
  }

  test("toRequestThrowDecodeFailures should return a selectable that can access stringEndpoint") {
    val selectable = testServiceRef.toRequestThrowDecodeFailures
    val requestFn = selectable.stringEndpoint
    val request = requestFn(())
    assert(request.uri.toString.contains("localhost:8080"))
    assert(request.uri.toString.contains("string"))
  }

  test("toRequestThrowDecodeFailures should return a selectable that can access bytesEndpoint") {
    val selectable = testServiceRef.toRequestThrowDecodeFailures
    val requestFn = selectable.bytesEndpoint
    val request = requestFn(())
    assert(request.uri.toString.contains("localhost:8080"))
    assert(request.uri.toString.contains("bytes"))
  }

  test("toRequestThrowErrors should return a selectable that can access stringEndpoint") {
    val selectable = testServiceRef.toRequestThrowErrors
    val requestFn = selectable.stringEndpoint
    val request = requestFn(())
    assert(request.uri.toString.contains("localhost:8080"))
    assert(request.uri.toString.contains("string"))
  }

  test("toRequestThrowErrors should return a selectable that can access bytesEndpoint") {
    val selectable = testServiceRef.toRequestThrowErrors
    val requestFn = selectable.bytesEndpoint
    val request = requestFn(())
    assert(request.uri.toString.contains("localhost:8080"))
    assert(request.uri.toString.contains("bytes"))
  }
