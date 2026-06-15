package yaga.codegen.wasmservice

import tastyquery.Symbols.*
import tastyquery.Types.*

case class ExtractedServiceAppApi(
  serviceAppClassPackageParts: Seq[String],
  serviceAppClassName: String,
  serviceAppConfigType: Type,
  modelSymbols: Seq[Symbol],
  openApiSpecJson: String,
  referencedSchemaableTypes: Map[ClassSymbol, SchemaableType]
)
