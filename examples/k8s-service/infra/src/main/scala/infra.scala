import besom.*
import besom.api.kubernetes
import besom.api.aws
import besom.api.docker
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Base64
import besom.json.*

import yaga.kubernetes.dockerSecretFromEcrToken

import example.echo.{EchoService, EchoServiceArgs, ServerConfig as EchoServerConfig}
import example.proxy.{ProxyService, ProxyServiceArgs, ServerConfig as ProxyServerConfig}

@main def main = Pulumi.run:

  // User's config (required) -- TODO extract to stack config where applicable

  val namespaceName = "my-application"
  val registryName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com"
  val echoImageFullName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com/yaga-test-echo:0.1.0-SNAPSHOT"
  val proxyImageFullName = "730335225485.dkr.ecr.eu-north-1.amazonaws.com/yaga-test-proxy:0.1.0-SNAPSHOT"

  ////////////////////////

  val provider = for
    stackRef <- StackReference("eksdev")
    kubeconfig <- stackRef.requireOutput("kubeconfig")
    p <- kubernetes.Provider("my-provider", kubernetes.ProviderArgs(kubeconfig = kubeconfig.toString))
  yield p

  val namespace = kubernetes.core.v1.Namespace(namespaceName, kubernetes.core.v1.NamespaceArgs(
      metadata = kubernetes.meta.v1.inputs.ObjectMetaArgs(name = namespaceName)
    ),
    opts = opts(provider = provider)
  )


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


  val dockerSecret = dockerSecretFromEcrToken(resourceName = "docker-secret", namespace = namespaceName, secretName = "docker-secret", registry = registryName, authToken = creds.authorizationToken, provider = provider)

  val echoApp = EchoService("echo-app", EchoServiceArgs(
    namespace = namespaceName,
    image = echoImage,
    imageSecrets = dockerSecret,
    runConfig = EchoServerConfig(myConfigValue = "some test value")
  ), opts = opts(providers = provider))

  val proxyApp = ProxyService("proxy-app", ProxyServiceArgs(
    namespace = namespaceName,
    image = proxyImage,
    imageSecrets = dockerSecret,
    runConfig = 
      for
        echoServiceRef <- echoApp.asServiceRef[example.echo.EchoEndpoints]
      yield
        ProxyServerConfig(
          myConfigValue = "some test value",
          echoService = echoServiceRef
        )
    ),
    opts = opts(providers = provider),
  )

  Stack(namespace, dockerSecret, echoImage, echoApp, proxyImage, proxyApp).exports(
    echoServiceName = echoApp.flatMap(_.serviceName),
    echoDeploymentName = echoApp.flatMap(_.deploymentName),
    proxyServiceName = proxyApp.flatMap(_.serviceName),
    proxyDeploymentName = proxyApp.flatMap(_.deploymentName),
  )
