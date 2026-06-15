package example.books

import sttp.shared.Identity
import yaga.wasmservice.WasmServiceApp

object BooksService extends WasmServiceApp[BooksConfig]:
  override def serviceName: String = "books-service"
  override def serviceVersion: String = "0.1.0"

  // WARNING: `serverEndpoints` is also invoked by the codegen extractor with
  // `null.asInstanceOf[BooksConfig]` so it can derive an OpenAPI spec without a
  // real config. Do NOT read fields from `config` at the top level of this
  // method — only inside `serverLogic` / `serverLogicPure` closures that run
  // at request time.
  override def serverEndpoints(config: BooksConfig): List[Endpoint] =
    val seed = List(
      Book("The Hobbit", "J.R.R. Tolkien"),
      Book("Dune",       "Frank Herbert")
    )

    val listHandler =
      BooksEndpoints.listBooks.serverLogicPure[Identity] { _ => Right(seed) }

    val addHandler =
      BooksEndpoints.addBook.serverLogicPure[Identity] { book => Right(book) }

    List(listHandler, addHandler)
