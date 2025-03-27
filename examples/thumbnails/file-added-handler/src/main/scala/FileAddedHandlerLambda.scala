package yaga.example

import yaga.extensions.aws.lambda.LambdaAsyncHandler
import yaga.extensions.aws.lambda.LambdaClient
import yaga.extensions.aws.lambda.LambdaHandle
import besom.json.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import yaga.example.eventnotifications.S3EventNotification

case class TriggerConfig(
  imageProcessorLambda: LambdaHandle[CreateThumbnail, Unit],
) derives JsonFormat

class FileAddedHandlerLambda extends LambdaAsyncHandler[TriggerConfig, S3EventNotification, Unit]:
  val lambdaClient = LambdaClient()

  override def handleInput(input: S3EventNotification) =
    println("Received input: " + input)

    val futures = for
      record <- input.Records
      (width, height) <- List((100, 100), (200, 200), (300, 300))
    yield
      val bucketName = record.s3.bucket.name
      val objectKey = record.s3.`object`.key

      if objectKey.endsWith(".png") then
        println(s"Processing image: $objectKey in bucket: $bucketName with dimensions: $width x $height")

        val createThumbnail = CreateThumbnail(
          bucketName = bucketName,
          objectKey = objectKey,
          width = width,
          height = height
        )

        println("Triggering image processor lambda with payload: " + createThumbnail)

        lambdaClient.triggerEvent(config.imageProcessorLambda, createThumbnail)
      else
        Future.successful(println(s"Skipping non-png file: $objectKey"))

    Future.sequence(futures).map(_ => ())
