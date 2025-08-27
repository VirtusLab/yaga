package yaga.codegen.k8sservice

import tastyquery.Contexts.*
import tastyquery.Symbols.*
import tastyquery.Types.*
import io.github.classgraph.ClassGraph
import scala.jdk.CollectionConverters.*
import yaga.codegen.core.extractor.{CodegenSource, ContextSetup, ModelExtractor as CoreModelExtractor}
import yaga.codegen.k8sservice.ModelExtractor

class ApiExtractor():
  val serviceAppBaseClassFullName = "yaga.k8sservice.ServiceApp"

  def extractServiceAppApi(serviceAppClassFullName: String, classLoader: ClassLoader)(using Context): ExtractedServiceAppApi =
    val serviceAppClass = ctx.findTopLevelModuleClass(serviceAppClassFullName.stripSuffix("$")) // TODO handle case when class is not found, e.g. for nested classes; does the entry point have to be a (module) object?
    val serviceAppClassPackageParts = CoreModelExtractor.ownerPackageNamesChain(serviceAppClass.owner)
    val serviceAppClassName = serviceAppClass.name.toString.stripSuffix("$") // TODO should this work for both modules and classes/traits?

    val rootTypes = serviceAppClass.parents.collectFirst:
      case at: AppliedType if at.tycon.showBasic == serviceAppBaseClassFullName /* TODO don't rely on showBasic? */  =>
        at.args.collect { case tpe: Type => tpe }
    .getOrElse(throw Exception(s"Class $serviceAppClassName does not directly extend $serviceAppBaseClassFullName"))

    val List(configType) = rootTypes

    val modelSymbols = extractReferencedSymbols(rootTypes).toSeq

    val openApiSpecYaml = extractOpenApiSpecYaml(serviceAppClassFullName, classLoader)

    val referencedSchemaableSymbols = ModelExtractor().collectSchemaableTypes(rootTypes)

    ExtractedServiceAppApi(
      serviceAppClassPackageParts = serviceAppClassPackageParts,
      serviceAppClassName = serviceAppClassName,
      serviceAppConfigType = configType,
      modelSymbols = modelSymbols,
      openApiSpecYaml = openApiSpecYaml,
      referencedSchemaableSymbols = referencedSchemaableSymbols
    )

  private def extractReferencedSymbols(rootTypes: Seq[Type])(using Context): Set[ClassSymbol] =
    val modelExtractor = ModelExtractor()
    modelExtractor.collect(rootTypes)

  private def extractOpenApiSpecYaml(serviceAppClassFullName: String, classLoader: ClassLoader)(using Context): String =
    val methodName = "serverEndpointsOpenapiSpecYaml"
    val clazz = Class.forName(serviceAppClassFullName, true, classLoader)
    val moduleField = clazz.getField("MODULE$")
    val moduleInstance = moduleField.get(null)
    val method = clazz.getMethod(methodName)
    method.invoke(moduleInstance).asInstanceOf[String]

  def extractServiceAppApis(codegenSources: Seq[CodegenSource])(using Context): Seq[ExtractedServiceAppApi] =
    val jarUrls = ContextSetup.getSourcesClasspath(codegenSources).map { path =>
      path.toUri.toURL
    }.toArray

    val jarClassLoader = new java.net.URLClassLoader(jarUrls)
    val serviceAppSubclasses = new ClassGraph().overrideClassLoaders(jarClassLoader).enableClassInfo.scan()
      .getClassesImplementing(serviceAppBaseClassFullName)
      .asScala.toList
      .filter(_.getName.endsWith("$")) // ТODO Find more reliable way to filter the extected classes; this eries to exclude intermediate descendents of the ServiceApp class

    serviceAppSubclasses.map: clazz =>
      extractServiceAppApi(serviceAppClassFullName = clazz.getName, classLoader = jarClassLoader)
