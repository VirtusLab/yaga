package example.books

import io.circe.Codec as CirceCodec
import io.circe.generic.semiauto.*

final case class Book(title: String, author: String)

object Book:
  given CirceCodec[Book] = deriveCodec
