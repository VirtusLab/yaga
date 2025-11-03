package yaga.codegen.aws.extractor

import tastyquery.Types.*

import yaga.codegen.core.extractor.ModelExtractor
import tastyquery.Contexts.Context

class AwsModelExtractor extends ModelExtractor:
  override def isBuiltinClass(ref: TypeRef)(using Context) =
    ref.showBasic.startsWith("yaga.extensions.aws.") || super.isBuiltinClass(ref)
