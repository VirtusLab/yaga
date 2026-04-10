resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

val wasmSnapshotVersion = "0.0.1-wasm-SNAPSHOT"

name := "yaga-wasm-service-sdk-runtime"
version := "0.1.0-SNAPSHOT"
organization := "org.virtuslab"
scalaOrganization := "io.github.scala-wasm"
scalaVersion := "3.8.3-RC1-wasm-bin-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %%% "tapir-core" % wasmSnapshotVersion,
  "com.softwaremill.sttp.tapir" %%% "tapir-server" % wasmSnapshotVersion,
  "com.softwaremill.sttp.tapir" %%% "tapir-json-circe" % wasmSnapshotVersion,
  "com.softwaremill.sttp.tapir" %%% "tapir-openapi-docs" % wasmSnapshotVersion,
  "com.softwaremill.sttp.apispec" %%% "openapi-circe" % wasmSnapshotVersion,
  "io.circe" %%% "circe-core" % wasmSnapshotVersion,
  "io.circe" %%% "circe-generic" % wasmSnapshotVersion,
  "io.circe" %%% "circe-parser" % wasmSnapshotVersion
)

// Workaround: scalaOrganization should work out of the box
scalaCompilerBridgeBinaryJar := {
  val sv = scalaVersion.value
  val bridgeModule = "io.github.scala-wasm" % "scala3-sbt-bridge" % sv
  val descriptor = dependencyResolution.value.wrapDependencyInModule(bridgeModule)
  val jar = dependencyResolution.value
    .update(
      descriptor,
      updateConfiguration.value,
      (update / unresolvedWarningConfiguration).value,
      streams.value.log
    )
    .toOption
    .flatMap { report =>
      report.select(
        configurationFilter(Compile.name),
        moduleFilter(bridgeModule.organization, bridgeModule.name, bridgeModule.revision),
        artifactFilter(extension = "jar", classifier = "")
      ).headOption
    }
  Some(jar.getOrElse(sys.error(s"Could not resolve $bridgeModule")))
}

enablePlugins(ScalaJSPlugin)
