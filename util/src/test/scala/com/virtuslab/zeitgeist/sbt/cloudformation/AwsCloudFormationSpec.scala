package com.virtuslab.zeitgeist.sbt.cloudformation

import com.virtuslab.zeitgeist.sbt.Region
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException
import com.virtuslab.zeitgeist.sbt.SbtTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}

import scala.io.{Codec, Source}
import scala.util.Failure

class AwsCloudFormationSpec extends WordSpec with MustMatchers with MockFactory with SbtTest {

  "Creating new stack" should {
    "work in simple case from template file" in {
      val validationEx = new AmazonCloudFormationException("Validation error")

      val awsClient = new AwsCloudFormation(Region("region")) {
        override protected def buildClient: AmazonCloudFormation = {
          val m = mock[AmazonCloudFormation]

          (m.validateTemplate _).expects(*).throws(validationEx)
          m
        }
      }

      val result = awsClient.deployStack("test-stack", "template-body",
        Map("LambdaArn" -> "arn:aws:lambda:eu-west-1:xxxxxx:function:bare-lambda")
      )
      result must be(Failure(validationEx))
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
}
