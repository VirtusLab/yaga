package yaga.codegen.aws.generator

import yaga.codegen.core.generator.{SourceFile, FilePath}

import yaga.codegen.aws.extractor.ExtractedLambdaApi

class JsProxiesGenerator(lambdaApis: Seq[ExtractedLambdaApi]):
  def generateJsLambdaProxies(): Seq[SourceFile] =
    lambdaApis.map: lambdaApi =>
      generateJsLambdaProxy(lambdaApi)

  def generateJsLambdaProxy(lambdaApi: ExtractedLambdaApi) =
    val mangledClassFullName = (lambdaApi.handlerClassPackageParts :+ lambdaApi.handlerClassName).mkString("_")

    val sourceCode =
      s"""|import { ${mangledClassFullName} as HandlerClass } from "./index.js"
          |
          |const handlerInstance = new HandlerClass() 
          |
          |export const handler = async(event, context) => {
          |    return await handlerInstance.handleRequest(event, context)
          |};
          |""".stripMargin

    SourceFile(
      FilePath(List(s"${mangledClassFullName}.mjs")),
      sourceCode
    )
