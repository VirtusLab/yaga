lazy val `echo-endpoints` = project
  .in(file("echo-endpoints"))
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.41",
      "org.virtuslab" %% "yaga-k8s-service-sdk" % "0.1.0-SNAPSHOT"
    )
  )

lazy val `echo-service` = project
  .in(file("echo-service"))
  .yagaK8sService()
  .dependsOn(`echo-endpoints`)
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.41",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "1.11.41",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.41",
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.41",
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9"
    ),
    dockerBaseImage := "eclipse-temurin:21"
  )

lazy val `proxy-service` = project
  .in(file("proxy-service"))
  .yagaK8sService()
  .dependsOn(`echo-endpoints`)
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.41",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "1.11.41",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.41",
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client4" % "1.11.41",
      "com.softwaremill.sttp.client4" %% "core" % "4.0.9",
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.41",
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9"
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
    `echo-service`.yagaK8sServiceInfra(),
    `proxy-service`.yagaK8sServiceInfra()
  )
