package yaga.codegen.k8sservice

import tastyquery.Contexts.*
import tastyquery.Symbols.*
import tastyquery.Types.*
import io.github.classgraph.ClassGraph
import scala.jdk.CollectionConverters.*
import yaga.codegen.core.extractor.{CodegenSource, ContextSetup, ModelExtractor}

class ApiExtractor():
  val serviceAppBaseClassFullName = "yaga.k8sservice.ServiceApp"

  def extractServiceAppApi(serviceAppClassFullName: String)(using Context): ExtractedServiceAppApi =
    val serviceAppClass = ctx.findTopLevelModuleClass(serviceAppClassFullName.stripSuffix("$")) // TODO handle case when class is not found, e.g. for nested classes; does the entry point have to be a (module) object?
    val serviceAppClassPackageParts = ModelExtractor.ownerPackageNamesChain(serviceAppClass.owner)
    val serviceAppClassName = serviceAppClass.name.toString.stripSuffix("$") // TODO should this work for both modules and classes/traits?

    val rootTypes = serviceAppClass.parents.collectFirst:
      case at: AppliedType if at.tycon.showBasic == serviceAppBaseClassFullName /* TODO don't rely on showBasic? */  =>
        at.args.collect { case tpe: Type => tpe }
    .getOrElse(throw Exception(s"Class $serviceAppClassName does not directly extend $serviceAppBaseClassFullName"))

    val List(configType) = rootTypes

    val modelSymbols = extractReferencedSymbols(rootTypes).toSeq

    ExtractedServiceAppApi(
      serviceAppClassPackageParts = serviceAppClassPackageParts,
      serviceAppClassName = serviceAppClassName,
      serviceAppConfigType = configType,
      modelSymbols = modelSymbols
    )

  private def extractReferencedSymbols(rootTypes: Seq[Type])(using Context): Set[ClassSymbol] =
    val modelExtractor = ModelExtractor()
    modelExtractor.collect(rootTypes)

  def extractServiceAppApis(codegenSources: Seq[CodegenSource])(using Context): Seq[ExtractedServiceAppApi] =
    val jarUrls = ContextSetup.getSourcesClasspath(codegenSources).map { path =>
      path.toUri.toURL
    }.toArray

    val jarClassLoader = new java.net.URLClassLoader(jarUrls)
    val serviceAppSubclasses = new ClassGraph().overrideClassLoaders(jarClassLoader).enableClassInfo.scan().getClassesImplementing(serviceAppBaseClassFullName).asScala.toList

    serviceAppSubclasses.map: clazz =>
      extractServiceAppApi(serviceAppClassFullName = clazz.getName)
