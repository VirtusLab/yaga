package yaga.sbt.aws

import _root_.sbt._
import _root_.sbt.Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import java.nio.file.{Files, Path}

import yaga.sbt.YagaPlugin
import yaga.sbt.YagaPlugin.autoImport.yagaGeneratedSources

import YagaAwsLambdaPlugin._
import YagaAwsLambdaPlugin.autoImport._

private[aws] object JsHelpers {
  def awsJsLambda(
    project: Project
  ) = {
    project
      .enablePlugins(ScalaJSPlugin)
      .settings(
        libraryDependencies ++= Seq(
          "org.virtuslab" %% "yaga-aws-lambda-sdk_sjs1" % yagaAwsLambdaVersion, // TODO use %%%
          //jsoniterMacrosDep
        ),
        addCompilerPlugin("org.virtuslab" %% "yaga-aws-lambda-compiler-plugin" % "0.1.0-SNAPSHOT"),
        yagaAwsLambdaRuntime := "nodejs22.x",
        scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
        yagaAwsLambdaProxyFiles := {
          implicit val log: Logger = streams.value.log
          val codegenOutputDir = (Compile / target).value / "yaga" / "aws-lambda" / "js-proxies" 
          val codegenSources: Seq[Path] = Seq(yagaAwsLambdaAssembly.value)
          val dependenciesChanged = yagaAwsLambdaAssembly.outputFileChanges.hasChanges
          if (dependenciesChanged || !Files.exists(codegenOutputDir.toPath)) {
            CodegenHelpers.generateJsProxyFiles(
              localJarSources = codegenSources,
              outputDir = codegenOutputDir.toPath
            )
          }
          
          (codegenOutputDir ** "*.mjs").get
        },
        yagaAwsDeployableLambdaArtifact := {
          // TODO Add caching
          val projectName = (project / name).value
          val fullLinkOutputDir = (Compile / fullLinkJSOutput).value
          val fullLinkMainFile = fullLinkOutputDir / "main.js"
          val zipFile = (Compile / target).value / "yaga" / "aws-lambda" / "lambda.zip"
          val handlerProxyFiles = yagaAwsLambdaProxyFiles.value.map(file => file -> file.name)
          val zipInputs = handlerProxyFiles ++ Seq(
            fullLinkMainFile -> "index.js"
          )
          val log = streams.value.log
          log.info(s"Yaga - AWS Lambda: Packaging deployable AWS Lambda artifact for ${projectName} into ${zipFile}")
          val formattedZipInputs = zipInputs.map { case (path, name) => s"  * ${path} -> ${name}" }.mkString("\n")
          log.debug(s"Yaga - AWS Lambda: Creating zip file ${zipFile} with files:\n${formattedZipInputs}")
          sbt.io.IO.zip(zipInputs, zipFile, time = None)
          zipFile.toPath
        },
      )
  }
}