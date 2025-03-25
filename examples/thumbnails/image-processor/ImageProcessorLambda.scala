package yaga.example

// import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
// import com.amazonaws.services.s3.model.S3Object
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
// import com.amazonaws.services.s3.model.PutObjectRequest
import software.amazon.awssdk.core.sync.ResponseTransformer;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;

import software.amazon.awssdk.core.sync.RequestBody;

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
// import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.nio.JpegWriter


import yaga.extensions.aws.lambda.LambdaHandler
import besom.json.*

case class ThumbnailCreationRequest(
  bucketName: String,
  objectKey: String,
  width: Int,
  height: Int
) derives JsonFormat

class ImageProcessorLambda extends LambdaHandler[Unit, ThumbnailCreationRequest, Unit]:
  // val s3: AmazonS3 = AmazonS3ClientBuilder.defaultClient()
  val s3Client = S3Client.builder()
                // .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

  override def handleInput(event: ThumbnailCreationRequest) =
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
    // Logic to create a thumbnail
    println("Creating thumbnail...")
    println(s"Bucket: $bucketName, Object Key: $objectKey, Width: $width, Height: $height")

    // Get image from S3
    // val s3Object = s3.getObject(bucketName, imageKey)

    val getObjectRequest = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(objectKey)
      .build()

    val getObjectResponse = s3Client.getObject(getObjectRequest, ResponseTransformer.toBytes())

    // Get the bytes from the response
    val byteBuffer = getObjectResponse.asByteBuffer()
    val imageBytes = new Array[Byte](byteBuffer.remaining())
    byteBuffer.get(imageBytes)

    // Create an InputStream from the byte array
    // val inputStream: ByteArrayInputStream = new ByteArrayInputStream(bytes)

    // val getObjectResponse: Int = s3Client.getObject(getObjectRequest,
    //     ResponseTransformer.toBytes());
    // val byteBuffer = getObjectResponse.asByteBuffer()
    // val bytes = new Array[Byte](byteBuffer.remaining())
    // byteBuffer.get(bytes)

    // val inputStream = s3Client.getObject(getObjectRequest);
    // val fileContent = IOUtils.toByteArray(inputStream); // Read the content of the file

    val transformedImageBytes = ImmutableImage.loader().fromBytes(imageBytes).scaleTo(width, height, ScaleMethod.FastScale).bytes(JpegWriter.Default)

    

    // val newKey = "__" + objectKey // New key for the copied object
    val newKey = s"${objectKey.stripSuffix(".png")}_${width}x${height}.jpg"

    // Step 2: Put the object back with the new key
    val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(newKey)
            .build();

    // // Upload the content back to S3 with the new key
    s3Client.putObject(putObjectRequest, RequestBody.fromBytes(transformedImageBytes));

    System.out.println("Object successfully copied to the new key: " + newKey);


    // val image = Image.fromInputStream(s3Object.getObjectContent)

    // // Resize the image
    // val resizedImage = image.scale(width, height)

    // // Save the resized image to S3 with a suffix
    // val resizedKey = s"${imageKey.split("\\.")(0)}_${width}x${height}.jpg"
    // val outputStream = new ByteArrayOutputStream()
    // resizedImage.output(JpegWriter(), outputStream)

    // val inputStream = new ByteArrayInputStream(outputStream.toByteArray)
    // val putRequest = new PutObjectRequest(bucketName, resizedKey, inputStream, null)

    // println(s"Putting object to S3: $bucketName/$resizedKey")

    // s3.putObject(putRequest)
