package example.library

import sttp.shared.Identity
import sttp.tapir.client.sttp4.SttpClientInterpreter
import yaga.wasmservice.WasmServiceApp

object LibraryService extends WasmServiceApp[LibraryConfig]:
  override def serviceName: String    = "library-service"
  override def serviceVersion: String = "0.1.0"

  override def serverEndpoints(config: LibraryConfig): List[Endpoint] =
    val handler = LibraryEndpoints.summary.serverLogic[Identity] { _ =>
      given SttpClientInterpreter = SttpClientInterpreter()
      val backend      = PlatformBackend.sync
      val booksClient  = config.booksRef.toRequestThrowErrors
      val greetingClient = config.greetingRef.toRequestThrowErrors

      try
        val books = booksClient.listBooks(()).send(backend).body
        val greeting = greetingClient.greet(Some("reader")).send(backend).body
        Right(LibrarySummary(count = books.size, titles = books.map(_.title), greeting = Some(greeting.message)))
      catch
        case t: Throwable => Left(s"upstream failure: ${t.getMessage}")
    }

    List(handler)
