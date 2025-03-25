package yaga.example.eventnotifications

import besom.json.*

case class S3EventNotification(
  Records: List[S3EventNotificationRecord]
) derives JsonFormat

case class S3EventNotificationRecord(
  s3: S3
) derives JsonFormat

case class S3(
  bucket: S3Bucket,
  `object`: S3Object
) derives JsonFormat

case class S3Bucket(
  name: String,
  arn: String
) derives JsonFormat

case class S3Object(
  key: String
) derives JsonFormat