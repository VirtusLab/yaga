package example.greeting

import io.circe.Codec as CirceCodec
import io.circe.generic.semiauto.*

final case class GreetingConfig(defaultName: String)

object GreetingConfig:
  given CirceCodec[GreetingConfig] = deriveCodec
