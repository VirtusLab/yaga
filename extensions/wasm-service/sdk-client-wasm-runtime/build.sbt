resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

val tapirVersion = "1.13.5-WASM-1"
val sttpClient4Version = "4.0.15-WASM-1"
val sttpSharedVersion = "1.5.0-WASM-1"
val circeVersion = "0.14.15-WASM-1"

name := "yaga-wasm-service-sdk-client-runtime"
version := "0.1.0"
organization := "org.virtuslab"
scalaOrganization := "io.github.scala-wasm"
scalaVersion := "3.8.3-RC1-wasm.4"

libraryDependencies ++= Seq(
  // Pulls in the WASM-side `yaga.wasmservice.{ServiceReference, ExtractEndpoints}` —
  // OpenApiServiceReference[E] extends `ServiceReference[E]`, so we depend on the
  // already-published runtime SDK rather than duplicating those types here.
  "org.virtuslab" %%% "yaga-wasm-service-sdk-runtime" % "0.1.0",
  "io.github.florian3k.sttp.client4" %%% "core" % sttpClient4Version,
  "io.github.florian3k.sttp.shared" %%% "core" % sttpSharedVersion,
  "io.github.florian3k.sttp.tapir" %%% "tapir-core" % tapirVersion,
  "io.github.florian3k.sttp.tapir" %%% "tapir-sttp-client4" % tapirVersion,
  "io.github.florian3k.circe" %%% "circe-core" % circeVersion
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
      report
        .select(
          configurationFilter(Compile.name),
          moduleFilter(bridgeModule.organization, bridgeModule.name, bridgeModule.revision),
          artifactFilter(extension = "jar", classifier = "")
        )
        .headOption
    }
  Some(jar.getOrElse(sys.error(s"Could not resolve $bridgeModule")))
}

enablePlugins(ScalaJSPlugin)
