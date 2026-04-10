package yaga.codegen.wasmservice

import yaga.codegen.core.extractor.CodegenSource
import java.nio.file.{Path, Paths}

case class ModuleApiCodegenMainArgs(
  codegenSources: List[CodegenSource],
  packagePrefix: String,
  generateInfra: Boolean,
  dockerContextAbsolutePath: Option[Path],
  outputDir: String,
)

object ModuleApiCodegenMainArgs:
  private case class Parser(
    codegenSources: List[CodegenSource],
    packagePrefix: Option[String],
    generateInfra: Option[Boolean],
    dockerContextAbsolutePath: Option[Path],
    outputDir: Option[String],
  ):
    // TODO don't allow overriding non-repeated parameters
    def parseArgs(args: List[String]): ModuleApiCodegenMainArgs =
      args match
        case "--local-classpath-jar" :: filePath :: rest =>
          val path = Paths.get(filePath)
          assert(path.isAbsolute, s"Path ${filePath} is not absolute")
          val localSource = CodegenSource.LocalJar(absolutePath = path)
          this.copy(
            codegenSources = codegenSources :+ localSource
          ).parseArgs(rest)
        case "--maven-artifact" :: artifactMavenCoordinates :: rest =>
          // Mirrors aws-lambda / future sbt-plugin convention: `org:module:version`.
          // Resolves via Coursier (ivy2 local + Maven Central), pulling transitive
          // deps — used to put the yaga-wasm-service-sdk jar (and its tapir/sttp/circe
          // transitives) on the reflection classloader without forcing the caller to
          // enumerate every jar with `--local-classpath-jar`.
          val mavenSource = CodegenSource.MavenArtifact.parseCoordinates(artifactMavenCoordinates)
          this.copy(
            codegenSources = codegenSources :+ mavenSource
          ).parseArgs(rest)
        case "--package-prefix" :: packagePrefix :: rest =>
          this.copy(
            packagePrefix = Some(packagePrefix)
          ).parseArgs(rest)
        case "--with-infra" :: rest =>
          this.copy(
            generateInfra = Some(true)
          ).parseArgs(rest)
        case "--docker-context-absolute-path" :: filePath :: rest =>
          val path = Paths.get(filePath)
          assert(path.isAbsolute, s"Path ${filePath} is not absolute")
          this.copy(
            dockerContextAbsolutePath = Some(path)
          ).parseArgs(rest)
        case "--output-dir" :: outputDir :: rest =>
          this.copy(
            outputDir = Some(outputDir)
          ).parseArgs(rest)
        case Nil =>
          assert(codegenSources.nonEmpty, "Missing codegen sources")
          ModuleApiCodegenMainArgs(
            codegenSources = codegenSources,
            packagePrefix = packagePrefix.getOrElse(""),
            generateInfra = generateInfra.getOrElse(false),
            dockerContextAbsolutePath = dockerContextAbsolutePath,
            outputDir = outputDir.getOrElse(""),
          )
        case _ =>
          throw Exception(s"Wrong main arguments: ${args}")
  end Parser

  def parse(args: Seq[String]): ModuleApiCodegenMainArgs =
    val emptyParser = Parser(
      codegenSources = Nil,
      packagePrefix = None,
      generateInfra = None,
      dockerContextAbsolutePath = None,
      outputDir = None,
    )
    emptyParser.parseArgs(args.toList)
