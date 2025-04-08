package yaga.codegen.aws

import yaga.codegen.core.extractor.{CodegenSource, ContextSetup, CoursierHelpers}
import yaga.codegen.core.generator.SourcesWriter
import yaga.codegen.core.generator.SourceFile
import yaga.codegen.aws.extractor.LambdaApiExtractor
import yaga.codegen.aws.generator.{ModuleApiGenerator, JsProxiesGenerator}

import tastyquery.Contexts.*
import tastyquery.Symbols.*
import java.nio.file.Path

object AwsCodegen:
  def sourcesForModuleApi(
    codegenSources: List[CodegenSource],
    packagePrefix: String,
    generateInfra: Boolean,
    lambdaArtifactAbsolutePath: Option[Path],
    lambdaRuntime: Option[String]
  ): Seq[SourceFile] =
    given Context = ContextSetup.contextFromCodegenSources(codegenSources)

    val packagePrefixParts = packagePrefix.split('.').toSeq.filter(_.nonEmpty)

    val extractedApis = LambdaApiExtractor().extractLambdaApis(codegenSources = codegenSources)

    val generator = ModuleApiGenerator(packagePrefixParts, extractedApis)
    
    val modelSources = generator.generateModelSources()

    val infraSources: Seq[SourceFile] = 
      if generateInfra then
        lambdaRuntime match
          case Some(runtime @ "java21") =>
            generator.generateJvmLambdas(jarPath = lambdaArtifactAbsolutePath.get)
          case Some(runtime @ "nodejs22.x") =>
            generator.generateNodejsLambdas(deployableArchivePath = lambdaArtifactAbsolutePath.get)
          case Some(runtime @ "graal") =>
            generator.generateGraalLambdas(deployableArchivePath = lambdaArtifactAbsolutePath.get)
          case Some(runtime) =>
            throw Exception(s"Unsupported lambda runtime: $runtime. Should be java21 or nodejs22.x or graal")
          case None =>
            throw Exception("Lambda runtime must be specified for infra generation")
      else
        Seq.empty

    modelSources ++ infraSources


  @main
  def generateModuleApiSources(args: String*) =
    val codegenMainArgs = ModuleApiCodegenMainArgs.parse(args.toList)

    val sources = sourcesForModuleApi(
      codegenSources = codegenMainArgs.codegenSources,
      packagePrefix = codegenMainArgs.packagePrefix,
      generateInfra = codegenMainArgs.generateInfra,
      lambdaArtifactAbsolutePath = codegenMainArgs.lambdaArtifactAbsolutePath,
      lambdaRuntime = codegenMainArgs.lambdaRuntime
    )
    val outputDirPath = os.Path(codegenMainArgs.outputDir)

    SourcesWriter().writeSources(outputDirPath, sources, summaryFile = None, cleanUpOutputDir = true)

  def sourcesForJsProxies(
    codegenSources: List[CodegenSource],
  ): Seq[SourceFile] =
    given Context = ContextSetup.contextFromCodegenSources(codegenSources)
    val extractedApis = LambdaApiExtractor().extractLambdaApis(codegenSources = codegenSources)
    val generator = JsProxiesGenerator(extractedApis)
    generator.generateJsLambdaProxies()

  @main
  def generateJsProxies(args: String*) =
    val codegenMainArgs = JsProxiesCodegenMainArgs.parse(args.toList)

    val sources = sourcesForJsProxies(
      codegenSources = codegenMainArgs.codegenSources
    )
    val outputDirPath = os.Path(codegenMainArgs.outputDir)

    SourcesWriter().writeSources(outputDirPath, sources, summaryFile = None, cleanUpOutputDir = true)
