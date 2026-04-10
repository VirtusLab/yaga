package example.library

import io.circe.Codec as CirceCodec
import io.circe.generic.semiauto.*
import yaga.wasmservice.OpenApiServiceReference
import example.books.BooksEndpoints

// `booksRef` is a typed pointer to books-service. At wire level it's just
// `{"uri": "http://..."}`, but the type parameter carries the server's
// endpoint set so that the client side gets typed accessors
// (`booksClient.listBooks(...)`) and so that the infra project can prove,
// at compile time, that the server schema the runtime will talk to is
// still compatible with the one the client was built against
// (`SchemaCompatibility`).
final case class LibraryConfig(
    booksRef: OpenApiServiceReference[BooksEndpoints.type]
)

object LibraryConfig:
  // Relies on `OpenApiServiceReference.codec[A]` from the WASM client SDK.
  given CirceCodec[LibraryConfig] = deriveCodec

final case class LibrarySummary(count: Int, titles: List[String])

object LibrarySummary:
  given CirceCodec[LibrarySummary] = deriveCodec
