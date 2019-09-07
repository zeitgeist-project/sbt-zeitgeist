package com.virtuslab.zeitgeist.sbt

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import com.virtuslab.zeitgeist.sbt.s3.AWSS3
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}
import sbt.util.Logger

import scala.collection.JavaConverters._
import scala.util.{Random, Success, Try}

class AwsS3Spec extends WordSpec with MustMatchers with MockFactory with SbtTest {

  "Pushing file to S3 with no prior upload" should {
    "correctly set S3 metadata and upload file" in {

      val testFile = randomFile
      val s3Client = generateS3Stub(testFile.length(), None, filePreviouslyExisted = false)


      val triedKey = s3Client.pushJarToS3(testFile, S3BucketId("test-bucket"), "key", true)
      triedKey.get.value must not be (empty)
    }
  }

  "Pushing file to S3 with missing hash" should {
    "correctly set S3 metadata and upload file" in {

      val testFile = randomFile
      val s3Client = generateS3Stub(testFile.length(), None)


      val triedKey = s3Client.pushJarToS3(testFile, S3BucketId("test-bucket"), "key", true)
      triedKey.get.value must not be (empty)
    }
  }

  "Pushing file with different hash or size" should {
    "cause upload" in {
      val testFile = randomFile
      val s3Client = generateS3Stub(testFile.length(), Option("bambam"))


      val triedKey = s3Client.pushJarToS3(testFile, S3BucketId("test-bucket"), "key", true)
      triedKey.get.value must not be (empty)
    }
  }

  "Pushing file with same hash and size" should {
    "avoid another file upload" in {
      val testFile = randomFile
      val fileMarkers = AWSS3.calculateFileMarkers(testFile)

      val s3Client = generateS3Stub(fileMarkers.size, Option(fileMarkers.hash), expectUpload = false)


      val triedKey = s3Client.pushJarToS3(testFile, S3BucketId("test-bucket"), "key", true)
      triedKey.get.value must not be (empty)
    }
  }

  private def generateS3Stub(fileSize: Long, maybeFileHash: Option[String], filePreviouslyExisted: Boolean = true, expectUpload: Boolean = true): AWSS3 = {
    new AWSS3(Region("region")) {
      override protected[sbt] def buildClient: AmazonS3 = {
        val m = mock[AmazonS3]


        val expectedMetadata = new ObjectMetadata()
        expectedMetadata.setUserMetadata(Map("zeitgeist.hash" -> "hash").asJava)

        if(expectUpload) {
          val result = new PutObjectResult
          (m.putObject(_: PutObjectRequest))
            .expects(*)
            .onCall { req: PutObjectRequest =>
              if (req.getMetadata != expectedMetadata)
                throw new IllegalArgumentException(s"Incorrect metadata sent: ${req.getMetadata.getUserMetadata} vs ${expectedMetadata.getUserMetadata}")

              result
            }
        }


        val objSummary = new S3ObjectSummary()
        objSummary.setKey("jar-file.jar")
        objSummary.setSize(fileSize)
        val summaries = List(objSummary)


        val listResult = new ObjectListing() {
          override def getObjectSummaries: util.List[S3ObjectSummary] = if(filePreviouslyExisted) summaries.asJava else List().asJava
        }
        (m.listObjects(_: String, _: String)).expects(*, *).returns(listResult)


        val metadata = new ObjectMetadata()
        maybeFileHash.foreach { fileHash =>
          metadata.addUserMetadata(AWSS3.HashMetadata, fileHash)
        }
        (m.getObjectMetadata(_: String, _: String)).expects(*, *).returns(metadata)

        m
      }

      override protected[sbt] def checkBucket(bucketId: S3BucketId, autoCreate: Boolean)(implicit log: Logger): Try[Unit] = {
        Success(())
      }
    }
  }

  private def randomFile = {
    val testFile = File.createTempFile("test", "jar")
    Files.write(
      Paths.get(testFile.getAbsolutePath),
      s"test-content ${Random.alphanumeric.take(Random.nextInt(100)).toString()}".getBytes(StandardCharsets.UTF_8)
    )
    testFile.deleteOnExit()
    testFile
  }
}
