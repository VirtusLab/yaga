package example.recipes

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.client4.*
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import besom.json.*
import yaga.k8sservice.NettyFutureServerApp
import yaga.k8sservice.ServiceReference

import example.products.ProductsEndpoints

// Domain models for recipes service
case class Ingredient(
    id: Long,
    productId: Long,
    name: String,
    quantity: Double,
    unit: String
)

object Ingredient:
  given Encoder[Ingredient] = deriveEncoder[Ingredient]
  given Decoder[Ingredient] = deriveDecoder[Ingredient]

case class Recipe(
    id: Long,
    name: String,
    instructions: String,
    ingredients: List[Ingredient],
    servings: Int
)

object Recipe:
  given Encoder[Recipe] = deriveEncoder[Recipe]
  given Decoder[Recipe] = deriveDecoder[Recipe]

case class NutritionInfo(
    calories: Double,
    protein: Double,
    fat: Double,
    carbohydrates: Double
)

object NutritionInfo:
  given Encoder[NutritionInfo] = deriveEncoder[NutritionInfo]
  given Decoder[NutritionInfo] = deriveDecoder[NutritionInfo]

case class ServerConfig(
    myConfigValue: String,
    productService: ServiceReference[ProductsEndpoints.type]
) derives JsonReader

object RecipesService extends NettyFutureServerApp[ServerConfig]:

  // Hardcoded recipe data matching Java service
  private val recipes: List[Recipe] = List(
    Recipe(
      id = 1L,
      name = "Caesar Salad",
      instructions = "1. Grill the chicken\n2. Chop lettuce and tomatoes\n3. Mix ingredients\n4. Add croutons",
      ingredients = List(
        Ingredient(1L, 1L, "Chicken", 150.0, "g"),
        Ingredient(2L, 2L, "Lettuce", 100.0, "g"),
        Ingredient(3L, 3L, "Tomato", 50.0, "g"),
        Ingredient(4L, 4L, "Croutons", 20.0, "g")
      ),
      servings = 2
    )
  )

  override def serverEndpoints(config: ServerConfig): List[ServerEndpoint] =
    lazy val productServiceUrl = config.productService.uri

    val backend: SyncBackend = DefaultSyncBackend()

    // GET /recipes - return all recipes
    val getAllRecipesEndpoint: PublicEndpoint[Unit, Unit, List[Recipe], Any] =
      endpoint.get
        .in("recipes")
        .out(jsonBody[List[Recipe]])

    val getAllRecipesServerEndpoint = getAllRecipesEndpoint.serverLogicSuccess { _ =>
      Future.successful(recipes)
    }

    // GET /recipes/{id} - return single recipe (404 if not found)
    val getRecipeByIdEndpoint: PublicEndpoint[Long, Unit, Recipe, Any] =
      endpoint.get
        .in("recipes" / path[Long]("id"))
        .out(jsonBody[Recipe])

    val getRecipeByIdServerEndpoint = getRecipeByIdEndpoint.serverLogic { id =>
      Future.successful(
        recipes.find(_.id == id) match
          case Some(recipe) => Right(recipe)
          case None         => Left(())
      )
    }

    // GET /recipes/{id}/nutrition - calculate nutrition by calling product service
    val getRecipeNutritionEndpoint: PublicEndpoint[Long, Unit, NutritionInfo, Any] =
      endpoint.get
        .in("recipes" / path[Long]("id") / "nutrition")
        .out(jsonBody[NutritionInfo])

    val getRecipeNutritionServerEndpoint = getRecipeNutritionEndpoint.serverLogic { id =>
      Future {
        recipes.find(_.id == id) match
          case None         => Left(())
          case Some(recipe) =>
            // Get product IDs from ingredients
            val productIds = recipe.ingredients.map(_.productId)

            // Call product service to get nutrition info for all products
            val request = SttpClientInterpreter()
              .toRequestThrowErrors(
                ProductsEndpoints.getNutritionInfosEndpoint, // TODO refer to endpoints bundled with URL in a typesafe way
                Some(uri"$productServiceUrl")
              )
              .apply(productIds)

            val response = request.send(backend)
            val productNutritionList = response.body

            // Create a map for easy lookup
            val nutritionMap = productNutritionList.map(n => n.productId -> n).toMap

            // Calculate total nutrition per serving
            var totalCalories = 0.0
            var totalProtein = 0.0
            var totalFat = 0.0
            var totalCarbs = 0.0

            recipe.ingredients.foreach { ingredient =>
              nutritionMap.get(ingredient.productId).foreach { productNutrition =>
                // Calculate per serving: (nutrition per gram) * (quantity per serving)
                val quantityPerServing = ingredient.quantity / recipe.servings
                totalCalories += productNutrition.calories * quantityPerServing
                totalProtein += productNutrition.protein * quantityPerServing
                totalFat += productNutrition.fat * quantityPerServing
                totalCarbs += productNutrition.carbohydrates * quantityPerServing
              }
            }

            Right(NutritionInfo(totalCalories, totalProtein, totalFat, totalCarbs))
      }
    }

    List(
      getAllRecipesServerEndpoint,
      getRecipeByIdServerEndpoint,
      getRecipeNutritionServerEndpoint
    )
