package com.virtuslab.zeitgeist.sbt.cloudformation

import java.io.File

import com.virtuslab.zeitgeist.sbt.Region
import sbt.util.Logger

import scala.io.Source

class CloudformationDeployer(region: Region, stackDir: File, stackMap: Seq[Stack]) {
  private val cloudformation = new AwsCloudFormation(region)

  def verifyStacks(implicit log: Logger): Unit = {
    log.info("Verify stacks started...")

    traverseStacks { stackDescriptor =>
        val validationResults = cloudformation.validateStack(stackDescriptor.content).toEither
        validationResults match {
          case Right(_) =>
            log.info(s"Stack: ${stackDescriptor.file.getName} has been validated successfully")

          case Left(ex) =>
            log.error(s"Stack: ${stackDescriptor.file.getName} has been verified as INVALID!")
            ex.printStackTrace()
        }
      }
  }

  def deployStacks(implicit log: Logger): Unit = {
    log.info("Deploy stacks started...")

    traverseStacks { stackDescriptor =>
      val validationResults = cloudformation.createOrUpdateStack(
        stackDescriptor.name,
        stackDescriptor.content,
        stackDescriptor.params
      ).toEither

      validationResults match {
        case Right(_) =>
          log.info(s"Stack: ${stackDescriptor.file.getName} has been validated successfully")

        case Left(ex) =>
          log.error(s"Stack: ${stackDescriptor.file.getName} has been verified as INVALID!")
          ex.printStackTrace()
      }
    }
  }

  def rollbackStacks(implicit log: Logger): Unit = {
    log.info("Rollback stacks started... NOT IMPLEMENTED YET")
  }

  private def traverseStacks(logic: StackInput => Unit)(implicit log: Logger): Unit = {
    if(stackDir.exists()) {
      stackMap.map { stack =>
        val stackFile = new File(stackDir, stack.path)

        val input = StackInput(
          name = stack.name,
          file = stackFile,
          content = Source.fromFile(stackFile).mkString,
          stack.params
        )

        logic(input)
      }
    } else {
      log.info(s"Directory ${stackDir.getName} does not exist... verification is skipped")
    }
  }

  case class StackInput(name: String, file: File, content: String, params: Map[String, String] = Map())
}
