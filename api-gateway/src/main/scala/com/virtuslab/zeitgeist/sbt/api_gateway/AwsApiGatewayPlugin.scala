package com.virtuslab.zeitgeist.sbt.api_gateway

import com.virtuslab.zeitgeist.sbt.api_gateway.HttpApiDeployer._
import com.virtuslab.zeitgeist.sbt.lambda.AWSLambdaPlugin
import com.virtuslab.zeitgeist.sbt.{LambdaName, Region}
import sbt.Keys.streams
import sbt._

object AwsApiGatewayPlugin extends AutoPlugin {
  object autoImport {
    val deployHttpApi = taskKey[String]("Generates & deploys simple HTTP api. Returns URL to deployed API.")
  }

  import autoImport._

  override def requires = AWSLambdaPlugin

  override lazy val projectSettings = Seq(
    deployHttpApi := doGenerateHttpApi(
      apiName = AWSLambdaPlugin.autoImport.lambdaName.value,
      region = AWSLambdaPlugin.autoImport.region.value,
      lambdaName = AWSLambdaPlugin.autoImport.lambdaName.value
    )(streams.value.log)
  )


  private def doGenerateHttpApi(apiName: String, region: String, lambdaName: String)(implicit log: Logger): String = {
    log.info(s"Initiating API deployment...")

    val resolvedRegion = Region(region)
    val apiGatewayClient = new AwsApiGateway(resolvedRegion)

    val gatewayDeployer = new HttpApiDeployer

    (for {
      deployResult <- gatewayDeployer.deployHttpApi(apiName, resolvedRegion, LambdaName(lambdaName))
    } yield {
      val maybeApiId = deployResult.outputs.find(_.keyName == ApiIdOutput)

      val apiIdOutput = maybeApiId.getOrElse(
        sys.error(s"Api was not retrieved after stack creation. Deployment result: ${deployResult}")
      )
      val apiUrl = apiGatewayClient.getStageUrl(apiIdOutput.keyValue, DefaultStageName)
      log.info(
        s"""========================================================================
           |>>> Your API has been deployed at:
           |>>> ${apiUrl}
           |========================================================================
         """.stripMargin)
      apiUrl
    }).get
  }
}
