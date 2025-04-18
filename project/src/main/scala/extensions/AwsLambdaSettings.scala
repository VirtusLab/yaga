import sbt._
import sbt.Keys._

object AwsLambdaSettings {
  val sdkSharedSettings = CommonSettings.sdkModuleSettings ++ Seq(
    name := "yaga-aws-lambda-sdk"
  )

  val sdkJvmSettings = sdkSharedSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
      // Adding a version of software.amazon.awssdk:lambda newer than 2.26.9 (at least until 2.28.26) to the classpath magically causes besom.internal.ResourceDecoder.resolve to crash at runtime for besom 0.4.0
      "software.amazon.awssdk" % "lambda" % "2.26.9",
    )
  )

  val sdkJsSettings = sdkSharedSettings ++ Seq(

  )

  val besomSettings = CommonSettings.besomModuleSettings ++ Seq(
    name := "yaga-aws-lambda-besom",
    libraryDependencies ++= Seq(
      CommonSettings.besomCoreDependency,
      CommonSettings.besomProviderDependency("aws", "6.72.0"),
      classGraphDep,
    )
  )

  val codegenSettings = CommonSettings.codegenModuleSettings ++ Seq(
    name := "yaga-aws-lambda-codegen",
    libraryDependencies ++= Seq(
      classGraphDep,
    )
  )

  val compilerPluginSettings = CommonSettings.compilerPluginModuleSettings ++ Seq(
    name := "yaga-aws-lambda-compiler-plugin"
  )

  val sbtPluginSettings = CommonSettings.sbtPluginModuleSettings ++ Seq(
    name := "sbt-yaga-aws-lambda",
    libraryDependencies ++= Seq(

    ),
    addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.0"),
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.18.2"),
    addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
  )

  val classGraphDep = "io.github.classgraph" % "classgraph" % "4.8.179"
}