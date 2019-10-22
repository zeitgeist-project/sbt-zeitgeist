package com.virtuslab.zeitgeist.sbt

import scala.util.Try

import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.{AWSSecurityTokenService, AWSSecurityTokenServiceClientBuilder}

case class AwsAccount(id: String, arn: String)

private[sbt] class AwsSTS(val region: Region) extends AwsClientSupport {
  lazy val stsClient: AWSSecurityTokenService = buildSTSClient

  protected def buildSTSClient: AWSSecurityTokenService = setupClient {
    AWSSecurityTokenServiceClientBuilder.standard()
  }

  def getAccount(): Try[AwsAccount] = Try {
    val rq = new GetCallerIdentityRequest()
    val rs = stsClient.getCallerIdentity(rq)

    AwsAccount(
      id = rs.getAccount,
      arn = rs.getArn
    )
  }
}
