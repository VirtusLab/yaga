import besom.*
import besom.api.kubernetes
import besom.api.aws
import besom.api.docker
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Base64
import besom.json.json

import yaga.kubernetes.dockerSecretFromEcrToken

import example.recipes.{RecipesService, RecipesServiceArgs, ServerConfig as RecipesServerConfig}
import example.products.{ProductService, ProductServiceArgs, ServerConfig as ProductServerConfig}

@main def main = Pulumi.run:

  // User's config (required) -- TODO extract to stack config where applicable

  val namespaceName = "my-application"
  val registryName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com"
  val productImageFullName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com/yaga-test-product:0.1.0-SNAPSHOT"
  val recipesImageFullName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com/yaga-test-recipes:0.1.0-SNAPSHOT"

  ////////////////////////

  val namespace = kubernetes.core.v1.Namespace(
    namespaceName,
    kubernetes.core.v1.NamespaceArgs(
      metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(name = namespaceName)
    )
  )

  val creds = aws.ecr.getAuthorizationToken(
    aws.ecr.GetAuthorizationTokenArgs()
  )

  val productImage = ProductService.imageResource(
    resourceName = "product-image",
    fullImageName = productImageFullName,
    registry = docker.inputs.RegistryArgs(
      username = creds.userName,
      password = creds.password
    )
  )

  val recipesImage = RecipesService.imageResource(
    resourceName = "recipes-image",
    fullImageName = recipesImageFullName,
    registry = docker.inputs.RegistryArgs(
      username = creds.userName,
      password = creds.password
    )
  )

  val dockerSecret = dockerSecretFromEcrToken(
    resourceName = "docker-secret",
    namespace = namespaceName,
    secretName = "docker-secret",
    registry = registryName,
    authToken = creds.authorizationToken
  )

  val productApp = ProductService(
    "product-app",
    ProductServiceArgs(
      namespace = namespaceName,
      image = productImage,
      imageSecrets = dockerSecret,
      runConfig = ProductServerConfig(myConfigValue = "some test value")
    )
  )

  val recipesApp = RecipesService(
    "recipes-app",
    RecipesServiceArgs(
      namespace = namespaceName,
      image = recipesImage,
      imageSecrets = dockerSecret,
      runConfig =
        for productServiceRef <- productApp.asServiceRef[example.products.ProductsEndpoints]
        yield RecipesServerConfig(
          myConfigValue = "some test value",
          productService = productServiceRef
        )
    )
  )

  Stack(namespace, dockerSecret, productImage, productApp, recipesImage, recipesApp).exports(
    productServiceName = productApp.flatMap(_.serviceName),
    productDeploymentName = productApp.flatMap(_.deploymentName),
    recipesServiceName = recipesApp.flatMap(_.serviceName),
    recipesDeploymentName = recipesApp.flatMap(_.deploymentName)
  )
