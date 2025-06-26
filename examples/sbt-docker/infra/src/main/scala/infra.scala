import besom.*
import besom.api.kubernetes
import besom.api.aws
import besom.api.docker
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Base64
import besom.json.json

import yaga.kubernetes.dockerSecretFromEcrToken

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

  val image = MyServiceApp.imageResource(
    resourceName = "image",
    fullImageName = imageFullName,
    registry = docker.inputs.RegistryArgs(
      username = creds.userName,
      password = creds.password
    )
  )


  val dockerSecret = dockerSecretFromEcrToken(resourceName = "docker-secret", namespace = namespaceName, secretName = "docker-secret", registry = registryName, authToken = creds.authorizationToken)


  val myServiceApp = MyServiceApp("my-app", MyServiceAppArgs(
    appName = "my-app",
    namespace = namespaceName,
    image = image,
    imageSecrets = dockerSecret
  ))

  Stack(namespace, dockerSecret, image, myServiceApp).exports(
    serviceName = myServiceApp.flatMap(_.serviceName),
    deploymentName = myServiceApp.flatMap(_.deploymentName),
    namespace = myServiceApp.flatMap(_.namespace)
  )