package yaga.sbt.aws

import _root_.sbt._
import _root_.sbt.Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*

import yaga.sbt.YagaPlugin
import yaga.sbt.YagaPlugin.autoImport.yagaGeneratedSources

import YagaAwsLambdaPlugin._
import YagaAwsLambdaPlugin.autoImport._

private[aws] object JsHelpers {
  def awsJsLambda(
    project: Project,
    handlerClass: String
  ) = {
    project
      .enablePlugins(ScalaJSPlugin)
      .settings(
        libraryDependencies ++= Seq(
          "org.virtuslab" %% "yaga-aws-lambda-sdk_sjs1" % yagaAwsLambdaVersion, // TODO use %%%
          //jsoniterMacrosDep
        ),
        yagaAwsLambdaRuntime := "nodejs22.x",
        yagaAwsLambdaHandlerClassName := handlerClass,
        scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
        yagaGeneratedSources ++= {
          val handlerClassName = (project / yagaAwsLambdaHandlerClassName).value
          val file = (Compile / sourceManaged).value / "yaga-aws-js" / "HandlerProxy.scala"
          IO.write(file, jsProxyHandlerCode(handlerClassName))
          Seq(file)
        },
        yagaAwsDeployableLambdaArtifact := {
          val fullLinkOutputDir = (Compile / fullLinkJSOutput).value
          val fullLinkMainFile = fullLinkOutputDir / "main.js"
          val zipFile = (Compile / target).value / "yaga" / "lambda-aws" / "lambda.zip"
          val zipInputs = Seq(
            fullLinkMainFile -> "index.js"
          )
          sbt.io.IO.zip(zipInputs, zipFile, time = None)
          zipFile.toPath
        },
      )
  }

  def jsProxyHandlerCode(handlerClassName: String): String = {
    val handlerInstance = "new com.virtuslab.child_lambda_a.ChildLambdaA"
    s"""
       |import scala.scalajs.js
       |import scala.scalajs.js.annotation.JSExportTopLevel
       |import scala.scalajs.js.JSConverters._
       |import scala.concurrent.Future
       |import scala.concurrent.ExecutionContext.Implicits.global
       |import yaga.extensions.aws.lambda.LambdaContext
       |
       |@JSExportTopLevel("handlerInstance")
       |val handlerInstance = new $handlerClassName
       |
       |@JSExportTopLevel("handler")
       |def handler(event: js.Any, context: LambdaContext.UnderlyingContext): Any =
       |  handlerInstance.handleRequest(event, context)
       |""".stripMargin
  }
}