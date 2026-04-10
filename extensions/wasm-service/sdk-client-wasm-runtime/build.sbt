resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

val wasmSnapshotVersion = "0.0.1-wasm-SNAPSHOT"

name := "yaga-wasm-service-sdk-client-runtime"
version := "0.1.0-SNAPSHOT"
organization := "org.virtuslab"
scalaOrganization := "io.github.scala-wasm"
scalaVersion := "3.8.3-RC1-wasm-bin-SNAPSHOT"

libraryDependencies ++= Seq(
  // Pulls in the WASM-side `yaga.wasmservice.{ServiceReference, ExtractEndpoints}` —
  // OpenApiServiceReference[E] extends `ServiceReference[E]`, so we depend on the
  // already-published runtime SDK rather than duplicating those types here.
  "org.virtuslab"                  %%% "yaga-wasm-service-sdk-runtime" % "0.1.0-SNAPSHOT",
  "com.softwaremill.sttp.client4"  %%% "core"                          % wasmSnapshotVersion,
  "com.softwaremill.sttp.shared"   %%% "core"                          % wasmSnapshotVersion,
  "com.softwaremill.sttp.tapir"    %%% "tapir-core"                    % wasmSnapshotVersion,
  "com.softwaremill.sttp.tapir"    %%% "tapir-sttp-client4"            % wasmSnapshotVersion,
  "io.circe"                       %%% "circe-core"                    % wasmSnapshotVersion
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
