package yaga.sbt.kubernetes

import sbt.*
import sbt.Keys.*
import java.nio.file.Path
import yaga.sbt.YagaPlugin
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin

object K8sServicePlugin extends AutoPlugin with K8sServicePluginKeys {
  override def requires = JavaAppPackaging && DockerPlugin && YagaPlugin
  override def trigger = allRequirements



  object autoImport {
    val k8sService = taskKey[Unit]("Build a k8s service")

    implicit class YagaK8sServiceProjectOps(project: Project) {
      def yagaK8sServiceModel(outputSubdirName: Option[String] = None, packagePrefix: String = ""): YagaK8sServiceDependency = {
        YagaK8sServiceDependency(
          project = project,
          outputSubdirName = outputSubdirName,
          packagePrefix = packagePrefix,
          withInfra = false
        )
      }

      def yagaK8sServiceInfra(outputSubdirName: Option[String] = None, packagePrefix: String = ""): YagaK8sServiceDependency = {
        YagaK8sServiceDependency(
          project = project,
          outputSubdirName = outputSubdirName,
          packagePrefix = packagePrefix,
          withInfra = true
        )
      }
    }
  }

  override def projectSettings = Seq(
    deployableJars := (Compile / fullClasspathAsJars).value.map(_.data.toPath)
  )
}

trait K8sServicePluginKeys {
  val deployableJars = taskKey[Seq[Path]]("The deployable jars for the k8s service")
}

object K8sServicePluginKeys extends K8sServicePluginKeys