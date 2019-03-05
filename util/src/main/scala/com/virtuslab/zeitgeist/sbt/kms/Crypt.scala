package com.virtuslab.zeitgeist.sbt.kms

import java.nio.ByteBuffer

import com.amazonaws.services.kms.AWSKMSClientBuilder
import com.amazonaws.services.kms.model.{DecryptRequest, EncryptRequest}
import com.amazonaws.util.Base64

object Crypt {
  def en(keyId: String, value: String): String = {
    val kmsClient = AWSKMSClientBuilder.defaultClient()
    val request = new EncryptRequest()
      .withKeyId(keyId)
      .withPlaintext(ByteBuffer.wrap(value.getBytes))

    val encoded = new String(
      Base64.encode(kmsClient.encrypt(request).getCiphertextBlob.array())
    )

    encoded
  }

  private def de(value: Array[Byte]): ByteBuffer = {
    val kmsClient = AWSKMSClientBuilder.defaultClient()
    val request = new DecryptRequest()
      .withCiphertextBlob(ByteBuffer.wrap(value))

    val response = kmsClient.decrypt(request)

    response.getPlaintext
  }
}
