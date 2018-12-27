package com.virtuslab.zeitgeist.sbt.lambda

import com.virtuslab.zeitgeist.sbt._
import sbt.Keys._
import sbt._


object AWSLambdaPlugin extends AutoPlugin {

  object autoImport {
    private val metaParadiseVersion   = "3.0.0-M11"

    lazy val zeitgeistMacroSettings = Seq(
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ),
      addCompilerPlugin("org.scalameta" % "paradise" % metaParadiseVersion cross CrossVersion.full),
      scalacOptions ++= Seq(
        "-Xplugin-require:macroparadise"/*,
        "-Ymacro-debug-lite"*/
      )
    )

    val deployLambda = taskKey[(String, LambdaARN)](
      "Create or Update the AWS Lambda project, with any appropriate trigger types & metadata.")

    val uploadLambda = taskKey[S3Location](
      "Uploads Jar to S3 and attaches it Lambda if it exists")

    val s3Bucket =
      settingKey[String]("ID of an S3 Bucket to upload the deployment jar to. Defaults to organization + project name.")

    val resolvedS3Bucket =
      taskKey[S3BucketId]("Resolved name of S3 Bucket (that is it contains filled placeholders)")

    val s3KeyPrefix =
      settingKey[String]("A prefix to the S3 key to which the jar will be uploaded.")

    val lambdaName =
      settingKey[String]("Name of the AWS Lambda to update or create. Defaults to project name.")

    val role =
      settingKey[String]("Name of the IAM role with which to configure the Lambda function.")

    val region =
      settingKey[String]("Required. Name of the AWS region to setup the Lambda function in.")

    val awsLambdaTimeout =
      settingKey[Int]("In seconds, the Lambda function's timeout length (1-900).")

    val awsLambdaMemory = settingKey[Int](
      "How much memory (in MB) to allocate to execution of the Lambda function (128-3008, multiple of 64)."
    )

    val s3Jar = taskKey[sbt.File](
      "Name of jar file to be sent to S3"
    )

    val createAutomatically = settingKey[Boolean](
        "Flag indicating if AWS infrastructure should be created automatically. If yes - objects like bucket, " +
        "lambda definition, api gateway would be automatically created. Defaults to: false"
    )

    val handlerName =
      settingKey[String]("Fully qualified name of the class of Lambda resolver. Use if you want to indicate " +
        "concrete class name without using auto discovery")

    val resolvedHandler =
      taskKey[String]("Fully qualified name of the class of Lambda resolver. Calculate from compilation path" +
        "or from handlerName given by hand.")

    val discoverAWSLambdaClasses = taskKey[Seq[String]](
      "Finds a sequence of lambda annotated classes."
    )
  }


  import autoImport._

  override def requires = sbtassembly.AssemblyPlugin

  override lazy val projectSettings = Seq(
    deployLambda := new AWSLambdaDeployer().doDeployLambda(
      lambdaName = lambdaName.value,

      region = region.value,
      jar = s3Jar.value,

      s3Bucket = resolvedS3Bucket.value,
      s3KeyPrefix = s3KeyPrefix.?.value,

      lambdaHandler = resolvedHandler.value,

      roleName = role.value,
      timeout = awsLambdaTimeout.?.value,
      memory = awsLambdaMemory.?.value,

      autoCreate = createAutomatically.value
    )(streams.value.log),

    uploadLambda := new AWSLambdaDeployer().doUploadLambdaCode(
      lambdaName = lambdaName.value,

      region = region.value,
      jar = s3Jar.value,

      s3Bucket = resolvedS3Bucket.value,
      s3KeyPrefix = s3KeyPrefix.?.value,

      autoCreate = createAutomatically.value
    )(streams.value.log),

    lambdaName := sbt.Keys.name.value,
    s3Bucket := sbt.Keys.organization.value + "." + lambdaName.value,

    role := lambdaName.value,

    createAutomatically := false,

    discoverAWSLambdaClasses := LambdaClassDiscovery.perform(compile.in(Compile).value),

    s3Jar := sbtassembly.AssemblyKeys.assembly.value,

    resolvedHandler := generateLambdasHandlers.value.head,

    resolvedS3Bucket := resolveBucketName.value
  )

  private def generateLambdasHandlers = Def.task[Seq[String]]{
    handlerName.?.value match {
      case Some(handler) => Seq(handler)
      case _ =>
        val discovered = discoverAWSLambdaClasses.value.map { c => s"${c}::handleRequest" }
        if (discovered.isEmpty) sys.error("No annotated Lambda resolver classes found! Use @LambdaHTTPApi annotation.")
        else if (discovered.length > 1) sys.error(s"For now only one Lambda resolver class is supported!" +
                                             s"Found following classes with @LambdaHTTPApi annotations: ${discovered}")
        else discovered
    }
  }

  private def resolveBucketName = Def.task[S3BucketId] {
    val resolvedRegion = Region(region.value)
    val awsIam = new AwsIam(resolvedRegion)

    val bucketNameResolver = new S3BucketResolver(awsIam)
    val bucketId = S3BucketId(s3Bucket.value)
    bucketNameResolver.resolveBucketName(bucketId)(streams.value.log).get
  }
}
