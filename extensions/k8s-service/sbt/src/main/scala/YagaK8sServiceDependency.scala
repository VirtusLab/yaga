package yaga.sbt.k8sservice

import sbt._
import sbt.Keys._
import java.nio.file.{Path, Files}
import com.typesafe.sbt.packager.Keys.stage
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker

case class YagaK8sServiceDependency(
  project: Project,
  outputSubdirName: Option[String],
  packagePrefix: String,
  withInfra: Boolean
) extends yaga.sbt.YagaDependency {
  import K8sServicePluginKeys.deployableJars

  override def addSelfToProject(baseProject: Project): Project = {
    val codegenTask = Def.task {
      val projectName = (project / name).value
      val baseProjectName = (baseProject / name).value
      val outputSubdirectoryName = outputSubdirName.getOrElse(projectName)
      val codegenOutputDir = (baseProject / Compile / sourceManaged).value / "yaga-k8s-service-codegen" / outputSubdirectoryName

      val sources: Seq[Path] = (project / deployableJars).value

      val dependencyJarsChanged = (project / deployableJars).outputFileChanges.hasChanges

      implicit val log: Logger = streams.value.log

      // TODO should Docker / stagingDirectory be enough here given that we trigger staging later?
      // TODO don't evaluate if withInfra == false
      val dockerContextPath = Some((project / Docker / stage).value.toPath)
      // TODO should we somehow check if the path itself was changed?


      // TODO track changes of codegen parameters

      if (dependencyJarsChanged || !Files.exists(codegenOutputDir.toPath)) {
        log.info(s"Yaga - k8s serviceYaga - k8s service: Generating module API sources from ${projectName} for ${baseProjectName}")
        CodegenHelpers.generateModuleApiSources(localJarSources = sources, packagePrefix = packagePrefix, outputDir = codegenOutputDir.toPath, withInfra = withInfra, dockerContextPath = dockerContextPath)
      }

      (codegenOutputDir ** "*.scala").get
    }

    baseProject.settings(
      yaga.sbt.YagaPlugin.autoImport.yagaGeneratedSources ++= codegenTask.value,

      libraryDependencies ++= {
        val infraDeps =
          if (withInfra)
            Seq(
              YagaK8sServicePlugin.yagaK8sServiceBesomDep,
            )
          else
            Seq.empty
        infraDeps
      }
    )
  }
}