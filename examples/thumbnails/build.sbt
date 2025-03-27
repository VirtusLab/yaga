ThisBuild / scalaVersion := "3.6.4"
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

lazy val imageProcessor = project.in(file("image-processor"))
  .awsJvmLambda()
  .settings(
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "s3" % "2.26.9",
      "com.sksamuel.scrimage" % "scrimage-core" % "4.3.0"
    )
  )

lazy val fileAddedHandler = project.in(file("file-added-handler"))
  .awsJvmLambda()
  .withYagaDependencies(
    imageProcessor.awsLambdaModel()
  )
  .settings(
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "s3-event-notifications" % "2.26.9",
    )
  )

lazy val infra = project.in(file("infra"))
  .withYagaDependencies(
    imageProcessor.awsLambdaInfra(packagePrefix = "image_processor"),
    fileAddedHandler.awsLambdaInfra(packagePrefix = "file_added"),
  )