import java.nio.file.Path

ThisBuild / scalaVersion := "3.3.5"
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

lazy val childLambdaA = project.in(file("child-lambda-a"))
  .awsJvmLambda

lazy val childLambdaB = project.in(file("child-lambda-b"))
  .awsJvmLambda

lazy val parentLambda = project.in(file("parent-lambda"))
  .awsJvmLambda
  .withYagaDependencies(
    childLambdaA.awsLambdaModel(),
    childLambdaB.awsLambdaModel()
  )

lazy val infra = project.in(file("infra"))
  .withYagaDependencies(
    childLambdaA.awsLambdaInfra(packagePrefix = "child_a"),
    childLambdaB.awsLambdaInfra(packagePrefix = "child_b"),
    parentLambda.awsLambdaInfra(packagePrefix = "parent")
  )
