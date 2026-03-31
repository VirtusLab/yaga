package example.recipes

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.client4.*
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

import besom.json.*
import yaga.k8sservice.NettySyncServerApp
import yaga.k8sservice.OpenApiServiceReference

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
  val zero: NutritionInfo = NutritionInfo(0.0, 0.0, 0.0, 0.0)

  given Encoder[NutritionInfo] = deriveEncoder[NutritionInfo]
  given Decoder[NutritionInfo] = deriveDecoder[NutritionInfo]

  extension (a: NutritionInfo)
    def +(b: NutritionInfo): NutritionInfo =
      NutritionInfo(
        calories = a.calories + b.calories,
        protein = a.protein + b.protein,
        fat = a.fat + b.fat,
        carbohydrates = a.carbohydrates + b.carbohydrates
      )

case class ServerConfig(
    myConfigValue: String,
    productService: OpenApiServiceReference[ProductsEndpoints.type]
) derives JsonReader
object RecipesService extends NettySyncServerApp[ServerConfig]:

  def serviceName: String = "recipes-service"
  def serviceVersion: String = "0.1.0-SNAPSHOT"

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
    lazy val backend: SyncBackend = DefaultSyncBackend()
    given SttpClientInterpreter = SttpClientInterpreter()

    lazy val productService = config.productService.toRequestThrowErrors

    // GET /recipes - return all recipes
    val getAllRecipesEndpoint: PublicEndpoint[Unit, Unit, List[Recipe], Any] =
      endpoint.get
        .in("recipes")
        .out(jsonBody[List[Recipe]])

    val getAllRecipesServerEndpoint = getAllRecipesEndpoint.handleSuccess { _ =>
      recipes
    }

    // GET /recipes/{id} - return single recipe (404 if not found)
    val getRecipeByIdEndpoint: PublicEndpoint[Long, Unit, Recipe, Any] =
      endpoint.get
        .in("recipes" / path[Long]("id"))
        .out(jsonBody[Recipe])

    val getRecipeByIdServerEndpoint = getRecipeByIdEndpoint.handle { id =>
      recipes.find(_.id == id) match
        case Some(recipe) => Right(recipe)
        case None         => Left(())
    }

    // GET /recipes/{id}/nutrition - calculate nutrition by calling product service
    val getRecipeNutritionEndpoint: PublicEndpoint[Long, Unit, NutritionInfo, Any] =
      endpoint.get
        .in("recipes" / path[Long]("id") / "nutrition")
        .out(jsonBody[NutritionInfo])

    val getRecipeNutritionServerEndpoint = getRecipeNutritionEndpoint.handle { id =>
      recipes.find(_.id == id) match
        case None         => Left(())
        case Some(recipe) =>
          // Get product IDs from ingredients
          val productIds = recipe.ingredients.map(_.productId)

          // Call product service to get nutrition info for all products
          val request = productService.getNutritionInfosEndpoint(productIds)
          val productNutritionList = request.send(backend).body

          // Create a map for easy lookup
          val nutritionMap = productNutritionList.map(n => n.productId -> n).toMap

          // Calculate total nutrition per serving
          val totalNutrition = recipe.ingredients
            .flatMap { ingredient =>
              nutritionMap.get(ingredient.productId).map { productNutrition =>
                val quantityPerServing = ingredient.quantity / recipe.servings
                NutritionInfo(
                  calories = productNutrition.calories * quantityPerServing,
                  protein = productNutrition.protein * quantityPerServing,
                  fat = productNutrition.fat * quantityPerServing,
                  carbohydrates = productNutrition.carbohydrates * quantityPerServing
                )
              }
            }
            .foldLeft(NutritionInfo.zero)(_ + _)

          Right(totalNutrition)
    }

    List(
      getAllRecipesServerEndpoint,
      getRecipeByIdServerEndpoint,
      getRecipeNutritionServerEndpoint
    )
