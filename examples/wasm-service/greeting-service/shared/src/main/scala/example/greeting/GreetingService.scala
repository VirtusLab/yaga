package example.greeting

import sttp.shared.Identity
import yaga.wasmservice.WasmServiceApp

object GreetingService extends WasmServiceApp[GreetingConfig]:
  override def serviceName: String = "greeting-service"
  override def serviceVersion: String = "0.1.0"

  override def serverEndpoints(config: GreetingConfig): List[Endpoint] =
    val handler =
      GreetingEndpoints.greet.serverLogicPure[Identity] { name =>
        val who = name.getOrElse(config.defaultName)
        Right(Greeting(s"Hello, $who!"))
      }

    List(handler)
