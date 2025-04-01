package yaga.sbt.aws

import _root_.sbt._
import _root_.sbt.Keys._
import java.nio.file.{Files, Path, Paths}

import com.typesafe.sbt.packager.graalvmnativeimage.GraalVMNativeImagePlugin
import GraalVMNativeImagePlugin.autoImport.{GraalVMNativeImage, graalVMNativeImageOptions, graalVMNativeImageGraalVersion/* , graalVMNativeImageCommand */}

import YagaAwsLambdaPlugin._
import YagaAwsLambdaPlugin.autoImport._
import yaga.sbt.YagaPlugin.autoImport.yagaGeneratedResources

private[aws] object GraalHelpers {
  val remoteBuilderUserEnvVar = "YAGA_AWS_LAMBDA_GRAAL_REMOTE_USER"
  val remoteBuilderIpEnvVar = "YAGA_AWS_LAMBDA_GRAAL_REMOTE_IP"

  def awsGraalLambda(project: Project) = {
    project
      .enablePlugins(GraalVMNativeImagePlugin)
      .settings(
        libraryDependencies ++= Seq(
          "org.virtuslab" %% "yaga-aws-lambda-sdk" % yagaAwsLambdaVersion,
          "com.amazonaws" % "aws-lambda-java-runtime-interface-client" % "2.4.2" // versions > 2.4.2 <= 2.6.0 are buggy and don't work with GraalVM
        ),
        Compile / mainClass := Some("com.amazonaws.services.lambda.runtime.api.client.AWSLambda"),
        yagaGeneratedResources ++= {
          generateGraalResources((Compile / resourceManaged).value)
        },
        graalVMNativeImageOptions ++= Seq(
          "--verbose",
          "-H:+ReportExceptionStackTraces",
          "--no-fallback",
          "--initialize-at-build-time=org.slf4j",
          "--enable-url-protocols=http",
          "'-H:IncludeResources=jni/.*'", // Asterisk had to be escaped - TODO?
          "--add-opens=java.base/java.util=ALL-UNNAMED"
        ),

        yagaAwsLambdaRuntime := "graal",

        yagaAwsLambdaGraalBootstrapFile := {
          val fileContent = """#!/usr/bin/env bash
                              |
                              |./lambda $_HANDLER""".stripMargin

          val filePath = (Compile / target).value / "yaga" / "graal" / "bootstrap"
          IO.write(filePath, fileContent)
          filePath.setExecutable(true, false) // executable for everyone
          filePath.toPath
        },

        yagaAwsLambdaGraaledImage := Def.taskDyn {
          val remoteBuilderUser = sys.env.getOrElse(remoteBuilderUserEnvVar, throw new Exception(s"${remoteBuilderUserEnvVar} is not set"))
          val remoteBuilderIp = sys.env.getOrElse(remoteBuilderIpEnvVar, throw new Exception(s"${remoteBuilderIpEnvVar} is not set"))
          val fatJarLocalPath = yagaAwsLambdaAssembly.value
          val projectName = (Compile / name).value
          val tmpName = s"lambda-${projectName}" // TODO Find better way to assure no race conditions when building in parallel
          val fatJarRemotePath = Paths.get(s"/tmp/${tmpName}.jar")
          val resultArtifactRemotePath = Paths.get(s"/tmp/${tmpName}")
          val resultArtifactLocalPath = (target.value / "yaga" / "graal" / "lambda").toPath
          val graalVmNativeImageCommand = "native-image" //GraalVMNativeImage / graalVMNativeImageCommand
          val nativeImageExtraSettings = graalVMNativeImageOptions.value

          lazy val dependenciesChanged = yagaAwsLambdaAssembly.outputFileChanges.hasChanges

          if (Files.exists(resultArtifactLocalPath) && !dependenciesChanged) {
            Def.task {
              resultArtifactLocalPath
            }
          } else {
            Def.task {
              makeGraaledImageRemotely(
                remoteBuilderUser = remoteBuilderUser,
                remoteBuilderIp = remoteBuilderIp,
                fatJarLocalPath = fatJarLocalPath,
                fatJarRemotePath = fatJarRemotePath,
                graalVmNativeImageCommand = graalVmNativeImageCommand,
                nativeImageExtraSettings = nativeImageExtraSettings,
                resultArtifactRemotePath = resultArtifactRemotePath,
                resultArtifactLocalPath = resultArtifactLocalPath
              )

              resultArtifactLocalPath
            }
          }
        }.value,

        yagaAwsLambdaGraalLambdaArchivePath := target.value / "yaga-graal" / "function.zip",

        // TODO Add caching
        yagaAwsDeployableLambdaArtifact := {
          val zipFile = yagaAwsLambdaGraalLambdaArchivePath.value

          val zipInputs = Seq(
            yagaAwsLambdaGraaledImage.value.toFile -> "lambda",
            yagaAwsLambdaGraalBootstrapFile.value.toFile -> "bootstrap"
          )
          sbt.io.IO.zip(zipInputs, zipFile, time = None)
          zipFile.toPath
        }
      )
  }

  private def generateGraalResources(resourceManaged: File): Seq[File] = {
    val resourcePathsToCopy = Seq[String](
      "META-INF/native-image/com.amazonaws/aws-lambda-java-core/native-image.properties",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-core/reflect-config.json",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-events/native-image.properties",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-events/reflect-config.json",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-runtime-interface-client/native-image.properties",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-runtime-interface-client/reflect-config.json",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-runtime-interface-client/jni-config.json",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-runtime-interface-client/resource-config.json",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-serialization/native-image.properties",
      "META-INF/native-image/com.amazonaws/aws-lambda-java-serialization/reflect-config.json"
    )

    resourcePathsToCopy.map { resourcePath =>
      val file = resourceManaged.toPath.resolve(resourcePath).toFile
      if (!file.exists()) {
        val resourceContent = getClass.getClassLoader.getResourceAsStream(resourcePath)
        IO.write(file, resourceContent.readAllBytes)
      }
      file
    }
  }

  private def makeGraaledImageRemotely(
    remoteBuilderUser: String,
    remoteBuilderIp: String,
    fatJarLocalPath: Path,
    fatJarRemotePath: Path,
    graalVmNativeImageCommand: String,
    nativeImageExtraSettings: Seq[String],
    resultArtifactRemotePath: Path,
    resultArtifactLocalPath: Path
  ): Unit = {
    import scala.sys.process._

    val copyToRemoteCommand = Seq(
      "scp",
      fatJarLocalPath.toString,
      s"$remoteBuilderUser@$remoteBuilderIp:$fatJarRemotePath"
    )

    copyToRemoteCommand.!!

    val buildGraaledImageRemoteCommandParts = Seq(
      graalVmNativeImageCommand,
      "-jar", fatJarRemotePath.toString,
      "-o", resultArtifactRemotePath.toString,
      ) ++ nativeImageExtraSettings

    val buildGraaledImageCommand = Seq(
      "ssh",
      s"$remoteBuilderUser@$remoteBuilderIp",
      "-t",
      buildGraaledImageRemoteCommandParts.mkString(" ")
    )

    buildGraaledImageCommand.!!

    resultArtifactLocalPath.toFile.getParentFile().mkdirs()

    val copyFromRemoteCommand = Seq(
      "scp",
      s"$remoteBuilderUser@$remoteBuilderIp:$resultArtifactRemotePath",
      resultArtifactLocalPath.toString
    )

    copyFromRemoteCommand.!!

    // TODO clean up on remote
  }
}