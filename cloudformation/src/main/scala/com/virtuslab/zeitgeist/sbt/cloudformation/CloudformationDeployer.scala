package com.virtuslab.zeitgeist.sbt.cloudformation

import java.io.File

import com.virtuslab.zeitgeist.sbt.Region
import sbt.util.Logger

import scala.io.Source
import scala.util.{Failure, Try}

class CloudformationDeployer(region: Region, stackDir: File, stackExecution: StackExec) {
  private val cloudformation = new AwsCloudFormation(region)

  def verifyStacks(implicit log: Logger): Unit = {
    log.info("Verify stacks started...")

    val stackExecTraversal = new StackTraversal

    stackExecTraversal.traverse(stackExecution) { stack =>

      for {
        stackDescriptor <- stackInput(stack)
        validationResult <- doValidate(stackDescriptor)
      } yield {
        validationResult
      }

    }.get
  }

  def deployStacks(implicit log: Logger): Unit = {
    log.info("Deploy stacks started...")

    val stackExecTraversal = new StackTraversal

    stackExecTraversal.traverse(stackExecution) { stack =>

      for {
        stackDescriptor <- stackInput(stack)
        deployResuls <- doDeploy(stackDescriptor)
      } yield {
        deployResuls
      }

    }.get
  }

  private def doDeploy(stackDescriptor: StackInput)(implicit log: Logger) = {
    val validationResults = cloudformation.deployStack(
      stackDescriptor.name,
      stackDescriptor.content,
      stackDescriptor.params
    )

    validationResults.toEither match {
      case Right(_) =>
        log.info(s"Stack: ${stackDescriptor.file.getName} has been validated successfully")

      case Left(ex) =>
        log.error(s"Stack: ${stackDescriptor.file.getName} has been verified as INVALID!")
        ex.printStackTrace()
    }

    validationResults
  }

  private def doValidate(stackDescriptor: StackInput)(implicit log: Logger) = {
    val validationResults = cloudformation.validateStack(stackDescriptor.content)
    validationResults.toEither match {
      case Right(_) =>
        log.info(s"Stack: ${stackDescriptor.file.getName} has been validated successfully")

      case Left(ex) =>
        log.error(s"Stack: ${stackDescriptor.file.getName} has been verified as INVALID!")
        ex.printStackTrace()
    }

    validationResults.map { _ =>
      StackResults()
    }
  }

  def rollbackStacks(implicit log: Logger): Unit = {
    log.info("Rollback stacks started... NOT IMPLEMENTED YET")
  }

  private def stackInput(stack: StackDescriptor): Try[StackInput] = {
    if(stackDir.exists()) {
      Try {
        val stackFile = new File(stackDir, stack.path)

        val stackDescriptor = StackInput(
          name = stack.name,
          file = stackFile,
          content = Source.fromFile(stackFile).mkString,
          stack.params
        )
        stackDescriptor
      }
    } else {
      Failure(new IllegalArgumentException(s"Directory ${stackDir.getName} does not exist... verification is skipped"))
    }
  }

  case class StackInput(name: String, file: File, content: String, params: Map[String, String] = Map())
}
