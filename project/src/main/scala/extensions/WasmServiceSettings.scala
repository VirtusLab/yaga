import sbt._
import sbt.Keys._

object WasmServiceSettings {
  val wasmSnapshotVersion = "0.0.1-wasm-SNAPSHOT"

  // JVM-only SDK: the WasmServiceApp trait + OpenAPI extraction
  // Compiles with standard Scala — used for metadata extraction at build time
  // The WASM runtime (WasmServer etc.) is in a separate standalone build
  // because it requires the WASM Scala compiler and ScalaJS plugin
  val sdkSettings = CommonSettings.sdkModuleSettings ++ Seq(
    name := "yaga-wasm-service-sdk",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core" % wasmSnapshotVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-server" % wasmSnapshotVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % wasmSnapshotVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % wasmSnapshotVersion,
      "com.softwaremill.sttp.apispec" %% "openapi-circe" % wasmSnapshotVersion,
      "io.circe" %% "circe-core" % wasmSnapshotVersion,
      "io.circe" %% "circe-generic" % wasmSnapshotVersion,
      "io.circe" %% "circe-parser" % wasmSnapshotVersion
    )
  )

  // JVM-only client SDK: forked OpenApiServiceReference[E] used at codegen time
  // (the same source is mirrored under sdk-client-wasm-runtime/ for the WASM runtime).
  val sdkClientSettings = CommonSettings.sdkModuleSettings ++ Seq(
    name := "yaga-wasm-service-sdk-client",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir"   %% "tapir-core"         % wasmSnapshotVersion,
      "com.softwaremill.sttp.tapir"   %% "tapir-sttp-client4" % wasmSnapshotVersion,
      "com.softwaremill.sttp.client4" %% "core"               % wasmSnapshotVersion,
      "io.circe"                      %% "circe-core"         % wasmSnapshotVersion
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
      "com.softwaremill.sttp.apispec" %% "openapi-circe" % wasmSnapshotVersion,
      "io.circe"                      %% "circe-parser"  % wasmSnapshotVersion
    )
  )
}
