package yaga.codegen.aws

import yaga.codegen.core.extractor.CodegenSource
import java.nio.file.{Path, Paths}

case class JsProxiesCodegenMainArgs(
  codegenSources: List[CodegenSource],
  outputDir: String,
)

object JsProxiesCodegenMainArgs:
  private[aws] case class Parser(
    codegenSources: List[CodegenSource],
    outputDir: Option[String],
  ):
    // TODO don't allow overriding non-repeated parameters
    def parseArgs(args: List[String]): JsProxiesCodegenMainArgs = 
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
        case "--output-dir" :: outputDir :: rest =>
          this.copy(
            outputDir = Some(outputDir)
          ).parseArgs(rest)
        case Nil =>
          assert(codegenSources.nonEmpty, "Missing codegen sources")
          JsProxiesCodegenMainArgs(
            codegenSources = codegenSources,
            outputDir = outputDir.getOrElse(throw Exception("Missing output dir")),
          )
        case _ =>
          throw Exception(s"Wrong main arguments: ${args}")
  end Parser

  def parse(args: Seq[String]): JsProxiesCodegenMainArgs =
    val emptyParser = Parser(
      codegenSources = Nil,
      outputDir = None,
    )
    emptyParser.parseArgs(args.toList)
