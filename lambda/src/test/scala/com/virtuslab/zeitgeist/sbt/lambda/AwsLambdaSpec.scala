package com.virtuslab.zeitgeist.sbt.lambda

import com.virtuslab.zeitgeist.sbt._
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.{GetFunctionRequest, ResourceNotFoundException}
import com.virtuslab.zeitgeist.sbt.SbtTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}

import scala.util.Failure

class AwsLambdaSpec extends WordSpec with MustMatchers with MockFactory with SbtTest {

  "Deploying lambda" should {
    "attempt to create new function configuration if lambda doesn't exist" in {

      val lambdaName = "name"
      class CreateOperationPerformedException extends Exception
      val expectedException = new CreateOperationPerformedException


      val awsLambdaClient = new AWSLambdaClient(Region("region")) {
        override protected def buildAwsClient = {
          val m = stub[AWSLambda]

          val getRequest = new GetFunctionRequest
          getRequest.setFunctionName(lambdaName)
          (m.getFunction _).when(getRequest).throwing(new ResourceNotFoundException("Lambda does not exist"))

          (m.createFunction _).when(*).throwing(expectedException)
          (m.updateFunctionConfiguration _).when(*).throwing(new IllegalStateException())
          (m.updateFunctionCode _).when(*).throwing(new IllegalStateException())

          m
        }
      }

      awsLambdaClient.deployLambda(
        LambdaParams(LambdaName(lambdaName), HandlerName("name"), Some(Timeout(60)), Some(Memory(128))),
        RoleArn("arn"),
        S3Params(S3BucketId("id"), S3Key("key"))
      ) must be(Failure(expectedException))
    }
  }
}
