package yaga.sbt.aws

import _root_.sbt._
import java.nio.file.Path
import yaga.sbt.MavenArtifactsHelpers

private[aws] object CodegenHelpers {
  def generateModuleApiSources(localJarSources: Seq[Path], packagePrefix: String, outputDir: Path, withInfra: Boolean, lambdaArtifactAbsolutePath: Option[Path], lambdaRuntime: Option[String])(implicit log: Logger): Unit = {
    val infraFlag =
      if (withInfra)
        Seq("--with-infra")
      else
        Seq.empty

    val infraMainArgs = (
      infraFlag ++
      lambdaArtifactAbsolutePath.map(path => Seq("--lambda-artifact-absolute-path", path.toString)).getOrElse(Nil) ++
      lambdaRuntime.map(runtime => Seq("--lambda-runtime", runtime)).getOrElse(Nil)
    )

    val sourcesOptions = localJarSources.flatMap(path => Seq("--local-classpath-jar", path.toString))

    val mainArgs = sourcesOptions ++ Seq(
      "--package-prefix", packagePrefix,
      "--output-dir", outputDir.toString,
    ) ++ infraMainArgs

    MavenArtifactsHelpers.runMavenArtifactMainWithArgs(
      "org.virtuslab", "yaga-aws-lambda-codegen_3", YagaAwsLambdaPlugin.yagaAwsLambdaVersion,
      "yaga.codegen.aws.generateModuleApiSources",
      mainArgs
    )
  }

  def generateJsProxyFiles(localJarSources: Seq[Path], outputDir: Path)(implicit log: Logger): Unit = {
    val sourcesOptions = localJarSources.flatMap(path => Seq("--local-classpath-jar", path.toString))
    val mainArgs = sourcesOptions ++ Seq(
      "--output-dir", outputDir.toString,
    )

    MavenArtifactsHelpers.runMavenArtifactMainWithArgs(
      "org.virtuslab", "yaga-aws-lambda-codegen_3", YagaAwsLambdaPlugin.yagaAwsLambdaVersion,
      "yaga.codegen.aws.generateJsProxies",
      mainArgs
    )
  }
}
