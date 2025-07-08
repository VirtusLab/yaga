import besom.*
import besom.api.kubernetes
import besom.api.aws
import besom.api.docker
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Base64
import besom.json.json

import yaga.kubernetes.dockerSecretFromEcrToken

import example.{EchoService, EchoServiceArgs, ServerConfig}

@main def main = Pulumi.run:

  // User's config (required)

  val namespaceName = "my-application"
  val registryName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com"
  val imageFullName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com/yaga-test:0.1.0-SNAPSHOT"


  ////////////////////////


  val namespace = kubernetes.core.v1.Namespace(namespaceName, kubernetes.core.v1.NamespaceArgs(
    metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(name = namespaceName)
  ))


  val creds = aws.ecr.getAuthorizationToken(
    aws.ecr.GetAuthorizationTokenArgs()
  )

  val image = EchoService.imageResource(
    resourceName = "image",
    fullImageName = imageFullName,
    registry = docker.inputs.RegistryArgs(
      username = creds.userName,
      password = creds.password
    )
  )


  val dockerSecret = dockerSecretFromEcrToken(resourceName = "docker-secret", namespace = namespaceName, secretName = "docker-secret", registry = registryName, authToken = creds.authorizationToken)


  val serviceApp = EchoService("my-app", EchoServiceArgs(
    appName = "my-app",
    namespace = namespaceName,
    image = image,
    imageSecrets = dockerSecret,
    runConfig = ServerConfig(
      myConfigValue = "Sample config value"
    )
  ))

  Stack(namespace, dockerSecret, image, serviceApp).exports(
    serviceName = serviceApp.flatMap(_.serviceName),
    deploymentName = serviceApp.flatMap(_.deploymentName),
    namespace = serviceApp.flatMap(_.namespace)
  )
