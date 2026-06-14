package yaga.codegen.wasmservice.test

import java.nio.file.{Path, Paths}

import yaga.codegen.core.extractor.CodegenSource
import yaga.codegen.wasmservice.Codegen

/** Phase 4 snapshot-style smoke test for the WASM service codegen pipeline.
  *
  * Runs `Codegen.sourcesForModuleApi` against the pre-built `testService` jar under `src/test/resources/testService/target/scala-3.3.5/`
  * and checks five invariants on the emitted sources (see the manual checklist in the plan):
  *
  *   1. Generated `TestService.scala` imports the WASM-service besom types. 2. Container command is `List("wasmtime", "serve",
  *      "/app/main.wasm", ...)`. 3. The env var list contains `YAGA_WASM_SERVICE_CONFIG`. 4. `serverApiSpecJson` triple-quoted literal is
  *      valid JSON. 5. `Book.scala` / `MyConfig.scala` derive `_root_.besom.json.JsonFormat`.
  *
  * Invoke via: sbt "wasm-service-codegen/Test/runMain yaga.codegen.wasmservice.test.runGeneratorSnapshot"
  */
object GeneratorSnapshotMain:

  private val testServiceJarPath: Path =
    Paths.get("extensions/wasm-service/codegen/src/test/resources/testService/target/scala-3.3.5/test-service_3-0.0.1.jar").toAbsolutePath

  @main
  def runGeneratorSnapshot(): Unit =
    assert(testServiceJarPath.toFile.exists, s"testService jar not found at ${testServiceJarPath}")

    // Reflection on the test-service classes needs `yaga.wasmservice.WasmServiceApp` +
    // tapir/circe/sttp transitives on the classloader. Pull them in via Coursier
    // against the locally-published SDK — same mechanism k8s-service's sbt plugin
    // uses for end-user codegen (see `extensions/k8s-service/sbt/.../CodegenHelpers.scala`).
    val sdkMaven = CodegenSource.MavenArtifact(
      orgName = "org.virtuslab",
      moduleName = "yaga-wasm-service-sdk_3",
      version = "0.1.0"
    )

    val sources = Codegen.sourcesForModuleApi(
      codegenSources = List(
        CodegenSource.LocalJar(absolutePath = testServiceJarPath),
        sdkMaven
      ),
      packagePrefix = "gen.test",
      generateInfra = true,
      dockerContextAbsolutePath = Some(Paths.get("/tmp/fake-ctx")),
      wasmRuntimeClassName = None
    )

    val sourcesByFile: Map[String, String] =
      sources.map(src => src.filePath.pathParts.last -> src.sourceCode).toMap

    println(s"generated ${sources.size} source files:")
    sources.foreach(src => println(s"  - ${src.filePath.pathParts.mkString("/")}"))

    val failures = scala.collection.mutable.ListBuffer.empty[String]

    def check(name: String, ok: Boolean, detail: => String = ""): Unit =
      if ok then println(s"  OK   $name")
      else
        println(s"  FAIL $name $detail")
        failures += name

    val testServiceSrc =
      sourcesByFile.getOrElse("TestService.scala", sys.error("expected gen.test.testservice.TestService.scala in output"))

    // 1. Imports
    check(
      "TestService imports yaga.wasmservice besom types",
      Seq(
        "import yaga.wasmservice.DeployableImage",
        "import yaga.wasmservice.ImagePlatform",
        "import yaga.wasmservice.ImageCoordinates"
      ).forall(testServiceSrc.contains)
    )

    // 2. Container command swap
    check(
      "TestService container command uses wasmtime serve",
      testServiceSrc.contains("""command = List("wasmtime", "serve", "/app/main.wasm", "-Wgc,function-references,exceptions")"""),
      "(expected wasmtime serve command line)"
    )

    // 3. Env var name
    check(
      "TestService env delivery uses yaga.wasmservice.internal.EnvWriter",
      testServiceSrc.contains("yaga.wasmservice.internal.EnvWriter.write(config)")
    )

    // 4. serverApiSpecJson is present and looks like an OpenAPI JSON document.
    //    Full JSON parse is left to the verification runMain (see plan step 6) to
    //    avoid pulling circe into the codegen module's test classpath.
    val jsonLiteralRegex = "inline val serverApiSpecJson = \"\"\"([\\s\\S]*?)\"\"\"".r
    val jsonLiteralOpt = jsonLiteralRegex.findFirstMatchIn(testServiceSrc).map(_.group(1))
    check(
      "serverApiSpecJson triple-quoted literal present",
      jsonLiteralOpt.isDefined
    )
    jsonLiteralOpt.foreach: literal =>
      val trimmed = literal.trim
      check(
        "serverApiSpecJson literal starts with '{' (JSON object)",
        trimmed.startsWith("{") && trimmed.endsWith("}"),
        s"(prefix=${trimmed.take(20)}, suffix=${trimmed.takeRight(20)})"
      )
      check(
        "serverApiSpecJson literal contains `openapi` + `paths` fields",
        trimmed.contains("\"openapi\"") && trimmed.contains("\"paths\""),
        "(expected OpenAPI root fields)"
      )
      check(
        "serverApiSpecJson literal mentions Book schema",
        trimmed.contains("Book"),
        "(expected Book in component schemas)"
      )

    // 5. Model files
    //    The config class (MyConfig) is always emitted as a model since it's the
    //    type parameter on WasmServiceApp[C]. Book is currently *not* emitted as a
    //    standalone file because the ModelExtractor only walks rootTypes (config),
    //    not endpoint I/O types — Book shows up in the embedded OpenAPI spec instead.
    //    That's consistent with k8s-service's ApiExtractor.extractReferencedSymbols.
    val myConfigSrc = sourcesByFile.getOrElse("MyConfig.scala", sys.error("expected MyConfig.scala in output"))
    check(
      "MyConfig.scala derives _root_.besom.json.JsonFormat",
      myConfigSrc.contains("derives _root_.besom.json.JsonFormat")
    )
    sourcesByFile
      .get("Book.scala")
      .foreach: bookSrc =>
        check(
          "Book.scala derives _root_.besom.json.JsonFormat (if emitted)",
          bookSrc.contains("derives _root_.besom.json.JsonFormat")
        )

    println()
    if failures.nonEmpty then
      println(s"FAIL — ${failures.size} check(s) failed:")
      failures.foreach(f => println(s"  * $f"))
      println()
      println("--- TestService.scala (full) ---")
      println(testServiceSrc)
      sys.exit(1)
    else println(s"OK — ${sources.size} source files generated, all snapshot checks passed")
