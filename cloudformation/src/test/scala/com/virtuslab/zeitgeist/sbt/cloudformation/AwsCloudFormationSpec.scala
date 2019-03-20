package com.virtuslab.zeitgeist.sbt.cloudformation

import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.StackStatus._
import com.amazonaws.services.cloudformation.model._
import com.virtuslab.zeitgeist.sbt.{Region, SbtTest}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}

import scala.io.{Codec, Source}
import scala.util.Failure

class AwsCloudFormationSpec extends WordSpec with MustMatchers with MockFactory with SbtTest {
  private val cfClient = mock[AmazonCloudFormation]

  "Creating new stack" should {
    "work in simple case from template file" in {
      val validationEx = new AmazonCloudFormationException("Validation error")

      mockValidateStackFailure(validationEx)

      testDeployment must be(Failure(validationEx))
    }

    "work for real tests" ignore {
      val awsClient = new AwsCloudFormation(Region("eu-west-1"))

      val templateBody = Source.fromInputStream(
        getClass.getResourceAsStream("dummyTemplate.yml")
      )(Codec.UTF8).mkString

      val result = awsClient.deployStack("test", templateBody,
        Map(
          "ApiName" -> "Test API",
          "LambdaArn" -> "arn:aws:lambda:eu-west-1:xxxxxxxx:function:http-hello"
        )
      )
      result.get
    }
  }

  "Updating existing stack" should {
    "complete with success" in {
      mockValidateStackOK
      mockDescribeStatus(CREATE_COMPLETE)
      mockUpdateStack
      mockDescribeStatus(UPDATE_COMPLETE).repeat(3)
      mockDescribeEvents

      testDeployment must be('success)
    }

    "cleanup failed deployment and create new stack" in {
      mockValidateStackOK
      mockDescribeStatus(ROLLBACK_COMPLETE)
      mockDeleteStack
      mockDescribeStatus(DELETE_IN_PROGRESS)
      mockDescribeNotExists
      mockCreateStack
      mockDescribeStatus(CREATE_COMPLETE).repeat(2)
      mockDescribeEvents

      testDeployment must be('success)
    }
  }

  private def testDeployment =
    createAwsClient().deployStack("test-stack", "template-body", Map())

  private def mockDescribeEvents =
    (cfClient.describeStackEvents _).expects(*).returns(
      new DescribeStackEventsResult()
    )

  private def mockDeleteStack =
    (cfClient.deleteStack _).expects(*).returns(new DeleteStackResult())

  private def mockCreateStack =
    (cfClient.createStack _).expects(*).returns(
      new CreateStackResult().withStackId("some-stack-id")
    )

  private def mockUpdateStack =
    (cfClient.updateStack _).expects(*).returns(
      new UpdateStackResult().withStackId("some-stack-id")
    )

  private def mockDescribeStatus(status: StackStatus) =
    (cfClient.describeStacks(_: DescribeStacksRequest)).expects(*).returns(
      new DescribeStacksResult().withStacks(
        new Stack().withStackStatus(status)
      )
    )

  private def mockDescribeNotExists =
    (cfClient.describeStacks(_: DescribeStacksRequest)).expects(*).throws {
      val exception = new AmazonCloudFormationException("Stack doesn't exist")
      exception.setErrorCode(AwsCloudFormation.ErrorValidation)
      exception
    }

  private def mockValidateStackOK =
    (cfClient.validateTemplate _).expects(*).returns(
      new ValidateTemplateResult()
    )

  private def mockValidateStackFailure(validationEx: AmazonCloudFormationException) =
    (cfClient.validateTemplate _).expects(*).throws(validationEx)

  private def createAwsClient() =
    new AwsCloudFormation(Region("region")) {
      override protected def buildClient: AmazonCloudFormation = cfClient
    }
}
