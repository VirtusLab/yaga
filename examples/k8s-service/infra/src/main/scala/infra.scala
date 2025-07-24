import besom.*
import besom.api.kubernetes
import besom.api.aws
import besom.api.docker
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Base64
import besom.json.json

import yaga.kubernetes.dockerSecretFromEcrToken

import example.echo.{EchoService, EchoServiceArgs, ServerConfig}
import example.proxy.{ProxyService, ProxyServiceArgs}

@main def main = Pulumi.run:

  // User's config (required)

  val namespaceName = "my-application"
  val registryName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com"
  val echoImageFullName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com/yaga-test-echo:0.1.0-SNAPSHOT"
  val proxyImageFullName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com/yaga-test-proxy:0.1.0-SNAPSHOT"


  ////////////////////////


  val namespace = kubernetes.core.v1.Namespace(namespaceName, kubernetes.core.v1.NamespaceArgs(
    metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(name = namespaceName)
  ))


  val creds = aws.ecr.getAuthorizationToken(
    aws.ecr.GetAuthorizationTokenArgs()
  )

  val echoImage = EchoService.imageResource(
    resourceName = "echo-image",
    fullImageName = echoImageFullName,
    registry = docker.inputs.RegistryArgs(
      username = creds.userName,
      password = creds.password
    )
  )

  val proxyImage = ProxyService.imageResource(
    resourceName = "proxy-image",
    fullImageName = proxyImageFullName,
    registry = docker.inputs.RegistryArgs(
      username = creds.userName,
      password = creds.password
    )
  )


  val dockerSecret = dockerSecretFromEcrToken(resourceName = "docker-secret", namespace = namespaceName, secretName = "docker-secret", registry = registryName, authToken = creds.authorizationToken)

  val echoApp = EchoService("echo-app", EchoServiceArgs(
    appName = "echo-app",
    namespace = namespaceName,
    image = echoImage,
    imageSecrets = dockerSecret,
    runConfig = ServerConfig(myConfigValue = "some test value")
  ))

  val proxyApp = ProxyService("proxy-app", ProxyServiceArgs(
    appName = "proxy-app",
    namespace = namespaceName,
    image = proxyImage,
    imageSecrets = dockerSecret,
  ))

  Stack(namespace, dockerSecret, echoImage, echoApp, proxyImage, proxyApp).exports(
    echoServiceName = echoApp.flatMap(_.serviceName),
    echoDeploymentName = echoApp.flatMap(_.deploymentName),
    proxyServiceName = proxyApp.flatMap(_.serviceName),
    proxyDeploymentName = proxyApp.flatMap(_.deploymentName),
  )
