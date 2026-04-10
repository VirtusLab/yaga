ThisBuild / scalaVersion := "3.3.5"
ThisBuild / organization := "testservice"
ThisBuild / version := "0.0.1"

// Pin tapir to the same wasm-forked snapshot the SDK uses. The SDK depends on
// these transitively but we need them on the test service classpath so the
// `serverEndpoints` body can compile.
val wasmSnapshotVersion = "0.0.1-wasm-SNAPSHOT"

lazy val testService = (project in file("."))
  .settings(
    name := "test-service",
    libraryDependencies ++= Seq(
      "org.virtuslab" %% "yaga-wasm-service-sdk" % "0.1.0-SNAPSHOT"
    )
  )
