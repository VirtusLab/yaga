package example.products

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import yaga.k8sservice.ExtractEndpoints
import sttp.model.StatusCode

// Domain models
case class Product(
    id: Long,
    name: String
)

object Product:
  given Encoder[Product] = deriveEncoder[Product]
  given Decoder[Product] = deriveDecoder[Product]

case class NewProduct(
    id: Long,
    name: String,
    calories: Double
)

object NewProduct:
  given Encoder[NewProduct] = deriveEncoder[NewProduct]
  given Decoder[NewProduct] = deriveDecoder[NewProduct]

case class NutritionInfo(
    productId: Long,
    unit: String,
    calories: Double,
    protein: Double,
    fat: Double,
    carbohydrates: Double
)

object NutritionInfo:
  given Encoder[NutritionInfo] = deriveEncoder[NutritionInfo]
  given Decoder[NutritionInfo] = deriveDecoder[NutritionInfo]

// Endpoint definitions
object ProductsEndpoints derives ExtractEndpoints:

  // GET /products/{productId}
  val getProductEndpoint: PublicEndpoint[Long, Unit, Product, Any] =
    endpoint.get
      .in("products" / path[Long]("productId"))
      .out(jsonBody[Product])
      .errorOut(statusCode(StatusCode.NotFound))

  // GET /products/{productId}/nutrition?unit={unit}
  val getNutritionInfoEndpoint: PublicEndpoint[(Long, String), Unit, NutritionInfo, Any] =
    endpoint.get
      .in("products" / path[Long]("productId") / "nutrition")
      .in(query[String]("unit"))
      .out(jsonBody[NutritionInfo])
      .errorOut(statusCode(StatusCode.NotFound))

  // GET /products/nutrition?productIds={ids}
  val getNutritionInfosEndpoint: PublicEndpoint[List[Long], Unit, List[NutritionInfo], Any] =
    endpoint.get
      .in("products" / "nutrition")
      .in(query[List[Long]]("productIds"))
      .out(jsonBody[List[NutritionInfo]])

object NewProductsEndpoints derives ExtractEndpoints:

  // GET /products/{productId}
  val getProductEndpoint: PublicEndpoint[Long, Unit, NewProduct, Any] =
    endpoint.get
      .in("products" / path[Long]("productId"))
      .out(jsonBody[NewProduct])
      .errorOut(statusCode(StatusCode.NotFound))

  // GET /products/{productId}/nutrition?unit={unit}
  val getNutritionInfoEndpoint: PublicEndpoint[(Long, String), Unit, NutritionInfo, Any] =
    endpoint.get
      .in("products" / path[Long]("productId") / "nutrition")
      .in(query[String]("unit"))
      .out(jsonBody[NutritionInfo])
      .errorOut(statusCode(StatusCode.NotFound))

  // GET /products/nutrition?productIds={ids}
  val getNutritionInfosEndpoint: PublicEndpoint[List[Long], Unit, List[NutritionInfo], Any] =
    endpoint.get
      .in("products" / "nutrition")
      .in(query[List[Long]]("productIds"))
      .out(jsonBody[List[NutritionInfo]])
