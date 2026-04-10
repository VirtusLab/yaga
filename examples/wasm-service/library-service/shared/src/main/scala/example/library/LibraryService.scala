package example.library

import sttp.shared.Identity
import sttp.tapir.client.sttp4.SttpClientInterpreter
import yaga.wasmservice.WasmServiceApp

object LibraryService extends WasmServiceApp[LibraryConfig]:
  override def serviceName: String    = "library-service"
  override def serviceVersion: String = "0.1.0"

  override def serverEndpoints(config: LibraryConfig): List[Endpoint] =
    val handler = LibraryEndpoints.summary.serverLogic[Identity] { _ =>
      // IMPORTANT: `config.booksRef` is only dereferenced INSIDE the request
      // handler. During codegen extraction `serverEndpoints` is invoked with
      // `null.asInstanceOf[LibraryConfig]`, so touching `config` at the top
      // level of this method would NPE at build time.
      given SttpClientInterpreter = SttpClientInterpreter()
      val backend      = PlatformBackend.sync
      val booksClient  = config.booksRef.toRequestThrowErrors

      try
        val books = booksClient.listBooks(()).send(backend).body
        Right(LibrarySummary(count = books.size, titles = books.map(_.title)))
      catch
        case t: Throwable => Left(s"upstream failure: ${t.getMessage}")
    }

    List(handler)
