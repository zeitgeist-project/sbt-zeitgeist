package com.virtuslab.zeitgeist.sbt.s3

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.{Files, Paths}
import java.security.{DigestInputStream, MessageDigest}
import java.util.Base64
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

import com.amazonaws.event.{ProgressEvent, ProgressEventType, SyncProgressListener}
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonServiceException, AmazonWebServiceRequest}
import com.virtuslab.zeitgeist.sbt.{AwsClientSupport, Region, S3BucketId, S3Key}
import sbt.Logger

object AWSS3 {
  val NoSuchBucketCode = "NoSuchBucket"
  val HashMetadata = "com.zeitgeist.hash"

  def calculateFileMarkers(jar: File): FileMarkers = {
    val localSize = jar.length()
    val md = MessageDigest.getInstance("MD5")

    val is = Files.newInputStream(Paths.get(jar.getAbsolutePath))
    val dis = new DigestInputStream(is, md)

    try {
      Stream.continually(dis.read()).takeWhile { _ =>
        dis.available() > 0
      }
    } finally {
      dis.close()
      is.close()
    }

    val localHash = Base64.getEncoder.encodeToString(md.digest)
    FileMarkers(localSize, localHash)
  }
}

private[s3] case class FileMarkers(
  size: Long,
  hash: String
)

private[sbt] class AWSS3(val region: Region) extends AwsClientSupport {
  private lazy val client = buildClient

  protected[sbt] def buildClient: AmazonS3 = setupClient {
    AmazonS3ClientBuilder.standard()
  }

  def pushJarToS3(jar: File, bucketId: S3BucketId, s3Key: String, autoCreate: Boolean)
    (implicit log: Logger): Try[S3Key] = {
    val fileMarkers = AWSS3.calculateFileMarkers(jar)
    for {
      _ <- checkBucket(bucketId, autoCreate)
      s3Key <- pushLambdaWithChecking(jar, fileMarkers, bucketId, s3Key)
    } yield {
      s3Key
    }
  }

  private def isLocalFileSameAsS3(jar: File, fileMarkers: FileMarkers, bucket: String, key: String)
    (implicit log: Logger): Try[Boolean] = Try {
    val objectSummary = client.listObjects(bucket, key).getObjectSummaries.asScala.toList

    objectSummary match {
      case Nil =>
        log.debug(s"File $key doesn't exist in $bucket")
        false
      case head :: _ =>
        val s3Size = head.getSize
        val s3Hash =
          Try {
            val metadata = client.getObjectMetadata(bucket, key)
            metadata.getUserMetaDataOf(AWSS3.HashMetadata)
          }.recover {
            case NonFatal(exception) =>
              log.warn(s"Failed to get hash from S3: $exception")
              ""
          }.get

        log.debug(
          s"Calculated JAR markers: " +
            s"localSize: ${fileMarkers.size} vs s3Size: ${s3Size} " +
            s"localHash: ${fileMarkers.hash} vs s3Hash: ${s3Hash}"
        )

        fileMarkers.size == s3Size && fileMarkers.hash == s3Hash
    }
  }

  private def pushLambdaWithChecking(jar: File, fileMarkers: FileMarkers, bucketId: S3BucketId, key: String)
    (implicit log: Logger): Try[S3Key] = {
    isLocalFileSameAsS3(jar, fileMarkers, bucketId.value, key).flatMap { isTheSame =>
      if (isTheSame) {
        log.info("Jar file upload skipped...")
        Try(S3Key(key))
      } else {
        log.info("Jar file is to be pushed to S3...")
        pushLambdaJarToBucket(jar, fileMarkers, bucketId, key)
      }
    }
  }

  private def pushLambdaJarToBucket(jar: File, fileMarkers: FileMarkers, bucketId: S3BucketId, key: String)
    (implicit log: Logger): Try[S3Key] = Try {
    val metadata = new ObjectMetadata()
    metadata.setUserMetadata(Map(AWSS3.HashMetadata -> fileMarkers.hash).asJava)
    metadata.setContentLength(jar.length())

    val fileStream = new FileInputStream(jar)

    try {
      val buffStream = new BufferedInputStream(fileStream)
      val objectRequest = new PutObjectRequest(bucketId.value, key, buffStream, metadata)
      objectRequest.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
      addProgressListener(objectRequest, jar.length(), key)
      client.putObject(objectRequest)
    } finally {
      fileStream.close()
    }

    S3Key(key)
  }

  protected[sbt] def checkBucket(bucketId: S3BucketId, autoCreate: Boolean)(implicit log: Logger): Try[Unit] = {
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
  private def progressBar(percent: Int) = {
    val b = "=================================================="
    val s = "                                                  "
    val p = percent / 2
    val z: StringBuilder = new StringBuilder(80)
    z.append("\r[")
    z.append(b.substring(0, p))
    if (p < 50) {
      z.append("=>"); z.append(s.substring(p))
    }
    z.append("]   ")
    if (p < 5) z.append(" ")
    if (p < 50) z.append(" ")
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
}


