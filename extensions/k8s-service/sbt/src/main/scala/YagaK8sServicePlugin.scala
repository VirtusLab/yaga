package yaga.sbt.k8sservice

import sbt.*
import sbt.Keys.*
import java.nio.file.Path
import yaga.sbt.YagaPlugin
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerCommands}
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.{stagingDirectory => universalStagingDirectory}

object YagaK8sServicePlugin extends AutoPlugin with K8sServicePluginKeys {
  val yagaK8sServiceVersion = YagaPlugin.yagaVersion
  val yagaK8sServiceBesomDep = "org.virtuslab" %% "yaga-k8s-service-besom" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiDep = "org.virtuslab" %% "yaga-k8s-service-sdk-openapi" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiNettyFutureDep = "org.virtuslab" %% "yaga-k8s-service-sdk-netty-future" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiNettySyncDep = "org.virtuslab" %% "yaga-k8s-service-sdk-netty-sync" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiClientDep = "org.virtuslab" %% "yaga-k8s-service-sdk-openapi-client" % yagaK8sServiceVersion

  override def requires = JavaAppPackaging && DockerPlugin && YagaPlugin
  override def trigger = allRequirements

  object autoImport {
    val k8sService = taskKey[Unit]("Build a k8s service")
    sealed trait ServerType
    object ServerType {
      case object NettyFuture extends ServerType
      case object NettySync extends ServerType
    }

    implicit class YagaK8sServiceProjectOps(project: Project) {
      def yagaOpenApiK8sService(serverType: ServerType) = {
        project
          .enablePlugins(JavaAppPackaging)
          .enablePlugins(DockerPlugin)
          .settings(
            libraryDependencies ++= Seq(
              yagaK8sServiceSdkOpenApiDep,
              serverType match {
                case ServerType.NettyFuture => yagaK8sServiceSdkOpenApiNettyFutureDep
                case ServerType.NettySync   => yagaK8sServiceSdkOpenApiNettySyncDep
              }
            )
          )
      }

      def yagaOpenApiClient = {
        project.settings(
          libraryDependencies ++= Seq(
            yagaK8sServiceSdkOpenApiClientDep
          )
        )
      }

      def yagaOpenApiEndpoints = {
        project.settings(
          libraryDependencies ++= Seq(
            yagaK8sServiceSdkOpenApiDep
          )
        )
      }

      def yagaOpenApiK8sServiceModel(outputSubdirName: Option[String] = None, packagePrefix: String = ""): YagaK8sServiceDependency = {
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
    deployableJars := (Compile / fullClasspathAsJars).value.map(_.data.toPath),

    // Remove the snp-multi-stage-id label from the docker commands to make generation of the Dockerfile idempotent
    Docker / dockerCommands := {
      dockerCommands.value.filter {
        case Cmd("LABEL", label) => !label.startsWith("snp-multi-stage-id=")
        case _                   => true
      }
    }
  )
}

trait K8sServicePluginKeys {
  val deployableJars = taskKey[Seq[Path]]("The deployable jars for the k8s service")
}

object K8sServicePluginKeys extends K8sServicePluginKeys
