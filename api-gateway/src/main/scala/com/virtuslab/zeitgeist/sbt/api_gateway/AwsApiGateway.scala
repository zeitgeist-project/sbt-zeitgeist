package com.virtuslab.zeitgeist.sbt.api_gateway

import com.amazonaws.services.apigateway.{AmazonApiGateway, AmazonApiGatewayClientBuilder}
import com.virtuslab.zeitgeist.sbt.{AwsClientSupport, Region}
import sbt.Logger

private[api_gateway] class AwsApiGateway(val region: Region) extends AwsClientSupport {
  lazy val apiClient: AmazonApiGateway = buildClient

  def getStageUrl(apiId: String, stageName: String)(implicit log: Logger): String = {
    s"https://${apiId}.execute-api.${region.value}.amazonaws.com/${stageName}/"
  }

  private def buildClient = setupClient {
    AmazonApiGatewayClientBuilder.standard()
  }
}
