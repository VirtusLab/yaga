package yaga.codegen.k8sservice

import tastyquery.Symbols.*
import tastyquery.Types.*

case class ExtractedServiceAppApi(
  serviceAppClassPackageParts: Seq[String],
  serviceAppClassName: String,
  serviceAppConfigType: Type,
  modelSymbols: Seq[Symbol],
  openApiSpecYaml: String,
  referencedSchemaableTypes: Map[ClassSymbol, SchemaableType]
)
