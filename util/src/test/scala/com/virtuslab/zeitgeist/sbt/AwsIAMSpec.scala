package com.virtuslab.zeitgeist.sbt

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.{GetRoleResult, Role => AwsRole}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}

import scala.util.Success

class AwsIAMSpec extends WordSpec with MustMatchers with MockFactory with SbtTest {

  "Creating new role" should {
    "must fail if policy already exists" in {
      val roleName = "myRoleName"
      val roleArn = "roleArn"

      val awsIamClient = new AwsIAM(Region("region")) {
        override protected def buildIAMClient: AmazonIdentityManagement = {
          val m = mock[AmazonIdentityManagement]
          val awsRole = new AwsRole().withRoleName(roleName).withArn(roleArn)
          val result = new GetRoleResult().withRole(awsRole)

          (m.getRole _).expects(*).returning(result)
          m
        }
      }

      val result = awsIamClient.getOrCreateRole(RoleName(roleName), autoCreate = true)
      result must be(Success(
        Role(RoleName(roleName), RoleArn(roleArn)))
      )
    }
  }
}
