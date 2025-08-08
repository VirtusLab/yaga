lazy val `echo-endpoints` = project.in(file("echo-endpoints"))
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"           % "1.11.35",
    )
  )

lazy val `echo-service` = project.in(file("echo-service"))
  .yagaK8sService()
  .dependsOn(`echo-endpoints`)
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"           % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server"   % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"     % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.35",
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9"
    ),
    dockerBaseImage := "eclipse-temurin:21"
  )

lazy val `proxy-service` = project.in(file("proxy-service"))
  .yagaK8sService()
  .dependsOn(`echo-endpoints`)
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"           % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server"   % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"     % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % "1.11.35",
      "com.softwaremill.sttp.client3" %% "core" % "3.9.0",

      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.35",
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9"
    ),
    dockerBaseImage := "eclipse-temurin:21"
  )

lazy val infra = project.in(file("infra"))
  .settings(
    scalaVersion := "3.7.0",
    libraryDependencies ++= Seq(
      "org.virtuslab" %% "besom-aws" % "6.73.0-core.0.5-SNAPSHOT",
      "org.virtuslab" %% "besom-kubernetes" % "4.22.1-core.0.5-SNAPSHOT",
      "org.virtuslab" %% "besom-docker" % "4.6.2-core.0.5-SNAPSHOT"
    )
  )
  .withYagaDependencies(
    `echo-service`.yagaK8sServiceInfra(),
    `proxy-service`.yagaK8sServiceInfra()
  )
