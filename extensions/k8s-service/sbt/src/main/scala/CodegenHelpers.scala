package yaga.sbt.k8sservice

import _root_.sbt._
import java.nio.file.Path
import yaga.sbt.MavenArtifactsHelpers

private[k8sservice] object CodegenHelpers {
  def generateModuleApiSources(localJarSources: Seq[Path], packagePrefix: String, outputDir: Path, withInfra: Boolean, dockerContextPath: Option[Path])(implicit log: Logger): Unit = {
    val mainArgs = buildCodegenMainArgs(localJarSources, packagePrefix, outputDir, withInfra, dockerContextPath, wasmRuntime = None)

    MavenArtifactsHelpers.runMavenArtifactMainWithArgs(
      "org.virtuslab", "yaga-k8s-service-codegen_3", YagaK8sServicePlugin.yagaK8sServiceVersion,
      "yaga.codegen.k8sservice.generateModuleApiSources",
      mainArgs
    )
  }

  def generateWasmModuleApiSources(
    localJarSources: Seq[Path],
    packagePrefix: String,
    outputDir: Path,
    withInfra: Boolean,
    dockerContextPath: Option[Path],
    wasmRuntime: WasmRuntime = WasmRuntime.EmbeddedWasmtime
  )(implicit log: Logger): Unit = {
    val mainArgs = buildCodegenMainArgs(localJarSources, packagePrefix, outputDir, withInfra, dockerContextPath, wasmRuntime = Some(wasmRuntime))

    MavenArtifactsHelpers.runMavenArtifactMainWithArgs(
      "org.virtuslab", "yaga-wasm-service-codegen_3", YagaK8sServicePlugin.yagaK8sServiceVersion,
      "yaga.codegen.wasmservice.generateModuleApiSources",
      mainArgs
    )
  }

  private def buildCodegenMainArgs(
    localJarSources: Seq[Path],
    packagePrefix: String,
    outputDir: Path,
    withInfra: Boolean,
    dockerContextPath: Option[Path],
    wasmRuntime: Option[WasmRuntime]
  ): Seq[String] = {
    val infraFlag =
      if (withInfra)
        Seq("--with-infra")
      else
        Seq.empty

    val runtimeArgs = wasmRuntime match {
      case Some(WasmRuntime.RuntimeClass(className)) => Seq("--wasm-runtime-class", className)
      case _ => Seq.empty  // EmbeddedWasmtime is the default, no flag needed
    }

    val infraMainArgs = (
      infraFlag ++
      dockerContextPath.map(path => Seq("--docker-context-absolute-path", path.toString)).getOrElse(Nil) ++
      runtimeArgs
    )

    val sourcesOptions = localJarSources.flatMap(path => Seq("--local-classpath-jar", path.toString))

    sourcesOptions ++ Seq(
      "--package-prefix", packagePrefix,
      "--output-dir", outputDir.toString,
    ) ++ infraMainArgs
  }
}
