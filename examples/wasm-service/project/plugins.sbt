// Ground rule #1 — scala-wasm fork pin.
// The plugin cannot set this for the user: sbt's plugin classpath is resolved
// before any AutoPlugin runs, so the scala-wasm fork of sbt-scalajs has to be
// pulled in at `project/plugins.sbt` time. Every WASM-service example and
// every user project must replay this pin until upstream sbt-scalajs gains a
// WASM backend.
resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"

addSbtPlugin("io.github.scala-wasm" % "sbt-scalajs" % "1.21.1-wasm.4")

// sbt-scalajs-crossproject 1.3.2 declares a hard dependency on the upstream
// `org.scala-js % sbt-scalajs` artifact; excluding it here lets the scala-wasm
// fork (pulled above) satisfy the plugin contract without a version clash.
addSbtPlugin(
  ("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
    .exclude("org.scala-js", "sbt-scalajs")
)

addSbtPlugin("org.virtuslab" % "sbt-yaga-k8s-service" % "0.1.0")
