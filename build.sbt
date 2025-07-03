////////////////////////////////////////////////////////////
// Root
////////////////////////////////////////////////////////////

lazy val root = project
  .in(file("."))
  .aggregate(`core`, `aws-lambda`)
  .settings(
    name := "yaga",
    publish / skip := true
  )


////////////////////////////////////////////////////////////
// Commons
////////////////////////////////////////////////////////////

ThisBuild / organization := "org.virtuslab"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / developers := List(
  Developer(id = "lbialy", name = "Łukasz Biały", email = "lbialy@virtuslab.com", url = url("https://github.com/lbialy")),
  Developer(id = "prolativ", name = "Michał Pałka", email = "mpalka@virtuslab.com", url = url("https://github.com/prolativ"))
)

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

////////////////////////////////////////////////////////////
// Core
////////////////////////////////////////////////////////////

lazy val `core-model` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core/model"))
  .jvmSettings(CoreSettings.modelJvmSettings)
  .jsSettings(CoreSettings.modelJsSettings)

lazy val `core-codegen` = project
  .in(file("core/codegen"))
  .settings(CoreSettings.codegenSettings)

lazy val `core-sbt` = project
  .in(file("core/sbt"))
  .settings(CoreSettings.sbtPluginSettings)

lazy val `core` = project
  .in(file("core"))
  .aggregate(`core-model`.jvm, `core-model`.js, `core-codegen`, `core-sbt`)
  .settings(
    publish / skip := true
  )


////////////////////////////////////////////////////////////
// AWS Lambda
////////////////////////////////////////////////////////////

lazy val `aws-lambda-sdk` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("extensions/aws-lambda/sdk"))
  .jvmSettings(AwsLambdaSettings.sdkJvmSettings)
  .jsSettings(AwsLambdaSettings.sdkJsSettings)
  .dependsOn(`core-model`)

lazy val `aws-lambda-besom` = project
  .in(file("extensions/aws-lambda/besom"))
  .settings(AwsLambdaSettings.besomSettings)
  .dependsOn(`aws-lambda-sdk`.jvm) // Needs dependency only on the model part od the SDK - split modules?

lazy val `aws-lambda-codegen` = project
  .in(file("extensions/aws-lambda/codegen"))
  .settings(AwsLambdaSettings.codegenSettings)
  .dependsOn(`core-codegen`)

lazy val `aws-lambda-compiler-plugin` = project
  .in(file("extensions/aws-lambda/compiler-plugin"))
  .settings(AwsLambdaSettings.compilerPluginSettings)

lazy val `aws-lambda-sbt` = project
  .in(file("extensions/aws-lambda/sbt"))
  .settings(AwsLambdaSettings.sbtPluginSettings)
  .dependsOn(`core-sbt`)

lazy val `aws-lambda` = project
  .in(file("extensions/aws-lambda"))
  .aggregate(`aws-lambda-sdk`.jvm, `aws-lambda-sdk`.js, `aws-lambda-besom`, `aws-lambda-codegen`, `aws-lambda-compiler-plugin`, `aws-lambda-sbt`)
  .settings(
    publish / skip := true
  )


////////////////////////////////////////////////////////////
// K8s Service
////////////////////////////////////////////////////////////

lazy val `k8s-service-sdk` = project
  .in(file("extensions/k8s-service/sdk"))
  .settings(K8sServiceSettings.sdkSettings)
  .dependsOn(`core-model`.jvm)

lazy val `k8s-service-besom` = project
  .in(file("extensions/k8s-service/besom"))
  .settings(K8sServiceSettings.besomSettings)
  .dependsOn(`k8s-service-sdk`)

lazy val `k8s-service-codegen` = project
  .in(file("extensions/k8s-service/codegen"))
  .settings(K8sServiceSettings.codegenSettings)
  .dependsOn(`core-codegen`)

lazy val `k8s-service-sbt` = project
  .in(file("extensions/k8s-service/sbt"))
  .settings(K8sServiceSettings.sbtPluginSettings)
  .dependsOn(`core-sbt`)

lazy val `k8s-service` = project
  .in(file("extensions/k8s-service"))
  .aggregate(`k8s-service-sdk`, `k8s-service-besom`, `k8s-service-codegen`, `k8s-service-sbt`)
  .settings(publish / skip := true)
