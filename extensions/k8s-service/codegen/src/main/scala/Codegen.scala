package yaga.codegen.k8sservice

import yaga.codegen.core.extractor.{CodegenSource, ContextSetup, CoursierHelpers}
import yaga.codegen.core.generator.SourcesWriter
import yaga.codegen.core.generator.SourceFile
import tastyquery.Contexts.*
import tastyquery.Symbols.*
import java.nio.file.Path

object Codegen:
  def sourcesForModuleApi(
    codegenSources: List[CodegenSource],
    packagePrefix: String,
    generateInfra: Boolean,
    dockerContextAbsolutePath: Option[Path]
  ): Seq[SourceFile] =
    given Context = ContextSetup.contextFromCodegenSources(codegenSources)

    val packagePrefixParts = packagePrefix.split('.').toSeq.filter(_.nonEmpty)
    val extractedApis = ApiExtractor().extractServiceAppApis(codegenSources = codegenSources)
    val generator = ModuleApiGenerator(packagePrefixParts, extractedApis)
    val modelSources = generator.generateModelSources()
    val infraSources = 
      if generateInfra then
        generator.generateServiceAppResourceClasses(dockerContextPath = dockerContextAbsolutePath.get) ++
        generator.generateSourcesForSchemaableTypes()
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
      dockerContextAbsolutePath = codegenMainArgs.dockerContextAbsolutePath,
    )

    val outputDirPath = os.Path(codegenMainArgs.outputDir)

    SourcesWriter().writeSources(outputDirPath, sources, summaryFile = None, cleanUpOutputDir = true)
