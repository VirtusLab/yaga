lazy val `products-endpoints` = project
  .in(file("products-endpoints"))
  .yagaOpenApiEndpoints()
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.41",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.41"
    )
  )

lazy val `product-service` = project
  .in(file("product-service"))
  .yagaOpenApiK8sService(ServerType.NettySync)
  .dependsOn(`products-endpoints`)
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.4.11"
    ),
    dockerBaseImage := "eclipse-temurin:21"
  )

lazy val `recipes-service` = project
  .in(file("recipes-service"))
  .yagaOpenApiK8sService(ServerType.NettySync)
  .dependsOn(`products-endpoints`)
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client4" % "1.11.41",
      "com.softwaremill.sttp.client4" %% "core" % "4.0.9",
      "ch.qos.logback" % "logback-classic" % "1.4.11"
    ),
    dockerBaseImage := "eclipse-temurin:21"
  )

lazy val infra = project
  .in(file("infra"))
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "org.virtuslab" %% "besom-aws" % "6.73.0-core.0.5",
      "org.virtuslab" %% "besom-kubernetes" % "4.22.1-core.0.5",
      "org.virtuslab" %% "besom-docker" % "4.6.2-core.0.5"
    )
  )
  .withYagaDependencies(
    `product-service`.yagaK8sServiceInfra(),
    `recipes-service`.yagaK8sServiceInfra()
  )
