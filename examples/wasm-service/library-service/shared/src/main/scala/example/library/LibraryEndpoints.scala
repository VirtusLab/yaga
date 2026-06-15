package example.library

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object LibraryEndpoints:
  val summary: PublicEndpoint[Unit, String, LibrarySummary, Any] =
    endpoint.get
      .in("library" / "summary")
      .errorOut(stringBody)
      .out(jsonBody[LibrarySummary])
