package example.products

import sttp.tapir.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import yaga.k8sservice.NettyFutureServerApp
import besom.json.*
import example.products.ProductsEndpoints.*

case class ServerConfig(
    myConfigValue: String
) derives JsonReader

object ProductService extends NettyFutureServerApp[ServerConfig]:

  // In-memory product data - matching Java service
  private val products: List[Product] = List(
    Product(1L, "Chicken"),
    Product(2L, "Lettuce"),
    Product(3L, "Tomato"),
    Product(4L, "Croutons")
  )

  private val newProducts: List[NewProduct] = List(
    NewProduct(1L, "Chicken", 1.65),
    NewProduct(2L, "Lettuce", 0.15),
    NewProduct(3L, "Tomato", 0.18),
    NewProduct(4L, "Croutons", 4.00)
  )

  // In-memory nutrition data - matching Java service
  private val nutritionInfos: List[NutritionInfo] = List(
    NutritionInfo(1L, "g", 1.65, 0.31, 0.036, 0),
    NutritionInfo(2L, "g", 0.15, 0.01, 0.002, 0.03),
    NutritionInfo(3L, "g", 0.18, 0.009, 0.002, 0.039),
    NutritionInfo(4L, "g", 4.00, 1.20, 0.16, 0.60)
  )

  // Service methods - matching Java service logic
  private def getProductById(productId: Long): Option[Product] =
    products.find(_.id == productId)

  private def getNewProductById(productId: Long): Option[NewProduct] =
    newProducts.find(_.id == productId)

  private def getNutritionInfo(productId: Long, unit: String): Option[NutritionInfo] =
    nutritionInfos.find(info => info.productId == productId && info.unit == unit)

  private def getNutritionInfos(productIds: List[Long]): List[NutritionInfo] =
    nutritionInfos.filter(info => productIds.contains(info.productId))

  override def serverEndpoints(config: ServerConfig): List[ServerEndpoint] =

    // GET /products/{productId}
    val getProductServerEndpoint = ProductsEndpoints.getProductEndpoint.serverLogic { productId =>
      Future.successful(
        getProductById(productId) match
          case Some(product) => Right(product)
          case None          => Left(())
      )
    }

    // GET /products/{productId}
    val getNewProductServerEndpoint = NewProductsEndpoints.getProductEndpoint.serverLogic { productId =>
      Future.successful(
        getNewProductById(productId) match
          case Some(product) => Right(product)
          case None          => Left(())
      )
    }

    // GET /products/{productId}/nutrition?unit={unit}
    val getNutritionInfoServerEndpoint = ProductsEndpoints.getNutritionInfoEndpoint.serverLogic {
      case (productId, unit) =>
        Future.successful(
          getNutritionInfo(productId, unit) match
            case Some(info) => Right(info)
            case None       => Left(())
        )
    }

    // GET /products/nutrition?productIds={ids}
    val getNutritionInfosServerEndpoint = ProductsEndpoints.getNutritionInfosEndpoint.serverLogicSuccess { productIds =>
      Future.successful(getNutritionInfos(productIds))
    }

    List(
      getProductServerEndpoint, // comment to trigger schema errors
      // getNewProductServerEndpoint, // uncomment to trigger schema errors
      getNutritionInfoServerEndpoint,
      getNutritionInfosServerEndpoint
    )
