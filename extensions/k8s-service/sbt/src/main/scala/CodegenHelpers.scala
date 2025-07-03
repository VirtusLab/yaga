package yaga.sbt.k8sservice

import _root_.sbt._
import java.nio.file.Path
import yaga.sbt.MavenArtifactsHelpers

private[k8sservice] object CodegenHelpers {
  def generateModuleApiSources(localJarSources: Seq[Path], packagePrefix: String, outputDir: Path, withInfra: Boolean, dockerContextPath: Option[Path])(implicit log: Logger): Unit = {
    val infraFlag =
      if (withInfra)
        Seq("--with-infra")
      else
        Seq.empty

    val infraMainArgs = (
      infraFlag ++
      dockerContextPath.map(path => Seq("--docker-context-absolute-path", path.toString)).getOrElse(Nil)
    )

    val sourcesOptions = localJarSources.flatMap(path => Seq("--local-classpath-jar", path.toString))

    val mainArgs = sourcesOptions ++ Seq(
      "--package-prefix", packagePrefix,
      "--output-dir", outputDir.toString,
    ) ++ infraMainArgs

    MavenArtifactsHelpers.runMavenArtifactMainWithArgs(
      "org.virtuslab", "yaga-k8s-service-codegen_3", YagaK8sServicePlugin.yagaK8sServiceVersion,
      "yaga.codegen.k8sservice.generateModuleApiSources",
      mainArgs
    )
  }
}
