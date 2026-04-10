package yaga.codegen.wasmservice

import yaga.codegen.core.extractor.{CodegenSource, ContextSetup}
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

  // Kept from the Phase 2 scaffold — useful for debugging extraction without running
  // the full generator pipeline.
  @main
  def extractServiceAppApisSmoke(args: String*): Unit =
    val parsed = ModuleApiCodegenMainArgs.parse(args.toList)
    given Context = ContextSetup.contextFromCodegenSources(parsed.codegenSources)
    val apis = ApiExtractor().extractServiceAppApis(parsed.codegenSources)
    apis.foreach { api =>
      val fqn = (api.serviceAppClassPackageParts :+ api.serviceAppClassName).mkString(".")
      println(s"== $fqn ==")
      println(s"config: ${api.serviceAppConfigType.showBasic}")
      println(s"models: ${api.modelSymbols.map(_.name).mkString(", ")}")
      println(s"spec:\n${api.openApiSpecJson}")
    }
