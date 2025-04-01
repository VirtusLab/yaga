package yaga.sbt.aws

import _root_.sbt._
import _root_.sbt.Keys._

import YagaAwsLambdaPlugin._
import YagaAwsLambdaPlugin.autoImport._

private[aws] object JvmHelpers {
  def awsJvmLambda(project: Project) = {
    project.settings(
      libraryDependencies ++= Seq(
        "org.virtuslab" %% "yaga-aws-lambda-sdk" % yagaAwsLambdaVersion,
        //jsoniterMacrosDep
      ),
      yagaAwsLambdaRuntime := "java21",
      yagaAwsDeployableLambdaArtifact := yagaAwsLambdaAssembly.value,
    )
  }
}