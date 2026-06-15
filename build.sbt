////////////////////////////////////////////////////////////
// Root
////////////////////////////////////////////////////////////

lazy val root = project
  .in(file("."))
  .aggregate(`core`, `aws-lambda`, `k8s-service`, `wasm-service`)
  .settings(
    name := "yaga",
    publish / skip := true
  )

////////////////////////////////////////////////////////////
// Commons
////////////////////////////////////////////////////////////

ThisBuild / organization := "org.virtuslab"
ThisBuild / version := "0.1.0"
ThisBuild / developers := List(
  Developer(
    id = "lbialy",
    name = "Łukasz Biały",
    email = "lbialy@virtuslab.com",
    url = url("https://github.com/lbialy")
  ),
  Developer(
    id = "prolativ",
    name = "Michał Pałka",
    email = "mpalka@virtuslab.com",
    url = url("https://github.com/prolativ")
  )
)

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
  .dependsOn(
    `aws-lambda-sdk`.jvm
  ) // Needs dependency only on the model part od the SDK - split modules?

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
  .aggregate(
    `aws-lambda-sdk`.jvm,
    `aws-lambda-sdk`.js,
    `aws-lambda-besom`,
    `aws-lambda-codegen`,
    `aws-lambda-compiler-plugin`,
    `aws-lambda-sbt`
  )
  .settings(
    publish / skip := true
  )

////////////////////////////////////////////////////////////
// K8s Service
////////////////////////////////////////////////////////////

lazy val `k8s-service-sdk-open-api` = project
  .in(file("extensions/k8s-service/sdk-openapi"))
  .settings(K8sServiceSettings.sdkOpenApiSettings)
  .dependsOn(`core-model`.jvm)

lazy val `k8s-service-sdk-open-api-client` = project
  .in(file("extensions/k8s-service/sdk-openapi-client"))
  .settings(K8sServiceSettings.sdkOpenApiClientSettings)
  .dependsOn(`k8s-service-sdk-open-api`)

lazy val `k8s-service-sdk-open-api-netty-future` = project
  .in(file("extensions/k8s-service/sdk-openapi-netty-future"))
  .settings(K8sServiceSettings.sdkNettyFutureSettings)
  .dependsOn(`k8s-service-sdk-open-api`)

lazy val `k8s-service-sdk-open-api-netty-sync` = project
  .in(file("extensions/k8s-service/sdk-openapi-netty-sync"))
  .settings(K8sServiceSettings.sdkNettySyncSettings)
  .dependsOn(`k8s-service-sdk-open-api`)

lazy val `k8s-service-besom` = project
  .in(file("extensions/k8s-service/besom"))
  .settings(K8sServiceSettings.besomSettings)
  .dependsOn(`k8s-service-sdk-open-api`)

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
  .aggregate(
    `k8s-service-sdk-open-api`,
    `k8s-service-sdk-open-api-client`,
    `k8s-service-sdk-open-api-netty-future`,
    `k8s-service-sdk-open-api-netty-sync`,
    `k8s-service-besom`,
    `k8s-service-codegen`,
    `k8s-service-sbt`
  )
  .settings(publish / skip := true)

////////////////////////////////////////////////////////////
// WASM Service
////////////////////////////////////////////////////////////

lazy val `wasm-service-sdk` = project
  .in(file("extensions/wasm-service/sdk"))
  .settings(WasmServiceSettings.sdkSettings)
  .dependsOn(`core-model`.jvm)

lazy val `wasm-service-sdk-client` = project
  .in(file("extensions/wasm-service/sdk-client"))
  .settings(WasmServiceSettings.sdkClientSettings)
  .dependsOn(`wasm-service-sdk`)

lazy val `wasm-service-besom` = project
  .in(file("extensions/wasm-service/besom"))
  .settings(WasmServiceSettings.besomSettings)
  .dependsOn(`wasm-service-sdk`)

lazy val `wasm-service-codegen` = project
  .in(file("extensions/wasm-service/codegen"))
  .settings(WasmServiceSettings.codegenSettings)
  .dependsOn(`core-codegen`)

lazy val `wasm-service` = project
  .in(file("extensions/wasm-service"))
  .aggregate(
    `wasm-service-sdk`,
    `wasm-service-sdk-client`,
    `wasm-service-besom`,
    `wasm-service-codegen`
  )
  .settings(publish / skip := true)
