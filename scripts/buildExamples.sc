#!/usr/bin/env -S scala-cli

//> using scala 3.6.4
//> using toolkit 0.7.0

val excludedExamples = List("aws-lambdas-graal")

val absoluteScriptPath = os.pwd / os.RelPath(scriptPath)
val examplesDir = absoluteScriptPath / os.up / os.up / "examples"
val exampleSubdirs = os.list(examplesDir).filter(os.isDir).map(_.last)

println("************************")
println(s"Building examples from directory ${examplesDir}")
println("************************")

os.list(examplesDir).foreach: path =>
  val exampleName = path.last
  if excludedExamples.contains(exampleName) then
    println("######################")
    println(s"Skipping build of example: $exampleName")
  else
    println("######################")
    println(s"Building example: $exampleName")
    os.proc("sbt", "clean; infra/compile").call(cwd = path)
    println(s"Example $exampleName built successfully")
