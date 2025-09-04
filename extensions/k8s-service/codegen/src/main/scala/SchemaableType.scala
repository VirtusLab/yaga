package yaga.codegen.k8sservice

import tastyquery.Symbols.ClassSymbol

case class SchemaableType(
  classSymbol: ClassSymbol,
  openApiSchemaYaml: String
)
