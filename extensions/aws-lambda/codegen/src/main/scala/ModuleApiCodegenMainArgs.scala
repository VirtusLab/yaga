package yaga.codegen.aws

import yaga.codegen.core.extractor.CodegenSource
import java.nio.file.{Path, Paths}

case class ModuleApiCodegenMainArgs(
  codegenSources: List[CodegenSource],
  handlerClassFullName: Option[String],
  packagePrefix: String,
  generateInfra: Boolean,
  lambdaArtifactAbsolutePath: Option[Path],
  lambdaRuntime: Option[String],
  outputDir: String,
)

object ModuleApiCodegenMainArgs:
  private[aws] case class Parser(
    codegenSources: List[CodegenSource],
    handlerClassFullName: Option[String],
    packagePrefix: Option[String],
    generateInfra: Option[Boolean],
    lambdaArtifactAbsolutePath: Option[Path],
    lambdaRuntime: Option[String],
    outputDir: Option[String],
  ):
    // TODO don't allow overriding non-repeated parameters
    def parseArgs(args: List[String]): ModuleApiCodegenMainArgs = 
      args match
        case "--maven-artifact" :: artifactMavenCoordinates :: rest =>
          val mavenSource = CodegenSource.MavenArtifact.parseCoordinates(artifactMavenCoordinates)
          this.copy(
            codegenSources = codegenSources :+ mavenSource
          ).parseArgs(rest)
        case "--local-classpath-jar" :: filePath :: rest =>
          val path = Paths.get(filePath)
          assert(path.isAbsolute, s"Path ${filePath} is not absolute")
          val localSource = CodegenSource.LocalJar(absolutePath = path)
          this.copy(
            codegenSources = codegenSources :+ localSource
          ).parseArgs(rest)
        case "--package-prefix" :: packagePrefix :: rest =>
          this.copy(
            packagePrefix = Some(packagePrefix)
          ).parseArgs(rest)
        case "--with-infra" :: rest =>
          this.copy(
            generateInfra = Some(true)
          ).parseArgs(rest)
        case "--lambda-artifact-absolute-path" :: filePath :: rest =>
          val path = Paths.get(filePath)
          assert(path.isAbsolute, s"Path ${filePath} is not absolute")
          this.copy(
            lambdaArtifactAbsolutePath = Some(path)
          ).parseArgs(rest)
        case "--lambda-runtime" :: lambdaRuntime :: rest =>
          this.copy(
            lambdaRuntime = Some(lambdaRuntime)
          ).parseArgs(rest)
        case "--output-dir" :: outputDir :: rest =>
          this.copy(
            outputDir = Some(outputDir)
          ).parseArgs(rest)
        case Nil =>
          assert(codegenSources.nonEmpty, "Missing codegen sources")
          ModuleApiCodegenMainArgs(
            codegenSources = codegenSources,
            handlerClassFullName = handlerClassFullName,
            packagePrefix = packagePrefix.getOrElse(throw Exception("Missing package prefix")),
            generateInfra = generateInfra.getOrElse(false),
            lambdaArtifactAbsolutePath = lambdaArtifactAbsolutePath,
            lambdaRuntime = lambdaRuntime,
            outputDir = outputDir.getOrElse(throw Exception("Missing output dir")),
          )
        case _ =>
          throw Exception(s"Wrong main arguments: ${args}")
  end Parser

  def parse(args: Seq[String]): ModuleApiCodegenMainArgs =
    val emptyParser = Parser(
      codegenSources = Nil,
      handlerClassFullName = None,
      packagePrefix = None,
      generateInfra = None,
      lambdaArtifactAbsolutePath = None,
      lambdaRuntime = None,
      outputDir = None,
    )
    emptyParser.parseArgs(args.toList)
