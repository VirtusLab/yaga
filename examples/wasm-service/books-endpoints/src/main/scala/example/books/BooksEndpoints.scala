package example.books

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import yaga.wasmservice.ExtractEndpoints

// `derives ExtractEndpoints` is what the codegen's schemaable-type extractor
// reflects on to derive the client-side OpenAPI spec for BooksEndpoints.
// Without it, the generated ClientApiSchema embeds a stub and SchemaCompatibility
// checks can't detect a real schema drift.
object BooksEndpoints derives ExtractEndpoints:

  val listBooks: PublicEndpoint[Unit, Unit, List[Book], Any] =
    endpoint.get
      .in("books")
      .out(jsonBody[List[Book]])

  val addBook: PublicEndpoint[Book, Unit, Book, Any] =
    endpoint.post
      .in("books")
      .in(jsonBody[Book])
      .out(jsonBody[Book])
