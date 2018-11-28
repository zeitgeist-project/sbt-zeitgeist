package cloud.zeitgeist.sbt

import com.amazonaws.services.cloudformation.model.Output

case class APIId(value: String) extends AnyVal
case class Region(value: String) extends AnyVal
case class S3BucketId(value: String) extends AnyVal
case class S3Key(value: String) extends AnyVal

case class LambdaName(value: String) extends AnyVal
case class LambdaARN(value: String) extends AnyVal
case class HandlerName(value: String) extends AnyVal

case class Role(name: RoleName, arn: RoleArn)

case class RoleName(value: String) extends AnyVal
case class RoleArn(value: String) extends AnyVal

case class StackArn(value: String) extends AnyVal

case class StackDeployResult(stackArn: StackArn, outputs: Seq[StackOutput])

object StackOutput {
  def apply(awsOutput: Output): StackOutput = StackOutput(
    awsOutput.getOutputKey,
    awsOutput.getOutputValue,
    awsOutput.getDescription
  )
}
case class StackOutput(keyName: String, keyValue: String, description: String)


case class Timeout(value: Int) {
  require(value > 0 && value <= 300, "Lambda timeout must be between 1 and 300 seconds")
}

case class Memory(value: Int) {
  require(value >= 128 && value <= 1536, "Lambda memory must be between 128 and 1536 MBs")
  require(value % 64 == 0)
}

