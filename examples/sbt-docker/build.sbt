lazy val server = project.in(file("server"))
  .yagaK8sService()
  .settings(
    scalaVersion := "3.3.4",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"           % "1.9.6",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server"   % "1.9.6",
      // "com.softwaremill.sttp.tapir" %% "tapir-json-circe"     % "1.9.6",
      // "io.circe"                     %% "circe-generic"        % "0.14.6",
    ),
    dockerBaseImage := "eclipse-temurin:21"
  )

lazy val infra = project.in(file("infra"))
  .settings(
    scalaVersion := "3.7.0",
    libraryDependencies ++= Seq(
      "org.virtuslab" %% "besom-aws" % "6.73.0-core.0.5-SNAPSHOT"
    )
  )
  .withYagaDependencies(server.yagaK8sServiceInfra())
