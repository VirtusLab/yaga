package yaga.example

import yaga.extensions.aws.lambda.LambdaAsyncHandler
import yaga.extensions.aws.lambda.LambdaClient
import yaga.extensions.aws.lambda.LambdaHandle
import besom.json.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import yaga.example.eventnotifications.S3EventNotification

case class Config(
  imageProcessorLambda: LambdaHandle[yaga.example.ThumbnailCreationRequest, Unit],
) derives JsonFormat

class FileAddedHandlerLambda extends LambdaAsyncHandler[Config, S3EventNotification, Unit]:
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

        val payload = ThumbnailCreationRequest(
          bucketName = bucketName,
          objectKey = objectKey,
          width = width,
          height = height
        )

        println("Triggering image processor lambda with payload: " + payload)

        lambdaClient.triggerEvent(config.imageProcessorLambda, payload)
      else
        Future.successful(println(s"Skipping non-png file: $objectKey"))

    Future.sequence(futures).map(_ => ())
