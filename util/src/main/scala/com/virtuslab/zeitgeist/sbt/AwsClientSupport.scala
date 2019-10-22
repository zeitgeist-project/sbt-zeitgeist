package com.virtuslab.zeitgeist.sbt

import com.amazonaws.client.builder.AwsClientBuilder

trait AwsClientSupport {
  val region: Region

  protected def setupClient[C, T <: AwsClientBuilder[_ <: AnyRef, C]](builder: AwsClientBuilder[T, C]): C = {
    builder.setCredentials(AwsCredentials.provider)
    builder.setRegion(region.value)
    builder.build()
  }

}
