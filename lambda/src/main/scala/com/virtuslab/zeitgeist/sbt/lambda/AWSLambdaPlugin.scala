package com.virtuslab.zeitgeist.sbt.lambda

import com.virtuslab.zeitgeist.sbt._
import com.virtuslab.zeitgeist.sbt.s3.AWSS3
import sbt.Keys._
import sbt._

import scala.util.{Failure, Success}


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

    val s3Bucket =
      settingKey[String]("ID of an S3 Bucket to upload the deployment jar to. Defaults to organization + project name.")

    val s3KeyPrefix =
      settingKey[String]("A prefix to the S3 key to which the jar will be uploaded.")

    val lambdaName =
      settingKey[String]("Name of the AWS Lambda to update or create. Defaults to project name.")

    val role =
      settingKey[String]("Name of the IAM role with which to configure the Lambda function.")

    val region =
      settingKey[String]("Required. Name of the AWS region to setup the Lambda function in.")

    val awsLambdaTimeout =
      settingKey[Int]("In seconds, the Lambda function's timeout length (1-300).")

    val awsLambdaMemory = settingKey[Int](
      "How much memory (in MB) to allocate to execution of the Lambda function (128-1536, multiple of 64)."
    )

    val createAutomatically = settingKey[Boolean](
        "Flag indicating if AWS infrastructure should be created automatically. If yes - objects like bucket, " +
        "lambda definition, api gateway would be automatically created. Defaults to: false"
    )

    val handlerName =
      settingKey[String]("Fully qualified name of the class of Lambda resolver. Use if you want to indicate " +
        "concrete class name without using auto discovery")

    val lambdaHandlers = settingKey[Seq[String]](
      "A sequence of fully qualified names of classes of Lambda resolvers. Use if you want to indicate " +
        "concrete class names without using auto discovery"
    )

    val discoverAWSLambdaClasses = taskKey[Seq[String]](
      "Finds a sequence of lambda annotated classes."
    )
  }


  import autoImport._

  override def requires = sbtassembly.AssemblyPlugin

  override lazy val projectSettings = Seq(
    deployLambda := doDeployLambda(
      lambdaName = lambdaName.value,

      region = region.value,
      jar = sbtassembly.AssemblyKeys.assembly.value,

      s3Bucket = s3Bucket.value,
      s3KeyPrefix = s3KeyPrefix.?.value,

      lambdaHandlers = lambdaHandlers.?.value
                        .orElse(handlerName.?.value.map(h => Seq(h)))
                        .getOrElse(generateLambdas.value),

      roleName = role.value,
      timeout = awsLambdaTimeout.?.value,
      memory = awsLambdaMemory.?.value,

      autoCreate = createAutomatically.value
    )(streams.value.log),

    lambdaName := sbt.Keys.name.value,
    s3Bucket := sbt.Keys.organization.value + "." + lambdaName.value,

    role := lambdaName.value,

    createAutomatically := false,

    discoverAWSLambdaClasses := LambdaClassDiscovery.perform(compile.in(Compile).value)
  )


  private def generateLambdas = Def.task[Seq[String]]{
    handlerName.?.value match {
      case Some(handler) => Seq(handler)
      case _ =>
        val discovered = discoverAWSLambdaClasses.value
        if (discovered.isEmpty) sys.error("No annotated Lambda resolver classes found! Use @LambdaHTTPApi annotation.")
        else if (discovered.length > 1) sys.error(s"For now only one Lambda resolver class is supported!" +
                                             s"Found following classes with @LambdaHTTPApi annotations: ${discovered}")
        else discovered
    }
  }

  private def doDeployLambda(
    lambdaName: String,
    region: String, jar: File,
    s3Bucket: String, s3KeyPrefix: Option[String],
    lambdaHandlers: Seq[String],
    roleName: String,
    timeout: Option[Int], memory: Option[Int],
    autoCreate: Boolean)(implicit log: Logger): (String, LambdaARN) = {

    assert(lambdaHandlers.nonEmpty)
    val resolvedLambdaHandlers = lambdaHandlers.map { h => HandlerName(s"${h}::handleRequest") }
    log.info(s"Inferred lambda handlers are: ${resolvedLambdaHandlers.mkString(", ")}")

    val resolvedRegion = Region(region)
    val awsS3 = new AWSS3(resolvedRegion)
    val awsLambda = new AWSLambdaClient(resolvedRegion)
    val awsIam = new AwsIam(resolvedRegion)

    val resolvedLambdaName = LambdaName(lambdaName)

    val resolvedS3KeyPrefix = s3KeyPrefix.getOrElse("")

    val resolvedBucketId = S3BucketId(s3Bucket)
    val resolvedTimeout = timeout.map(Timeout)
    val resolvedMemory = memory.map(Memory)

    (for {
      role <- awsIam.getOrCreateRole(RoleName(roleName), autoCreate)
      s3Key <- awsS3.pushJarToS3(jar, resolvedBucketId, resolvedS3KeyPrefix, autoCreate)
    } yield {
      val handlerClass = resolvedLambdaHandlers.head
      deployLambdaFunction(
        awsLambda,
        LambdaParams(resolvedLambdaName, handlerClass, resolvedTimeout, resolvedMemory),
        role,
        S3Params(resolvedBucketId, s3Key)
      )
    }).recover {
      case ex =>
        sys.error(s"${ex.getMessage}\n${ex.getStackTrace.mkString("\n")}")
    }.get
  }

  private def deployLambdaFunction(awsLambda: AWSLambdaClient, params: LambdaParams, role: Role, s3Params: S3Params)
                                  (implicit log: Logger): (String, LambdaARN) = {
    awsLambda.deployLambda(params, role.arn, s3Params) match {
      case Success(Left(createFunctionCodeResult)) =>
        params.name.value -> LambdaARN(createFunctionCodeResult.getFunctionArn)
      case Success(Right(updateFunctionCodeResult)) =>
        params.name.value -> LambdaARN(updateFunctionCodeResult.getFunctionArn)
      case Failure(ex) =>
        sys.error(s"Failed to create lambda function: ${ex.getMessage}\n${ex.getStackTrace.mkString("\n")}")
    }
  }
}
