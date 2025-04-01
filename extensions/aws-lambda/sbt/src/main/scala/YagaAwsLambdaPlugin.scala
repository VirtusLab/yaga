package yaga.sbt.aws

import scala.language.implicitConversions

import _root_.sbt._
import _root_.sbt.Keys._
import _root_.sbt.AutoPlugin
import _root_.sbt.nio.{ file => _, * }
import java.nio.file.{Files, Path}
import sbtassembly.AssemblyPlugin.*
import sbtassembly.AssemblyPlugin.autoImport.*
import sbtassembly.MergeStrategy
import yaga.sbt.YagaPlugin

object YagaAwsLambdaPlugin extends AutoPlugin {
  val yagaAwsLambdaVersion = YagaPlugin.yagaVersion
  val yagaBesomAwsSdkDep = "org.virtuslab" %% "yaga-aws-lambda-besom" % yagaAwsLambdaVersion

  // val jsoniterVersion = "2.33.2"
  // val jsoniterMacrosDep = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.33.2" % "compile-internal"

  override def requires = sbtassembly.AssemblyPlugin && YagaPlugin
  override def trigger = allRequirements

  object autoImport {
    // TODO Hide keys from users if they don't have to be set manually

    val yagaAwsLambdaRuntime = settingKey[String]("Yaga AWS Lambda runtime") // TODO make it more typesafe
    val yagaAwsLambdaHandlerClassName = settingKey[String]("Fully qualified name (with package) of yaga AWS Lambda handler class name")
    val yagaAwsLambdaAssembly = taskKey[Path]("Assembled AWS lambda jar")
    val yagaAwsDeployableLambdaArtifact = taskKey[Path]("Deployable AWS lambda artifact")
    val yagaAwsRunCodegen: TaskKey[Seq[File]] = taskKey[Seq[File]]("Generate code for yaga AWS")
    val yagaAwsLambdaGraalLambdaArchivePath = settingKey[File]("Path to the graal lambda archive")
    val yagaAwsLambdaGraaledImage = taskKey[Path]("Path to the graaled image")
    val yagaAwsLambdaGraalBootstrapFile = taskKey[Path]("Bootstrap file for graaled lambda")

    implicit class ProjectYagaDependencyOps(project: Project) {
      def awsJvmLambda() = {
        JvmHelpers.awsJvmLambda(project)
      }

      def awsJsLambda(
        handlerClass: String
      ) = {
        JsHelpers.awsJsLambda(project, handlerClass = handlerClass)
      }

      def awsGraalLambda() = {
        GraalHelpers.awsGraalLambda(project)
      }

      def awsLambdaModel(outputSubdirName: Option[String] = None, packagePrefix: String = ""): YagaAwsLambdaProjectDependency = {
        YagaAwsLambdaProjectDependency(
          project = project,
          outputSubdirName = outputSubdirName,
          packagePrefix = packagePrefix,
          withInfra = false
        )
      }

      def awsLambdaInfra(outputSubdirName: Option[String] = None, packagePrefix: String = ""): YagaAwsLambdaProjectDependency = {
        YagaAwsLambdaProjectDependency(
          project = project,
          outputSubdirName = outputSubdirName,
          packagePrefix = packagePrefix,
          withInfra = true
        )
      }
    }
  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    yagaAwsLambdaAssembly := {
      assembly.value.toPath
    },
    assembly / assemblyMergeStrategy := defaultAssemblyMergeStrategy.value,
  )

  def defaultAssemblyMergeStrategy = Def.setting[String => MergeStrategy] {
    (path: String) => path match {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.discard
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  }
}