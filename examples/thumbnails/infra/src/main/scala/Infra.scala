import besom.*
import besom.json.json
import besom.api.aws.{iam, s3, lambda}
import file_added.yaga.example.FileAddedHandlerLambda
import file_added.yaga.example.TriggerConfig
import image_processor.yaga.example.ImageProcessorLambda

@main def main = Pulumi.run {
  val bucket = s3.BucketV2("my-bucket")

  val imageProcessorRole = iam.Role("imageProcessorLambdaRole", iam.RoleArgs(
    assumeRolePolicy = json"""{
      "Version": "2012-10-17",
      "Statement": [{
        "Action": "sts:AssumeRole",
        "Principal": { "Service": "lambda.amazonaws.com" },
        "Effect": "Allow"
      }]
    }""".map(_.prettyPrint),
    managedPolicyArns = List(iam.enums.ManagedPolicy.AWSLambdaBasicExecutionRole.value)
  ))

  val imageProcessorPolicy = iam.Policy("imageProcessorPolicy", iam.PolicyArgs(
    policy = json"""{
      "Version": "2012-10-17",
      "Statement": [{
        "Effect": "Allow",
        "Action": ["s3:GetObject", "s3:PutObject"],
        "Resource": [
          ${bucket.arn.map(arn => s"${arn}/*")}
        ]
      }]
    }""".map(_.prettyPrint)
  ))

  val imageProcessorPolicyAttachment = iam.RolePolicyAttachment("imageProcessorPolicyAttachment", iam.RolePolicyAttachmentArgs(
    role = imageProcessorRole.name,
    policyArn = imageProcessorPolicy.arn
  ))

  val imageProcessorLambda = ImageProcessorLambda(
    "imageProcessor",
    lambda.FunctionArgs(
      role = imageProcessorRole.arn,
      timeout = 30,
    )
  )

  val fileAddedHandlerRole = iam.Role("fileAddedHandlerLambdaRole", iam.RoleArgs(
      assumeRolePolicy = json"""{
        "Version": "2012-10-17",
        "Statement": [{
            "Effect": "Allow",
            "Principal": {
                "Service": "lambda.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }]
      }""".map(_.prettyPrint),
      managedPolicyArns = List(iam.enums.ManagedPolicy.AWSLambdaBasicExecutionRole.value)
  ))

  // Attach permissions for invoking ImageProcessor Lambda
  val fileAddedHandlerPolicy = iam.Policy("fileAddedHandlerPolicy", iam.PolicyArgs(
      name = "fileAddedHandlerPolicy",
      policy = json"""{
          "Version": "2012-10-17",
          "Statement": [
              {
                "Effect": "Allow",
                "Action": "lambda:InvokeFunction",
                "Resource": ${imageProcessorLambda.arn}
              },
              {
                "Effect": "Allow",
                "Action": "s3:GetObject",
                "Resource": ${bucket.arn.map(arn => s"${arn}/*")}
              }
          ]
      }""".map(_.prettyPrint)
  ))

  val fileAddedHandlerPolicyAttachment = iam.RolePolicyAttachment("fileAddedHandlerPolicyAttachment", iam.RolePolicyAttachmentArgs(
    role = fileAddedHandlerRole.name,
    policyArn = fileAddedHandlerPolicy.arn
  ))

  val fileAddedHandlerLambda = FileAddedHandlerLambda(
    "fileAddedHandler",
    lambda.FunctionArgs(
      role = fileAddedHandlerRole.arn,
      timeout = 30,
    ),
    config = imageProcessorLambda.lambdaHandle.map( handle =>
      TriggerConfig(
        imageProcessorLambda = handle
      )
    ),
  )

  val allowBucket = lambda.Permission("allowS3InvokeFileAddedHandler", lambda.PermissionArgs(
    action = "lambda:InvokeFunction",
    function = fileAddedHandlerLambda.arn,
    principal = "s3.amazonaws.com",
    sourceArn = bucket.arn
  ))

  val bucketNotification = s3.BucketNotification("bucketNotification",
    s3.BucketNotificationArgs(
      bucket = bucket.id,
      lambdaFunctions = List(
        s3.inputs.BucketNotificationLambdaFunctionArgs(
            lambdaFunctionArn = fileAddedHandlerLambda.arn,
            events = List("s3:ObjectCreated:*")
        )
      )
    ),
    opts(dependsOn = List(allowBucket))
  )

  Stack(
    bucket,
    imageProcessorPolicyAttachment, imageProcessorLambda,
    fileAddedHandlerPolicyAttachment, fileAddedHandlerLambda,
    bucketNotification
  )
}
