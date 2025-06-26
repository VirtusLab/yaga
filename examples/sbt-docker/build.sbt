import com.typesafe.sbt.packager.docker._

lazy val server = project.in(file("server"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    scalaVersion := "3.3.4",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"           % "1.9.6",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server"   % "1.9.6",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"     % "1.9.6",
      "io.circe"                     %% "circe-generic"        % "0.14.6",
      "dev.zio"                      %% "zio"                  % "2.0.21"
    ),
    dockerBaseImage := "openjdk:11",
    organization := "org.virtuslab",
    name := "yaga-server-test",
    version := "0.1.0-SNAPSHOT"
  )

lazy val infra = project.in(file("infra"))
  .settings(
    scalaVersion := "3.7.0"
  )
  .withYagaDependencies(server.yagaK8sServiceInfra())