import sbt._
import sbt.Keys._

object WasmServiceSettings {
  val tapirVersion = "1.13.5-WASM-1"
  val apispecVersion = "0.11.10-WASM-1"
  val circeVersion = "0.14.15-WASM-1"
  val sttpClient4Version = "4.0.15-WASM-1"

  // JVM-only SDK: the WasmServiceApp trait + OpenAPI extraction
  // Compiles with standard Scala — used for metadata extraction at build time
  // The WASM runtime (WasmServer etc.) is in a separate standalone build
  // because it requires the WASM Scala compiler and ScalaJS plugin
  val sdkSettings = CommonSettings.sdkModuleSettings ++ Seq(
    name := "yaga-wasm-service-sdk",
    libraryDependencies ++= Seq(
      "io.github.florian3k.sttp.tapir" %% "tapir-core" % tapirVersion,
      "io.github.florian3k.sttp.tapir" %% "tapir-server" % tapirVersion,
      "io.github.florian3k.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "io.github.florian3k.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
      "io.github.florian3k.sttp.apispec" %% "openapi-circe" % apispecVersion,
      "io.github.florian3k.circe" %% "circe-core" % circeVersion,
      "io.github.florian3k.circe" %% "circe-generic" % circeVersion,
      "io.github.florian3k.circe" %% "circe-parser" % circeVersion
    )
  )

  // JVM-only client SDK: forked OpenApiServiceReference[E] used at codegen time
  // (the same source is mirrored under sdk-client-wasm-runtime/ for the WASM runtime).
  val sdkClientSettings = CommonSettings.sdkModuleSettings ++ Seq(
    name := "yaga-wasm-service-sdk-client",
    libraryDependencies ++= Seq(
      "io.github.florian3k.sttp.tapir" %% "tapir-core" % tapirVersion,
      "io.github.florian3k.sttp.tapir" %% "tapir-sttp-client4" % tapirVersion,
      "io.github.florian3k.sttp.client4" %% "core" % sttpClient4Version,
      "io.github.florian3k.circe" %% "circe-core" % circeVersion
    )
  )

  val codegenSettings = CommonSettings.codegenModuleSettings ++ Seq(
    name := "yaga-wasm-service-codegen",
    libraryDependencies ++= Seq(
      // Reuse k8s-service's classgraph coord; TODO (tracked on K8sServiceSettings.scala:64)
      // hoist this dep into CommonSettings once we have two stable extension codegens.
      K8sServiceSettings.classGraphDep
    )
  )

  // Forked besom-side runtime types (Service, ServiceRef, ApiSchema, DeployableImage,
  // ImageCoordinates, SchemaCompatibility). Matches K8sServiceSettings.besomSettings
  // modulo: no openapi-circe-yaml (Phase 1 blew up on its upstream-circe transitive
  // deps — we switched to JSON-only), and sttp-apispec pinned to the wasm fork coords.
  val besomSettings = CommonSettings.besomModuleSettings ++ Seq(
    name := "yaga-wasm-service-besom",
    libraryDependencies ++= Seq(
      CommonSettings.besomCoreDependency,
      CommonSettings.besomProviderDependency("kubernetes", "4.22.1"),
      CommonSettings.besomProviderDependency("docker", "4.6.2"),
      // openapi-circe transitively pulls openapi-model, which in the wasm fork
      // bundles OpenAPIComparator (upstream ships it as a separate openapi-comparator
      // artifact; the wasm-sttp-apispec fork folded it into openapi-model).
      "io.github.florian3k.sttp.apispec" %% "openapi-circe" % apispecVersion,
      "io.github.florian3k.circe" %% "circe-parser" % circeVersion
    )
  )
}
