package com.virtuslab.zeitgeist.sbt

import com.amazonaws.auth._

private[sbt] object AwsCredentials {
  lazy val provider: AWSCredentialsProvider =
    new DefaultAWSCredentialsProviderChain()
}
