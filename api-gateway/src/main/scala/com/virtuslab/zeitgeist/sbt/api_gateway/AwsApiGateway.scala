package com.virtuslab.zeitgeist.sbt.api_gateway

import com.virtuslab.zeitgeist.sbt.{AwsCredentials, Region}
import com.amazonaws.services.apigateway.{AmazonApiGateway, AmazonApiGatewayClientBuilder}
import sbt.Logger

private[api_gateway] class AwsApiGateway(region: Region) {
  lazy val apiClient: AmazonApiGateway = buildClient

  def getStageUrl(apiId: String, stageName: String)(implicit log: Logger): String = {
    s"https://${apiId}.execute-api.${region.value}.amazonaws.com/${stageName}/"
  }

  private def buildClient = {
    val clientBuilder = AmazonApiGatewayClientBuilder
      .standard()
      .withCredentials(AwsCredentials.provider)

    clientBuilder.setRegion(region.value)
    clientBuilder.build()
  }
}
