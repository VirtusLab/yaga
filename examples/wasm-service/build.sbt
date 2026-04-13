import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import org.scalajs.linker.interface.ModuleKind
import yaga.sbt.k8sservice.WasmRuntime

// scala-wasm fork consumes snapshots from Sonatype Central Snapshots.
ThisBuild / resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

// Pinned coordinates of the WASM fork of sttp-tapir / sttp-apispec / circe.
// Keep in lock-step with extensions/wasm-service/sdk-wasm-runtime/build.sbt.
val wasmLibsVersion = "0.0.1-wasm-SNAPSHOT"
val yagaVersion     = "0.1.0-SNAPSHOT"

// WASM runtime mode: "embedded" for wasmtime-in-container, "runtimeclass" for k8s RuntimeClass
// Set via WASM_RUNTIME env var (default: runtimeclass)
val wasmRuntime: WasmRuntime = sys.env.getOrElse("WASM_RUNTIME", "runtimeclass").toLowerCase match {
  case "embedded" => WasmRuntime.EmbeddedWasmtime
  case _          => WasmRuntime.RuntimeClass("wasmtime")
}

// Ground rule #3 — the yaga AutoPlugin deliberately does not set
// `scalaOrganization` / `scalaVersion` / `scalaCompilerBridgeBinaryJar`
// for the JS halves of WASM services. The example has to set them itself.
// Copy this verbatim into any new WASM-service build.
lazy val jsPlatformSettings: Seq[Setting[_]] = Seq(
  scalaOrganization := "io.github.scala-wasm",
  scalaVersion      := "3.8.3-RC1-wasm-bin-SNAPSHOT",

  // Workaround: sbt resolves the compiler bridge based on `scalaVersion` but
  // assumes `org.scala-lang` as the organization. With `scalaOrganization`
  // overridden to `io.github.scala-wasm`, the bridge lookup fails. Resolve
  // the fork's bridge jar by hand and hand it back to sbt via this key.
  // Copied verbatim from extensions/wasm-service/sdk-wasm-runtime/build.sbt.
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
)

// ---------------------------------------------------------------------------
// books-endpoints: shared endpoint definitions consumed by books-service's
// server side (JVM + JS) and library-service's client side.
//
// CrossType.Pure — there's nothing platform-specific here; one source tree.
// ---------------------------------------------------------------------------
lazy val `books-endpoints` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("books-endpoints"))
  .settings(scalaVersion := "3.3.6")
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"       % wasmLibsVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % wasmLibsVersion,
      "io.circe"                    %% "circe-core"       % wasmLibsVersion,
      "io.circe"                    %% "circe-generic"    % wasmLibsVersion,
      // JVM-side SDK brings in `yaga.wasmservice.ExtractEndpoints` for the macro derivation.
      "org.virtuslab" %% "yaga-wasm-service-sdk" % yagaVersion
    )
  )
  .jsSettings(jsPlatformSettings*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %%% "tapir-core"       % wasmLibsVersion,
      "com.softwaremill.sttp.tapir" %%% "tapir-json-circe" % wasmLibsVersion,
      "io.circe"                    %%% "circe-core"       % wasmLibsVersion,
      "io.circe"                    %%% "circe-generic"    % wasmLibsVersion,
      // JS-side runtime mirror of `yaga.wasmservice.ExtractEndpoints`.
      "org.virtuslab" %%% "yaga-wasm-service-sdk-runtime" % yagaVersion
    )
  )

// ---------------------------------------------------------------------------
// books-service: a WASM service that lists and adds Book records. Pure in-memory
// (each request gets a fresh component instance — see README known-caveats).
// ---------------------------------------------------------------------------
lazy val `books-service` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("books-service"))
  .dependsOn(`books-endpoints`)
  .yagaWasmService
  .settings(scalaVersion := "3.3.6")
  .jsSettings(jsPlatformSettings*)

// ---------------------------------------------------------------------------
// library-service: a WASM service that calls books-service via an in-WASM
// sttp backend and returns a summary. This is the piece that exercises the
// outgoing `wasi:http/outgoing-handler@0.2.0` path and closes Phase 3's
// pending round-trip sign-off.
// ---------------------------------------------------------------------------
lazy val `library-service` = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("library-service"))
  .dependsOn(`books-endpoints`)
  .yagaWasmService
  .yagaWasmServiceClient
  .settings(scalaVersion := "3.3.6")
  .jsSettings(jsPlatformSettings*)

// ---------------------------------------------------------------------------
// infra: Besom/Pulumi program that consumes the generated BooksService /
// LibraryService resource classes and deploys them onto a local k3s cluster.
// ---------------------------------------------------------------------------
lazy val infra = project
  .in(file("infra"))
  .settings(
    scalaVersion := "3.3.6",
    libraryDependencies ++= Seq(
      "org.virtuslab" %% "besom-kubernetes" % "4.22.1-core.0.5",
      "org.virtuslab" %% "besom-docker"     % "4.6.2-core.0.5"
    )
  )
  .withYagaDependencies(
    `books-service`.yagaWasmServiceInfra(wasmRuntime = wasmRuntime),
    `library-service`.yagaWasmServiceInfra(wasmRuntime = wasmRuntime)
  )
