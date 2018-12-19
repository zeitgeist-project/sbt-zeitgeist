package com.virtuslab.zeitgeist.sbt.lambda

import java.net.InetAddress

import com.virtuslab.zeitgeist.sbt.{AwsIam, AwsUser, S3BucketId}
import sbt.util.Logger

import scala.util.{Failure, Success, Try}

import S3BucketResolver._
case class UserNameNotAvailable() extends Exception(
  s"Username not returned by AWS SDK. Consider using: ${fullPlaceHolder(PlaceHolderUserId)} instead of" +
  s"${fullPlaceHolder(PlaceHolderUserName)}"
)

object S3BucketResolver {
  private[lambda] val PlaceHolderUserName = "username"
  private[lambda] val PlaceHolderUserId = "userid"
  private[lambda] val PlaceHolderHostname = "hostname"

  private[lambda] def fullPlaceHolder(id: String) = s"{${id}}"
}

/**
  * Resolves placeholders within bucket name.
  */
class S3BucketResolver(awsIam: AwsIam) {
  def resolveBucketName(bucketId: S3BucketId)(implicit log: Logger): Try[S3BucketId] = {
    val bucketName = bucketId.value

    val tryUser = if(containsAwsPlaceholder(bucketName)) {
      awsIam.getCurrentAwsUser.flatMap { user =>
        if(containsUserName(bucketName) && user.userName.isEmpty) {
          Failure(new UserNameNotAvailable)
        } else {
          Success(user)
        }
      }
    } else {
      Success(AwsUser(userId = "", userName = Option("")))
    }

    val localhost = InetAddress.getLocalHost.getHostName

    for {
      AwsUser(userId, userName) <- tryUser
    } yield {
      S3BucketId(
        bucketName
          .replaceAll(s"(?i)\\${fullPlaceHolder(PlaceHolderUserId)}", userId)
          .replaceAll(s"(?i)\\${fullPlaceHolder(PlaceHolderUserName)}", userName.getOrElse(""))
          .replaceAll(s"(?i)\\${fullPlaceHolder(PlaceHolderHostname)}", localhost)
          .toLowerCase
      )
    }
  }

  private def containsAwsPlaceholder(bucketName: String) = {
    containsUserName(bucketName) || containsUserId(bucketName)
  }

  private def containsUserId(bucketName: String) = {
    bucketName.toLowerCase.contains(fullPlaceHolder(PlaceHolderUserId))
  }

  private def containsUserName(bucketName: String) = {
    bucketName.toLowerCase.contains(fullPlaceHolder(PlaceHolderUserName))
  }
}
