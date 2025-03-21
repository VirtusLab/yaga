import sbt._
import sbt.Keys._
import sbt.librarymanagement.ModuleID

object CommonSettings {
  val scala3LTSVersion = "3.3.5"
  val scala3NextVersion = "3.6.4"

  val besomMinorVersion = "0.4"
  val besomVersion = "0.4.0-SNAPSHOT"
  val besomCoreDependency = "org.virtuslab" %% "besom-core" % besomVersion
  def besomProviderDependency(providerName: String, providerVersion: String): ModuleID = {
    val artifactName = s"besom-${providerName}"
    val artifactVersion = s"${providerVersion}-core.${besomMinorVersion}-SNAPSHOT"
    "org.virtuslab" %% artifactName % artifactVersion
  }

  val sdkModuleSettings = Seq(
    scalaVersion := scala3LTSVersion,
  )

  val besomModuleSettings = Seq(
    scalaVersion := scala3LTSVersion,
  )

  val compilerPluginModuleSettings = Seq(
    scalaVersion := scala3LTSVersion,
    libraryDependencies ++= Seq(
      "org.scala-lang" %% "scala3-compiler" % scala3LTSVersion
    )
  )

  val codegenModuleSettings = Seq(
    scalaVersion := scala3NextVersion,
  )

  val sbtPluginModuleSettings = Seq(
    sbtPlugin := true
  )
}