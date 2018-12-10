package com.virtuslab.zeitgeist.sbt

import com.amazonaws.services.identitymanagement.model._
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagement, AmazonIdentityManagementClientBuilder, model}
import sbt.Logger

import scala.util.{Failure, Try}

private[sbt] class AwsIam(region: Region) {

  lazy val iamClient: AmazonIdentityManagement = buildIamClient

  def getOrCreateRole(roleName: RoleName, autoCreate: Boolean)
                     (implicit log: Logger): Try[Role] = {
    val tryRole = getRole(roleName)
    tryRole.recoverWith {
      case e: NoSuchEntityException if autoCreate =>
        log.info(s"Role ${roleName.value} was not found. It will be now created...")
        val tryRole = createRole(roleName)
        tryRole

      case e: NoSuchEntityException if !autoCreate =>
        log.error(s"Given role ${roleName.value} does not exist. Make sure it's created on AWS side")
        Failure(e)

      case e: Throwable =>
        Failure(e)
    }
  }

  protected def buildIamClient: AmazonIdentityManagement = {
    val builder = AmazonIdentityManagementClientBuilder
      .standard()
      .withCredentials(AwsCredentials.provider)

    builder.setRegion(region.value)
    builder.build()
  }

  private def getRole(roleName: RoleName)(implicit log: Logger): Try[Role] = Try {
    val request = new GetRoleRequest()
    request.setRoleName(roleName.value)

    val result = iamClient.getRole(request)
    val role = result.getRole

    log.info(s"Role ${role.getArn} has been found and will be used...")

    Role(RoleName(role.getRoleName), RoleArn(role.getArn))
  }

  private def createRole(roleName: RoleName)(implicit log: Logger): Try[Role] = for {
    role <- createBasicRole(roleName)
    policy <- createRolePolicy(roleName)
    _ <- attachPolicy(roleName, policy)
  } yield {
    role
  }

  private def createBasicRole(roleName: RoleName)(implicit log: Logger): Try[Role] = {
    val policyDocument =
      s"""{
         |"Version":"2012-10-17",
         |"Statement": [
         | {
         |   "Effect":"Allow",
         |   "Principal":{"Service":"lambda.amazonaws.com"},
         |   "Action":"sts:AssumeRole"
         | }
         |]
         |}""".stripMargin

    val req = new CreateRoleRequest
    req.setRoleName(roleName.value)
    req.setAssumeRolePolicyDocument(policyDocument)

    Try {
      val result = iamClient.createRole(req)
      val role = result.getRole
      log.info(s"Role ${role.getRoleName} has been created [ ${role.getArn} ]...")
      log.debug(s"Create assume role policy document: ${policyDocument}")

      Role(RoleName(role.getRoleName), RoleArn(role.getArn))
    }
  }

  private def createRolePolicy(roleName: RoleName)(implicit log: Logger): Try[Policy] = Try {
    val req = new CreatePolicyRequest
    req.setPolicyName(roleName.value)

    val policyDoc =
        s"""{
           |"Version":"2012-10-17",
           |"Statement": [
           | {
           |   "Action": [
           |     "logs:CreateLogGroup",
           |     "logs:CreateLogStream",
           |     "logs:PutLogEvents"
           |   ],
           |   "Effect": "Allow",
           |   "Resource": "arn:aws:logs:*:*:log-group:/aws/lambda/${roleName.value}:*"
           | }
           |]
           |}""".stripMargin

    req.setPolicyDocument(policyDoc)

    val result = iamClient.createPolicy(req)
    log.info(s"Created role policy [ ${req.getPolicyName} ]...")
    log.debug(s"Created policy document: ${policyDoc}")
    result.getPolicy
  }

  private def attachPolicy(roleName: RoleName, policy: Policy)(implicit log: Logger): Try[Unit] = Try {
    val req = new AttachRolePolicyRequest
    req.setPolicyArn(policy.getArn)
    req.setRoleName(roleName.value)

    iamClient.attachRolePolicy(req)
    log.info(s"Role policy successfully attached...")
  }
}
