package cloud.zeitgeist.sbt.api_gateway

import cloud.zeitgeist.sbt.api_gateway.HttpApiDeployer.{ApiNameParam, LambdaArnParam}
import cloud.zeitgeist.sbt.lambda.AWSLambdaClient
import cloud.zeitgeist.sbt.{AwsCloudFormation, LambdaName, Region, StackDeployResult}
import com.amazonaws.services.lambda.model.{FunctionConfiguration, ResourceNotFoundException}
import sbt.{File, Logger}

import scala.io.{Codec, Source}
import scala.util.Try

private[api_gateway] object HttpApiDeployer {
  val ApiNameParam = "ApiName"
  val LambdaArnParam = "LambdaArn"

  val DefaultStageName = "dev"

  val ApiIdOutput = "ApiId"
}

private[api_gateway] class HttpApiDeployer {
  def deployHttpApi(apiName: String, region: Region, lambdaName: LambdaName)(implicit log: Logger): Try[StackDeployResult] = {
    val template = Source.fromInputStream(
      getClass.getResourceAsStream("/cloudformation/basicHttpApi.yml")
    )(Codec.UTF8).mkString

    val lambdaClient = new AWSLambdaClient(region)
    val cloudFormationClient = new AwsCloudFormation(region)

    (for {
      lambdaConfig <- lambdaClient.getLambdaConfig(lambdaName)
      deployResult <- cloudFormationClient.deployStack(apiName, template, constructParams(apiName, lambdaConfig))
    } yield {
      deployResult
    }).recover {
      case e: ResourceNotFoundException =>
        log.error(s"Lambda function ${lambdaName} not found. Make sure it is correctly deployed...")
        throw e
    }
  }

  private def constructParams(apiName: String, lambdaConfig: FunctionConfiguration) = Map(
    ApiNameParam -> apiName,
    LambdaArnParam -> lambdaConfig.getFunctionArn
  )
}
