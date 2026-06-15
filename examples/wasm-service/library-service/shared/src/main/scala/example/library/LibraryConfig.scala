package example.library

import io.circe.Codec as CirceCodec
import io.circe.generic.semiauto.*
import yaga.wasmservice.OpenApiServiceReference
import example.books.BooksEndpoints
import example.greeting.GreetingEndpoints

final case class LibraryConfig(
    booksRef: OpenApiServiceReference[BooksEndpoints.type],
    greetingRef: OpenApiServiceReference[GreetingEndpoints.type]
)

object LibraryConfig:
  // Relies on `OpenApiServiceReference.codec[A]` from the WASM client SDK.
  given CirceCodec[LibraryConfig] = deriveCodec

final case class LibrarySummary(count: Int, titles: List[String], greeting: Option[String] = None)

object LibrarySummary:
  given CirceCodec[LibrarySummary] = deriveCodec
