package cloud.zeitgeist.sbt.lambda

import cloud.zeitgeist.sbt._
import com.amazonaws.services.lambda.model.Runtime.Java8
import com.amazonaws.services.lambda.model._
import com.amazonaws.services.lambda.{AWSLambda, AWSLambdaClientBuilder}
import sbt._

import scala.util.{Failure, Try}

private[sbt] case class LambdaParams(name: LambdaName, handlerName: HandlerName, timeout: Option[Timeout],
                                      memory: Option[Memory])

private[sbt] case class S3Params(s3BucketId: S3BucketId, s3Key: S3Key)

private[sbt] class AWSLambdaClient(region: Region) {

  private lazy val lambdaClient = buildAwsClient

  def deployLambda(lambdaParams: LambdaParams, roleArn: RoleArn, s3Params: S3Params)(implicit log: Logger):
  Try[Either[CreateFunctionResult, UpdateFunctionCodeResult]] = {
    for {
      exists <- lambdaExist(lambdaParams.name)
    } yield {
      if (exists) {
        Right(updateExistingLambda(lambdaParams, s3Params, roleArn))
      } else {
        Left(createNewLambda(lambdaParams, roleArn, s3Params))
      }
    }
  }

  def getLambdaConfig(lambdaName: LambdaName): Try[FunctionConfiguration] = Try {
    val getRequest = new GetFunctionRequest
    getRequest.setFunctionName(lambdaName.value)
    val result = lambdaClient.getFunction(getRequest)
    result.getConfiguration
  }

  private def lambdaExist(lambdaName: LambdaName): Try[Boolean] = {
    val getConfigAttempt = getLambdaConfig(lambdaName).map { _ =>
      true
    }

    getConfigAttempt match {
      case Failure(_: ResourceNotFoundException) => Try(false)
      case _ => getConfigAttempt
    }
  }

  private def createNewLambda(lambdaParams: LambdaParams, roleArn: RoleArn, s3Params: S3Params)
                             (implicit log: Logger): CreateFunctionResult = {
    print(s"Creating new AWS Lambda function '${lambdaParams.name.value}'\n")
    val request = createNewLambdaRequest(lambdaParams, roleArn, s3Params)
    val createResult = lambdaClient.createFunction(request)

    log.info(s"Created Lambda: ${createResult.getFunctionArn}\n")
    createResult
  }

  private def createNewLambdaRequest(lambdaParams: LambdaParams, roleArn: RoleArn, s3Params: S3Params) = {
    val req = new CreateFunctionRequest()
    req.setFunctionName(lambdaParams.name.value)
    req.setHandler(lambdaParams.handlerName.value)
    req.setRole(roleArn.value)
    req.setRuntime(Java8)

    if (lambdaParams.timeout.isDefined) req.setTimeout(lambdaParams.timeout.get.value)
    if (lambdaParams.memory.isDefined) req.setMemorySize(lambdaParams.memory.get.value)

    val functionCode = createFunctionCodeParams(s3Params)

    req.setCode(functionCode)

    req
  }

  private def updateExistingLambda(lambdaParams: LambdaParams, s3Params: S3Params, roleName: RoleArn)
                                  (implicit log: Logger): UpdateFunctionCodeResult = {
    log.info(s"Updating existing AWS Lambda function '${lambdaParams.name.value}'\n")
    val updateLambdaReq = createUpdateLambdaRequest(lambdaParams, s3Params)
    val updateCodeResult = lambdaClient.updateFunctionCode(updateLambdaReq)
    log.info(s"Successfully updated function code: ${updateCodeResult.getFunctionArn}")

    val updateFunctionConfReq = new UpdateFunctionConfigurationRequest()
    updateFunctionConfReq.setFunctionName(lambdaParams.name.value)
    updateFunctionConfReq.setHandler(lambdaParams.handlerName.value)
    updateFunctionConfReq.setRole(roleName.value)
    updateFunctionConfReq.setRuntime(Java8)
    lambdaParams.timeout.foreach { t => updateFunctionConfReq.setTimeout(t.value) }
    lambdaParams.memory.foreach { m => updateFunctionConfReq.setMemorySize(m.value) }
    val updateConfResult = lambdaClient.updateFunctionConfiguration(updateFunctionConfReq)
    log.info(s"Successfully updated function configuration: ${updateConfResult.getFunctionArn}")


    log.info(s"Updated lambda ${updateCodeResult.getFunctionArn}")
    updateCodeResult
  }

  private def createUpdateLambdaRequest(lambdaParams: LambdaParams, s3Params: S3Params): UpdateFunctionCodeRequest = {
    val req = new UpdateFunctionCodeRequest()
    req.setFunctionName(lambdaParams.name.value)
    req.setS3Bucket(s3Params.s3BucketId.value)
    req.setS3Key(s3Params.s3Key.value)
    req
  }

  private def createFunctionCodeParams(s3Params: S3Params): FunctionCode = {
    val code = new FunctionCode
    code.setS3Bucket(s3Params.s3BucketId.value)
    code.setS3Key(s3Params.s3Key.value)
    code
  }

  protected def buildAwsClient: AWSLambda = {
    val clientBuilder = AWSLambdaClientBuilder
      .standard()
      .withCredentials(AwsCredentials.provider)

    clientBuilder.setRegion(region.value)
    clientBuilder.build()
  }
}
