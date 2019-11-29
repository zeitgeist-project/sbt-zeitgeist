package com.virtuslab.zeitgeist.sbt

import scala.util.{Failure, Success}

import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AWSSecurityTokenServiceException, GetCallerIdentityResult}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}

class AwsSTSSpec extends WordSpec with MustMatchers with MockFactory with SbtTest {

  "Getting account" should {
    "read caller account details" in {
      val accountId = "1234-abcd"
      val accountArn = "arn:aws:iam::1234-abcd:user/Bob"

      val awsStsClient = new AwsSTS(Region("region")) {
        override protected def buildSTSClient: AWSSecurityTokenService = {
          val client = stub[AWSSecurityTokenService]
          val result = new GetCallerIdentityResult()
            .withAccount(accountId)
            .withArn(accountArn)
            .withUserId("userId")

          (client.getCallerIdentity _).when(*).returning(result)

          client
        }
      }

      awsStsClient.getAccount() mustBe Success(
        AwsAccount(accountId, accountArn)
      )
    }

    "fail if AWS client throws exception" in {
      val exception = new AWSSecurityTokenServiceException("n/a")

      val awsStsClient = new AwsSTS(Region("region")) {
        override protected def buildSTSClient: AWSSecurityTokenService = {
          val client = stub[AWSSecurityTokenService]
          (client.getCallerIdentity _).when(*).throwing(exception)
          client
        }
      }

      awsStsClient.getAccount() mustBe Failure(exception)
    }
  }
}
