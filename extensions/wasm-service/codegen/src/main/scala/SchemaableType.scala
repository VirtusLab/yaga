package yaga.codegen.wasmservice

import tastyquery.Symbols.ClassSymbol

case class SchemaableType(
  classSymbol: ClassSymbol,
  openApiSchemaJson: String
)
