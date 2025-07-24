import sbt._
import sbt.Keys._

object K8sServiceSettings {
  val sdkSettings = CommonSettings.sdkModuleSettings ++ Seq(
    name := "yaga-k8s-service-sdk",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server"   % "1.11.35", // TODO avoid this dependency for core SDK by splitting modules?

      // TODO Don't require these depencies by moving the logic of printing OpenAPI spec to the codegen module? 
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.35",
      "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9"
    )
  )

  val besomSettings = CommonSettings.besomModuleSettings ++ Seq(
    name := "yaga-k8s-service-besom",
    libraryDependencies ++= Seq(
      CommonSettings.besomCoreDependency,
      CommonSettings.besomProviderDependency("kubernetes", "4.22.1"),
      CommonSettings.besomProviderDependency("docker", "4.6.2"),
    )
  )

  val codegenSettings = CommonSettings.codegenModuleSettings ++ Seq(
    name := "yaga-k8s-service-codegen",
    libraryDependencies ++= Seq(
      classGraphDep
    )
  )

  val sbtPluginSettings = CommonSettings.sbtPluginModuleSettings ++ Seq(
    name := "sbt-yaga-k8s-service",
    libraryDependencies ++= Seq(

    ),
    addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
  )

  val classGraphDep = "io.github.classgraph" % "classgraph" % "4.8.179" // TODO reuse between extensions
}
