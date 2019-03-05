package com.virtuslab.zeitgeist.sbt

import com.amazonaws.services.cloudformation.model.Output

case class APIId(value: String) extends AnyVal
case class Region(value: String) extends AnyVal
case class S3BucketId(value: String) extends AnyVal
case class S3Key(value: String) extends AnyVal
case class S3Location(bucket: String, key: String)

case class LambdaName(value: String) extends AnyVal
case class LambdaARN(value: String) extends AnyVal
case class HandlerName(value: String) extends AnyVal

case class Role(name: RoleName, arn: RoleArn)

case class RoleName(value: String) extends AnyVal
case class RoleArn(value: String) extends AnyVal

case class Timeout(value: Int) {
  require(value > 0 && value <= 900, "Lambda timeout must be between 1 and 900 seconds")
}

case class Memory(value: Int) {
  require(value >= 128 && value <= 3008, "Lambda memory must be between 128 and 3008 MBs")
  require(value % 64 == 0)
}

