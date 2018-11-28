package cloud.zeitgeist.sbt.s3

import java.io.File

import cloud.zeitgeist.sbt.{AwsCredentials, Region, S3BucketId, S3Key}
import com.amazonaws.event.{ProgressEvent, ProgressEventType, SyncProgressListener}
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonServiceException, AmazonWebServiceRequest}
import sbt.Logger

import scala.util.{Failure, Try}

private[s3] object AWSS3 {
  val NoSuchBucketCode = "NoSuchBucket"
}

private[sbt] class AWSS3(region: Region) {
  private lazy val client = buildClient

  def pushJarToS3(jar: File, bucketId: S3BucketId, s3KeyPrefix: String, autoCreate: Boolean)
                 (implicit log: Logger): Try[S3Key] = for {
    _ <- checkBucket(bucketId, autoCreate)
    s3Key <- pushLambdaJarToBucket (jar, bucketId, s3KeyPrefix + jar.getName)
  } yield {
    s3Key
  }

  private def pushLambdaJarToBucket(jar: File, bucketId: S3BucketId, key: String)
                                   (implicit log: Logger): Try[S3Key] = Try {
    val objectRequest = new PutObjectRequest(bucketId.value, key, jar)
    objectRequest.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
    addProgressListener(objectRequest, jar.length(), key)
    client.putObject(objectRequest)
    S3Key(key)
  }

  private def checkBucket(bucketId: S3BucketId, autoCreate: Boolean)(implicit log: Logger): Try[Unit] = {
    Try {
      client.listObjects(new ListObjectsRequest(bucketId.value, null, null, null, 0))
      log.info(s"Bucket ${bucketId.value} exists and is accessible")
    }.recoverWith {
      case e: AmazonServiceException if e.getErrorCode == AWSS3.NoSuchBucketCode =>
        handleBucketDoesNotExist(e, bucketId, autoCreate)
      case e: AmazonServiceException =>
        log.error(s"Unable to access specified bucket: ${bucketId}")
        Failure(e)
    }
  }

  private def handleBucketDoesNotExist(e: AmazonServiceException, bucketId: S3BucketId, autoCreate: Boolean)
                                      (implicit log: Logger): Try[Unit] = {
    if (autoCreate) {
      log.info(s"Bucket ${bucketId.value} doesn't exists, attempting to create it")
      Try {
        client.createBucket(bucketId.value)
      }
    } else {
      log.error(s"Bucket ${bucketId.value} doesn't exists - " +
        s"it needs be created and have appropriate privileges before lambda can be uploaded")
      Failure(e)
    }
  }


  /**
    * Progress bar code borrowed from
    * https://github.com/sbt/sbt-s3/blob/master/src/main/scala/S3Plugin.scala
    */
  private def progressBar(percent:Int) = {
    val b="=================================================="
    val s="                                                  "
    val p=percent/2
    val z:StringBuilder=new StringBuilder(80)
    z.append("\r[")
    z.append(b.substring(0,p))
    if (p<50) {z.append("=>"); z.append(s.substring(p))}
    z.append("]   ")
    if (p<5) z.append(" ")
    if (p<50) z.append(" ")
    z.append(percent)
    z.append("%   ")
    z.mkString
  }

  private def addProgressListener(request: AmazonWebServiceRequest, fileSize: Long, key: String)(implicit log: Logger) = {
    val MbSize = 1024.0 * 1024

    request.setGeneralProgressListener(new SyncProgressListener {
      var uploadedBytes = 0L
      val fileName = {
        val area = 30
        val n = new File(key).getName
        val l = n.length()
        if (l > area - 3)
          "..." + n.substring(l - area + 3)
        else
          n
      }
      override def progressChanged(progressEvent: ProgressEvent): Unit = {
        if (progressEvent.getEventType == ProgressEventType.REQUEST_BYTE_TRANSFER_EVENT ||
          progressEvent.getEventType == ProgressEventType.RESPONSE_BYTE_TRANSFER_EVENT) {
          uploadedBytes = uploadedBytes + progressEvent.getBytesTransferred
        }
        print(progressBar(if (fileSize > 0) ((uploadedBytes * 100) / fileSize).toInt else 100))
        print(f"(${uploadedBytes / MbSize}%.2f/${fileSize / MbSize}%.2f MB) ")
        print(s"Lambda JAR -> S3")
        if (progressEvent.getEventType == ProgressEventType.TRANSFER_COMPLETED_EVENT)
          println()
      }
    })
  }

  private def prettyLastMsg(verb:String, objects:Seq[String], preposition:String, bucket:String) =
    if (objects.length == 1) s"$verb '${objects.head}' $preposition the S3 bucket '$bucket'."
    else                     s"$verb ${objects.length} objects $preposition the S3 bucket '$bucket'."

  private def buildClient: AmazonS3 = {
    val builder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(AwsCredentials.provider)
    builder.setRegion(region.value)
    builder.build()
  }
}


