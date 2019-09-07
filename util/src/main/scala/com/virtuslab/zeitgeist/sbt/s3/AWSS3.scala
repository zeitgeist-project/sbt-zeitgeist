package com.virtuslab.zeitgeist.sbt.s3

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.{Files, Paths}
import java.security.{DigestInputStream, MessageDigest}
import java.util.Base64

import com.amazonaws.event.{ProgressEvent, ProgressEventType, SyncProgressListener}
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.{AmazonServiceException, AmazonWebServiceRequest}
import com.virtuslab.zeitgeist.sbt.{AwsCredentials, Region, S3BucketId, S3Key}
import sbt.Logger

import scala.util.{Failure, Try}
import scala.collection.JavaConverters._

object AWSS3 {
  val NoSuchBucketCode = "NoSuchBucket"
  val HashMetadata = "com.zeitgeist.hash"

  def calculateFileMarkers(jar: File): FileMarkers = {
    val localSize = jar.length()
    val md = MessageDigest.getInstance("MD5")

    Try {
      val is = Files.newInputStream(Paths.get(jar.getAbsolutePath))
      val dis = new DigestInputStream(is, md)
      Try {
        while(dis.available() > 0) {
          dis.read
        }
      }

      Try(is.close())
      Try(dis.close())
    }

    val localHash = Base64.getEncoder.encodeToString(md.digest)
    FileMarkers(localSize, localHash)
  }
}

private[s3] case class FileMarkers(
  val size: Long,
  val hash: String
)

private[sbt] class AWSS3(region: Region) {
  private lazy val client = buildClient

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

  private def checkChecksum(jar: File, fileMarkers: FileMarkers, bucketId: S3BucketId, key: String)
                           (implicit log: Logger): Try[Boolean] = Try {

    val s3Size = client.listObjects(bucketId.value, key).getObjectSummaries.asScala.headOption.map(_.getSize).getOrElse(-1)
    val metadata = client.getObjectMetadata(bucketId.value, key)
    val s3Hash = metadata.getUserMetaDataOf(AWSS3.HashMetadata)

    log.debug(
      s"Calculated JAR markers: " +
      s"localSize: ${fileMarkers.size} vs s3Size: ${s3Size} " +
      s"localHash: ${fileMarkers.hash} vs s3Hash: ${s3Hash}"
    )

    fileMarkers.size != s3Size || fileMarkers.hash != s3Hash
  }

  private def pushLambdaWithChecking(jar: File, fileMarkers: FileMarkers, bucketId: S3BucketId, key: String)(implicit log: Logger): Try[S3Key] = {
    checkChecksum(jar, fileMarkers, bucketId, key).flatMap { needToUpload =>
      if(needToUpload) {
        log.debug("Jar file is to be pushed to S3...")
        pushLambdaJarToBucket(jar, fileMarkers, bucketId, key)
      } else {
        log.debug("Jar file upload skipped...")
        Try(S3Key(key))
      }
    }
  }

  private def pushLambdaJarToBucket(jar: File, fileMarkers: FileMarkers, bucketId: S3BucketId, key: String)
                                   (implicit log: Logger): Try[S3Key] = Try {
    val metadata = new ObjectMetadata()
    metadata.setUserMetadata(Map(AWSS3.HashMetadata -> fileMarkers.hash).asJava)

    val fileStream = new FileInputStream(jar)

    Try {
      val buffStream = new BufferedInputStream(fileStream)
      val objectRequest = new PutObjectRequest(bucketId.value, key, buffStream, metadata)
      objectRequest.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
      addProgressListener(objectRequest, jar.length(), key)
      client.putObject(objectRequest)
    }

    fileStream.close()

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

  protected[sbt] def buildClient: AmazonS3 = {
    val builder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(AwsCredentials.provider)
    builder.setRegion(region.value)
    builder.build()
  }
}


