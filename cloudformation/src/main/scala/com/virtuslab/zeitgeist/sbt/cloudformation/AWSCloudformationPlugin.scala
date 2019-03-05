package com.virtuslab.zeitgeist.sbt.cloudformation

import com.virtuslab.zeitgeist.sbt._
import sbt.Keys._
import sbt._


object AWSCloudformationPlugin extends AutoPlugin {

  object autoImport {

    val verifyStacks = taskKey[Unit](
      "Verify correctness of Cloudformation stacks"
    )

    val deployStacks = taskKey[Unit](
      "Attempts to deploy CloudFormation stacks"
    )

    val rollbackStacks = taskKey[Unit](
      "Attempts to rollback CloudFormation stacks"
    )

    val cloudformationRegion =
      settingKey[String]("Required. Name of the AWS cloudformationRegion to setup the Lambda function in.")

    val cloudformationDir = settingKey[String](
      "Directory containing Cloudformation definitions"
    )

    val cloudFormationStackMap = taskKey[StackExec](
      "Description of stack executions"
    )
  }


  import autoImport._

  override lazy val projectSettings = Seq(
    verifyStacks := cloudformation(
      cloudformationRegion.value, cloudformationDir.?.value, cloudFormationStackMap.value
    ).verifyStacks(streams.value.log),

    deployStacks := cloudformation(
      cloudformationRegion.value, cloudformationDir.?.value, cloudFormationStackMap.value
    ).deployStacks(streams.value.log),

    rollbackStacks := cloudformation(
      cloudformationRegion.value, cloudformationDir.?.value, cloudFormationStackMap.value
    ).rollbackStacks(streams.value.log),
  )

  private def cloudformation(region: String, dir: Option[String], stackSteps: StackExec): CloudformationDeployer =
    new CloudformationDeployer(
      Region(region), getCloudformationDir(dir), stackSteps
    )

  private def getCloudformationDir(cloudformationDir: Option[String]): File = {
    cloudformationDir
      .map { dir => new File(dir) }
      .getOrElse(new File("cloudformation"))
  }
}
