package example.books

import io.circe.Codec as CirceCodec
import io.circe.generic.semiauto.*

final case class BooksConfig(greeting: String)

object BooksConfig:
  given CirceCodec[BooksConfig] = deriveCodec
