package example.greeting

import io.circe.Codec as CirceCodec
import io.circe.generic.semiauto.*

final case class Greeting(message: String)

object Greeting:
  given CirceCodec[Greeting] = deriveCodec
