package com.virtuslab.zeitgeist.sbt.lambda

import com.virtuslab.zeitgeist.sbt._
import com.virtuslab.zeitgeist.sbt.s3.AWSS3
import sbt.{File, Logger}

import scala.util.{Failure, Success}

class AWSLambdaDeployer {
  private[lambda] def doDeployLambda(
                                      lambdaName: String,
                                      region: String, jar: File,
                                      s3Bucket: S3BucketId, s3KeyPrefix: Option[String],
                                      lambdaHandler: String,
                                      roleName: String,
                                      timeout: Option[Int], memory: Option[Int],
                                      autoCreate: Boolean)(implicit log: Logger): (String, LambdaARN) = {

    assert(lambdaHandler.nonEmpty)
    log.info(s"Inferred lambda handler is: ${lambdaHandler}")

    val resolvedRegion = Region(region)
    val awsS3 = new AWSS3(resolvedRegion)
    val awsLambda = new AWSLambdaClient(resolvedRegion)
    val awsIam = new AwsIam(resolvedRegion)
    val resolvedLambdaName = LambdaName(lambdaName)

    val resolvedS3Key = s3KeyPrefix.getOrElse("") + jar.getName

    val resolvedTimeout = timeout.map(Timeout)
    val resolvedMemory = memory.map(Memory)

    (for {
      role <- awsIam.getOrCreateRole(RoleName(roleName), autoCreate)
      s3Key <- awsS3.pushJarToS3(jar, s3Bucket, resolvedS3Key, autoCreate)
    } yield {
      val handlerClass = HandlerName(lambdaHandler)
      deployLambdaFunction(
        awsLambda,
        LambdaParams(resolvedLambdaName, handlerClass, resolvedTimeout, resolvedMemory),
        role,
        S3Params(s3Bucket, s3Key)
      )
    }).recover {
      case ex =>
        sys.error(s"${ex.getMessage}\n${ex.getStackTrace.mkString("\n")}")
    }.get
  }

  private[lambda] def doUploadLambdaCode(
                                      lambdaName: String,
                                      region: String, jar: File,
                                      s3Bucket: S3BucketId, s3KeyPrefix: Option[String],
                                      autoCreate: Boolean)(implicit log: Logger): S3Location = {

    val resolvedRegion = Region(region)
    val awsS3 = new AWSS3(resolvedRegion)
    val awsLambda = new AWSLambdaClient(resolvedRegion)
    val resolvedLambdaName = LambdaName(lambdaName)

    val resolvedS3Key = s3KeyPrefix.getOrElse("") + jar.getName

    awsS3.pushJarToS3(jar, s3Bucket, resolvedS3Key, autoCreate).flatMap { s3Key =>
      awsLambda.uploadLambdaCodeIfExists(
        resolvedLambdaName,
        S3Params(s3Bucket, s3Key)
      ).map { _ =>
        S3Location(s3Bucket.value, resolvedS3Key)
      }
    }.recover {
      case ex =>
        sys.error(s"${ex.getMessage}\n${ex.getStackTrace.mkString("\n")}")
    }.get
  }

  private def deployLambdaFunction(awsLambda: AWSLambdaClient, params: LambdaParams, role: Role, s3Params: S3Params)
                                  (implicit log: Logger): (String, LambdaARN) = {
    awsLambda.deployLambda(params, role.arn, s3Params) match {
      case Success(Left(createFunctionCodeResult)) =>
        params.name.value -> LambdaARN(createFunctionCodeResult.getFunctionArn)
      case Success(Right(updateFunctionCodeResult)) =>
        params.name.value -> LambdaARN(updateFunctionCodeResult.getFunctionArn)
      case Failure(ex) =>
        sys.error(s"Failed to create lambda function: ${ex.getMessage}\n${ex.getStackTrace.mkString("\n")}")
    }
  }
}
