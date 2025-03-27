package yaga.example

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import software.amazon.awssdk.core.sync.ResponseTransformer;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;

import software.amazon.awssdk.core.sync.RequestBody;

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import com.sksamuel.scrimage.nio.JpegWriter


import yaga.extensions.aws.lambda.LambdaHandler
import besom.json.*

case class CreateThumbnail(
  bucketName: String,
  objectKey: String,
  width: Int,
  height: Int
) derives JsonFormat

class ImageProcessorLambda extends LambdaHandler[Unit, CreateThumbnail, Unit]:
  val s3Client = S3Client.builder().build()

  override def handleInput(event: CreateThumbnail) =
    makeThumbnail(
      bucketName = event.bucketName,
      objectKey = event.objectKey,
      width = event.width,
      height = event.height
    )

  def makeThumbnail(
    bucketName: String,
    objectKey: String,
    width: Int,
    height: Int
  ): Unit =
    println(s"Creating thumbnail; Bucket: $bucketName, Object Key: $objectKey, Width: $width, Height: $height")

    val getObjectRequest = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(objectKey)
      .build()

    val getObjectResponse = s3Client.getObject(getObjectRequest, ResponseTransformer.toBytes())

    // Get the bytes from the response
    val byteBuffer = getObjectResponse.asByteBuffer()
    val imageBytes = new Array[Byte](byteBuffer.remaining())
    byteBuffer.get(imageBytes)

    val transformedImageBytes = ImmutableImage.loader().fromBytes(imageBytes).scaleTo(width, height, ScaleMethod.FastScale).bytes(JpegWriter.Default)

    val newKey = s"${objectKey.stripSuffix(".png")}_${width}x${height}.jpg"

    val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(newKey)
            .build();

    s3Client.putObject(putObjectRequest, RequestBody.fromBytes(transformedImageBytes));

    System.out.println("Object successfully copied to the new key: " + newKey);

