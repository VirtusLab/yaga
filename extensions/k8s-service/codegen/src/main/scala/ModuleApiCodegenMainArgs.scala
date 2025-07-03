package yaga.codegen.k8sservice

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
            packagePrefix = packagePrefix.getOrElse(throw Exception("Missing package prefix")),
            generateInfra = generateInfra.getOrElse(false),
            dockerContextAbsolutePath = dockerContextAbsolutePath,
            outputDir = outputDir.getOrElse(throw Exception("Missing output dir")),
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
