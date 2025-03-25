ThisBuild / scalaVersion := "3.6.4"
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

lazy val imageProcessor = project.in(file("image-processor"))
  // .awsJsLambda(
  //   handlerClass = "yaga.example.ImageProcessorLambda",
  // )
  .awsJvmLambda()
  .settings(
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "s3" % "2.26.9",
      "software.amazon.awssdk" % "s3control" % "2.26.9",
      // "com.sksamuel.scrimage" % "scrimage-core" % "4.3.0"
      "com.sksamuel.scrimage" % "scrimage-scala_2.13" % "4.3.0"
    )
  )

lazy val fileAddedHandler = project.in(file("s3-file-added"))
  // .awsJsLambda(handlerClass = "yaga.example.FileAddedHandlerLambda")
  .awsJvmLambda()
  .withYagaDependencies(
    imageProcessor.awsLambdaModel(),
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