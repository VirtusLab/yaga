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
import sbtcrossproject.CrossProject
import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import org.scalajs.linker.interface.ModuleKind

object YagaK8sServicePlugin extends AutoPlugin with K8sServicePluginKeys {
  val yagaK8sServiceVersion = YagaPlugin.yagaVersion
  val yagaK8sServiceBesomDep = "org.virtuslab" %% "yaga-k8s-service-besom" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiDep = "org.virtuslab" %% "yaga-k8s-service-sdk-openapi" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiNettyFutureDep = "org.virtuslab" %% "yaga-k8s-service-sdk-netty-future" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiNettySyncDep = "org.virtuslab" %% "yaga-k8s-service-sdk-netty-sync" % yagaK8sServiceVersion
  val yagaK8sServiceSdkOpenApiClientDep = "org.virtuslab" %% "yaga-k8s-service-sdk-openapi-client" % yagaK8sServiceVersion

  // WASM service — JVM-side artifacts used during codegen/extraction
  val yagaWasmServiceSdkDep = "org.virtuslab" %% "yaga-wasm-service-sdk" % yagaK8sServiceVersion
  val yagaWasmServiceSdkClientDep = "org.virtuslab" %% "yaga-wasm-service-sdk-client" % yagaK8sServiceVersion
  // WASM service — Besom resource types for infra projects
  val yagaWasmServiceBesomDep = "org.virtuslab" %% "yaga-wasm-service-besom" % yagaK8sServiceVersion
  // JS-side runtime artifacts are referenced via %%% inline inside .jsSettings(...), because
  // %%% is a whitebox macro that reads platformDepsCrossVersion.value from the enclosing
  // Setting scope and cannot be used to build a top-level val.

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

    implicit class CrossProjectYagaWasmOps(cp: CrossProject) {
      def yagaWasmService: CrossProject = cp
        .jvmSettings(
          libraryDependencies += yagaWasmServiceSdkDep,
          // The JVM half is not packaged with JavaAppPackaging/DockerPlugin (as k8s-service
          // projects are), so YagaK8sServicePlugin's projectSettings — including the
          // `deployableJars` task definition — don't auto-apply. Set it here so that
          // `YagaWasmServiceDependency` can read the JVM classpath for reflection.
          deployableJars := (Compile / fullClasspathAsJars).value.map(_.data.toPath)
        )
        .jsConfigure(_.enablePlugins(ScalaJSPlugin))
        .jsSettings(
          libraryDependencies += "org.virtuslab" %%% "yaga-wasm-service-sdk-runtime" % yagaK8sServiceVersion,
          // NOTE: scala-wasm fork's `scalaJSWitDirectory` is a `globalSettings` File key
          // defaulting to a relative `wit` path, which resolves against the JVM working
          // directory (= sbt root) — not the project base. In multi-project builds that
          // silently misses `<projectBase>/wit/`. Override it with an absolute path so the
          // wit-bindgen source generator finds the WIT files regardless of where sbt is
          // launched from. Mirror the same value into `scalaJSLinkerConfig` so the linker
          // (which embeds wit metadata into the .wasm) sees the same directory.
          Compile / scalaJSWitDirectory := baseDirectory.value / "wit",
          scalaJSLinkerConfig := {
            val witDir = (Compile / scalaJSWitDirectory).value.getAbsolutePath
            // NOTE: prettyPrint=false on purpose. The scala-wasm fork's WAT TextWriter
            // (org.scalajs.linker.backend.webassembly.TextWriter) crashes on certain
            // array-get instructions emitted by tapir/circe-derived code. The pretty-printed
            // WAT is debug-only — the actual .wasm binary still emits correctly when this
            // is disabled. Re-enable per-project if you need WAT for debugging.
            scalaJSLinkerConfig.value
              .withPrettyPrint(false)
              .withExperimentalUseWebAssembly(true)
              .withModuleKind(ModuleKind.WasmComponent)
              .withWasmFeatures(
                // _.withTargetPureWasm(true)
                // _.withComponentModel(true)
                _.withWitDirectory(Some(witDir))
              )
          }
        )

      def yagaWasmServiceClient: CrossProject = cp
        .jvmSettings(libraryDependencies += yagaWasmServiceSdkClientDep)
        .jsSettings(libraryDependencies += "org.virtuslab" %%% "yaga-wasm-service-sdk-client-runtime" % yagaK8sServiceVersion)

      def yagaWasmServiceModel(
          outputSubdirName: Option[String] = None,
          packagePrefix: String = ""
      ): YagaWasmServiceDependency =
        YagaWasmServiceDependency(
          crossProject = cp,
          outputSubdirName = outputSubdirName,
          packagePrefix = packagePrefix,
          withInfra = false
        )

      def yagaWasmServiceInfra(
          outputSubdirName: Option[String] = None,
          packagePrefix: String = "",
          wasmRuntime: WasmRuntime = WasmRuntime.EmbeddedWasmtime
      ): YagaWasmServiceDependency =
        YagaWasmServiceDependency(
          crossProject = cp,
          outputSubdirName = outputSubdirName,
          packagePrefix = packagePrefix,
          withInfra = true,
          wasmRuntime = wasmRuntime
        )
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
