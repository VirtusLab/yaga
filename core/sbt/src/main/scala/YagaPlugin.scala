package yaga.sbt

import _root_.sbt._
import _root_.sbt.Keys._
import _root_.sbt.AutoPlugin

object YagaPlugin extends AutoPlugin {
  override def trigger = allRequirements

  val yagaVersion = "0.1.0-SNAPSHOT"

  object autoImport {
    val yagaGeneratedSources = taskKey[Seq[File]]("Sources generated by yaga")

    implicit class ProjectYagaOps(project: Project) {
      def withYagaDependencies(dependencies: YagaDependency*) = {
        dependencies.foldLeft(project) { (acc, dep) =>
          dep.addSelfToProject(acc)
        }
      }
    }
  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    yagaGeneratedSources := Seq.empty,
    Compile / sourceGenerators += yagaGeneratedSources,
  )
}
