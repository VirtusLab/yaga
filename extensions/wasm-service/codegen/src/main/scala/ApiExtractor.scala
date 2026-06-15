package yaga.codegen.wasmservice

import tastyquery.Contexts.*
import tastyquery.Symbols.*
import tastyquery.Types.*
import io.github.classgraph.ClassGraph
import scala.jdk.CollectionConverters.*
import yaga.codegen.core.extractor.{CodegenSource, ContextSetup, ModelExtractor as CoreModelExtractor}
import yaga.codegen.wasmservice.ModelExtractor

class ApiExtractor():
  val serviceAppBaseClassFullName = "yaga.wasmservice.WasmServiceApp"

  def extractServiceAppApi(serviceAppClassFullName: String, classLoader: ClassLoader)(using Context): ExtractedServiceAppApi =
    val serviceAppClass = ctx.findTopLevelModuleClass(
      serviceAppClassFullName.stripSuffix("$")
    ) // TODO handle case when class is not found, e.g. for nested classes; does the entry point have to be a (module) object?
    val serviceAppClassPackageParts = CoreModelExtractor.ownerPackageNamesChain(serviceAppClass.owner)
    val serviceAppClassName = serviceAppClass.name.toString.stripSuffix("$") // TODO should this work for both modules and classes/traits?

    val rootTypes = serviceAppClass.parents
      .collectFirst:
        // TastyQuery's `showBasic` for a TypeRef tycon sometimes prefixes the FQN
        // with a leading `.` (empty-package root marker). Strip it defensively so
        // the match is robust across tasty-query versions.
        case at: AppliedType if at.tycon.showBasic.stripPrefix(".") == serviceAppBaseClassFullName =>
          at.args.collect { case tpe: Type => tpe }
      .getOrElse(throw Exception(s"Class $serviceAppClassName does not directly extend $serviceAppBaseClassFullName"))

    val List(configType) = rootTypes

    val modelSymbols = extractReferencedSymbols(rootTypes).toSeq

    val openApiSpecJson = extractOpenApiServerSpecJson(serviceAppClassFullName, classLoader)

    val referencedSchemaableSymbols = ModelExtractor().collectSchemaableTypeSymbols(rootTypes)

    val referencedSchemaableTypes = referencedSchemaableSymbols.map { sym =>
      val packageParts = CoreModelExtractor.ownerPackageNamesChain(sym.owner)
      val className = sym.name.toString // .stripSuffix("$")
      val classFullName = (packageParts :+ className).mkString(".") // TODO handle this logic in the right place?
      val schema = extractOpenApiClientSpecJson(classFullName, classLoader)
      sym -> SchemaableType(sym, schema)
    }.toMap

    ExtractedServiceAppApi(
      serviceAppClassPackageParts = serviceAppClassPackageParts,
      serviceAppClassName = serviceAppClassName,
      serviceAppConfigType = configType,
      modelSymbols = modelSymbols,
      openApiSpecJson = openApiSpecJson,
      referencedSchemaableTypes = referencedSchemaableTypes
    )

  private def extractReferencedSymbols(rootTypes: Seq[Type])(using Context): Set[ClassSymbol] =
    val modelExtractor = ModelExtractor()
    modelExtractor.collect(rootTypes)

  private def extractOpenApiServerSpecJson(serviceAppClassFullName: String, classLoader: ClassLoader)(using Context): String =
    val methodName = "serverEndpointsOpenapiSpecJson"
    val clazz = Class.forName(serviceAppClassFullName, true, classLoader)
    val moduleField = clazz.getField("MODULE$")
    val moduleInstance = moduleField.get(null)
    val method = clazz.getMethod(methodName)
    method.invoke(moduleInstance).asInstanceOf[String]

  // Mirrors `extractOpenApiClientSpecYaml` in `extensions/k8s-service/codegen/src/main/scala/ApiExtractor.scala`,
  // but consumes the wasm-service `ExtractEndpoints` typeclass which emits JSON instead of YAML
  // (`endpointsOpenApiSpecJson`). The user's endpoint module derives `ExtractEndpoints`, which the
  // Scala 3 macro reifies as a synthetic `derived$ExtractEndpoints` member on the companion module.
  private def extractOpenApiClientSpecJson(classFullName: String, classLoader: ClassLoader)(using Context): String =
    val clazz = Class.forName(classFullName, true, classLoader)
    val moduleField = clazz.getField("MODULE$")
    val moduleInstance = moduleField.get(null)
    val method1 = clazz.getMethod("derived$ExtractEndpoints") // TODO improve error message when the typeclass was not derived
    val extractEndpointsInstance = method1.invoke(moduleInstance)
    val method2 = extractEndpointsInstance.getClass.getMethod("endpointsOpenApiSpecJson")
    method2.invoke(extractEndpointsInstance).asInstanceOf[String]

  def extractServiceAppApis(codegenSources: Seq[CodegenSource])(using Context): Seq[ExtractedServiceAppApi] =
    val jarUrls = ContextSetup
      .getSourcesClasspath(codegenSources)
      .map { path =>
        path.toUri.toURL
      }
      .toArray

    // Use the platform classloader as parent so the codegen process's own
    // scala3-library (on Scala 3.6.4) doesn't leak into the test service's
    // classloader (compiled against a different 3.x) and cause NoSuchMethodError
    // on binary-incompatible scala3 runtime APIs (e.g. `scala.runtime.LazyVals`).
    val jarClassLoader = new java.net.URLClassLoader(jarUrls, ClassLoader.getPlatformClassLoader())
    val serviceAppSubclasses = new ClassGraph()
      .overrideClassLoaders(jarClassLoader)
      .enableClassInfo
      .scan()
      .getClassesImplementing(serviceAppBaseClassFullName)
      .asScala
      .toList
      .filter(
        _.getName.endsWith("$")
      ) // ТODO Find more reliable way to filter the extected classes; this eries to exclude intermediate descendents of the ServiceApp class

    serviceAppSubclasses.map: clazz =>
      extractServiceAppApi(serviceAppClassFullName = clazz.getName, classLoader = jarClassLoader)
