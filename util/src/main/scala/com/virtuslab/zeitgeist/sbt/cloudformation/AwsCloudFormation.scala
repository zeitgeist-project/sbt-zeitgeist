package com.virtuslab.zeitgeist.sbt.cloudformation

import java.util.Date

import com.virtuslab.zeitgeist.sbt._
import com.amazonaws.services.cloudformation._
import com.amazonaws.services.cloudformation.model.StackStatus._
import com.amazonaws.services.cloudformation.model._
import sbt.Logger

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

private [cloudformation] object AwsCloudFormation {
  val ErrorValidation = "ValidationError"

  val ErrorMsgNoUpdates = "No updates are to be performed."

  val StackAwsType = "AWS::CloudFormation::Stack"
}

private[sbt] class AwsCloudFormation(region: Region) {
  import AwsCloudFormation._

  lazy val client: AmazonCloudFormation = buildClient

  def deployStack(stackName: String, templateBody: String, params: Map[String, String])
                 (implicit log: Logger): Try[StackDeployResult] = {
    log.debug(s"Deploying stack: ${stackName} with parameters: ${params}...")

    (for {
      _ <- validateStack(templateBody)
      maybeExistingStack <- describeStack(stackName)
      stackId <- createOrUpdateStack(stackName, templateBody, params, maybeExistingStack)
      stackResult <- fetchStackOutputs(stackName)
    } yield {
      log.info(s"Stack ${stackId.value} successfully deployed...".stripMargin)
      stackResult
    }).recoverWith {
      case e: AmazonCloudFormationException if e.getErrorCode == ErrorValidation =>
        log.error(
          s"Template for creating stack is invalid: ${stackName}\n" +
          s"Error: ${e.getErrorMessage}"
        )
        Failure(e)
      case e =>
        Failure(e)
    }
  }

  private def validateStack(templateBody: String)(implicit log: Logger): Try[Unit] = Try {
    val req = new ValidateTemplateRequest
    req.setTemplateBody(templateBody)
    client.validateTemplate(req)
  }

  private def createOrUpdateStack(stackName: String, templateBody: String, params: Map[String, String],
                                  maybeExistingStack: Option[Stack])
                                 (implicit log: Logger): Try[StackArn] = maybeExistingStack match {
    case Some(stack) =>
      updateStack(stack, templateBody, params)
    case None =>
      createStack(stackName, templateBody, params)
  }

  private def describeStack(stackName: String)(implicit log: Logger): Try[Option[Stack]] = Try {
    doDescribeStack(stackName)
  }.recoverWith {
    case e: AmazonCloudFormationException if e.getErrorCode == ErrorValidation =>
      log.info(s"Given stack does not exist on AWS: ${stackName}, it will be created...")
      Success(None)
    case e =>
      Failure(e)
  }

  private def doDescribeStack(stackName: String)(implicit log: Logger): Option[Stack] = {
    val req = new DescribeStacksRequest()
      .withStackName(stackName)

    val result = client.describeStacks(req)
    result.getStacks.asScala.headOption
  }

  private def updateStack(stack: Stack, templateBody: String, params: Map[String, String])
                         (implicit log: Logger): Try[StackArn] = {
    Try {
      val stackStatus = StackStatus.fromValue(stack.getStackStatus)
      if(!checkStatusAllowedToUpdate(stackStatus)) {
        throw new IllegalStateException(s"Unable to update stack: ${stack.getStackName} due to its status: ${stackStatus}")
      }

      val stackArn = doUpdateStack(stack, templateBody, params)
      val maybeUpdateStack = doDescribeStack(stack.getStackName)
      val maybeLastUpdate = maybeUpdateStack.flatMap { updatedStack =>
        Option(updatedStack.getLastUpdatedTime)
          .orElse(Option(updatedStack.getCreationTime))
      }

      statusWatch(stack.getStackName, maybeLastUpdate)
      stackArn
    }.recoverWith {
      case e: AmazonCloudFormationException if noChangesError(e) =>
        log.info(s"There are no relevant changes in stack template. Update completed.")
        Success(StackArn(stack.getStackId))
      case e =>
        Failure(e)
    }
  }

  private def doUpdateStack(stack: Stack, templateBody: String, params: Map[String, String])
                           (implicit log: Logger): StackArn = {
    val req = new UpdateStackRequest()
      .withStackName(stack.getStackName)
      .withTemplateBody(templateBody)
      .withParameters(constructParams(params): _*)
    val result = client.updateStack(req)
    StackArn(result.getStackId)
  }

  private def checkStatusAllowedToUpdate(status: StackStatus)(implicit log: Logger) = status match {
    case CREATE_COMPLETE | DELETE_COMPLETE | ROLLBACK_COMPLETE | UPDATE_COMPLETE | UPDATE_ROLLBACK_COMPLETE =>
      log.debug(s"Existing stack with status: ${status}. Update will proceed.")
      true
    case other =>
      log.error(s"Existing stack with status: ${status}. Update cannot proceed - please investigate an issue first.")
      false
  }

  private def createStack(stackName: String, templateBody: String, params: Map[String, String])
                         (implicit log: Logger): Try[StackArn] = Try {
    val req = new CreateStackRequest()
      .withTemplateBody(templateBody)
      .withStackName(stackName)
      .withOnFailure(OnFailure.ROLLBACK)
      .withParameters(constructParams(params): _*)

    val result = client.createStack(req)
    statusWatch(stackName)

    StackArn(result.getStackId)
  }

  private def statusWatch(stackName: String, maybeLastChange: Option[Date] = None)(implicit log: Logger): Unit = {
    val statusAttemptBarrier = waitTillProcessFinished(stackName)
    printDetailedStackEvents(stackName, maybeLastChange)
    statusAttemptBarrier.get
  }

  private def fetchStackEvents(stackName: String)(implicit log: Logger): Seq[StackEvent] = {
    val req = new DescribeStackEventsRequest()
      .withStackName(stackName)

    val result = client.describeStackEvents(req)
    result.getStackEvents.asScala
  }

  private def waitTillProcessFinished(stackName: String)(implicit log: Logger): Try[Unit] = Try {
    val statusStream = stackStatusStream(stackName)
    statusStream.takeWhile {
        case Some(status) if !status.endsWith("_PROGRESS") && status.contains("ROLLBACK") =>
          log.error(s"Stack sync finished with error: ${status}")
          throw new IllegalStateException(s"Stack deploy finished with error: ${status}")

        case Some(status) if !status.endsWith("_PROGRESS") =>
          log.info(s"Stack sync finished with status: ${status}")
          false

        case None =>
          false

        case Some(_) =>
          true
      }
      .foreach { maybeStatus =>
        log.info(s"Progress: ${maybeStatus.getOrElse("UNKNOWN")}...")
        Thread.sleep(2500)
      }
  }

  private def printDetailedStackEvents(stackName: String, maybeNewerThan: Option[Date] = None)
                                      (implicit log: Logger): Unit = {
    log.info(s"Detailed log of events during update of stack: ${stackName}")

    fetchStackEvents(stackName)
      .takeWhile { event =>
        maybeNewerThan.isEmpty || maybeNewerThan.exists(_.before(event.getTimestamp))
      }
      .reverse
      .foreach { event =>
        val maybeReason = Option(event.getResourceStatusReason)
        val reasonString = maybeReason.map(r => s" (${r})").getOrElse("")
        log.info(s"\t${event.getResourceType}: ${event.getResourceStatus}${reasonString}...")
      }
  }

  private def stackStatusStream(stackName: String)(implicit log: Logger): Stream[Option[String]] = {
    val stack = doDescribeStack(stackName)
    Stream.cons(stack.map(_.getStackStatus), stackStatusStream(stackName))
  }

  private def fetchStackOutputs(stackName: String)(implicit log: Logger): Try[StackDeployResult] = Try {
    val maybeStack = doDescribeStack(stackName)
    maybeStack.map { stack =>
      StackDeployResult(
        StackArn(stack.getStackId),
        stack.getOutputs.asScala.map(StackOutput.apply)
      )
    }.getOrElse {
      throw new IllegalStateException(s"Stack should have been created at this point.")
    }
  }

  private def noChangesError(e: AmazonCloudFormationException) = {
    e.getErrorCode == ErrorValidation && e.getErrorMessage == ErrorMsgNoUpdates
  }

  protected def buildClient = {
    val builder = AmazonCloudFormationClientBuilder
      .standard()
      .withCredentials(AwsCredentials.provider)

    builder.build()
  }

  private def constructParams(paramsMap: Map[String, String]): Seq[Parameter] = {
    paramsMap.toSeq.map {
      case (key, value) =>
        new Parameter()
          .withParameterKey(key)
          .withParameterValue(value)
    }
  }
}
