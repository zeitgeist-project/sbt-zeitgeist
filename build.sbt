import sbt.Keys.publishTo
import sbt.ScriptedPlugin.autoImport.scriptedBufferLog

val projectVersion = "0.1.3-SNAPSHOT"
val projectOrg = "com.virtuslab.zeitgeist"
val awsSdkVersion = "1.11.1034"

lazy val commonSettings = Seq(
  organization := projectOrg,
  version := projectVersion,
  scalaVersion := "2.12.14",
  retrieveManaged := true,

  licenses += "MIT" -> url("http://opensource.org/licenses/MIT"),

  Test / run / fork := true,

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test
  ),

  coverageHighlighting := false,

  addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.0.0"),

  credentials ++= {
    val credentialsFile = Path.userHome / ".sbt" / ".credentials.zeitgeist"
    if (credentialsFile.exists())
      Seq(Credentials(credentialsFile))
    else if (sys.env.contains("CLOUDSMITH_USERNAME") && sys.env.contains("CLOUDSMITH_PASSWORD"))
      Seq(Credentials("Cloudsmith API", "maven.cloudsmith.io", sys.env("CLOUDSMITH_USERNAME"), sys.env("CLOUDSMITH_PASSWORD")))
    else
      Nil
  },

  publishTo := {
    val repo = "https://maven.cloudsmith.io/zeitgeist-project/"
    if (isSnapshot.value)
      Some("Cloudsmith Snapshots" at repo + "snapshots")
    else
      Some("Cloudsmith Releases" at repo + "releases")
  }
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "zeitgeist-sbt",
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))),
  ).
  aggregate(
    lambda,
    cloudFormation,
    util,
    apiGateway
  )

lazy val util = (project in file("util")).
  settings(commonSettings: _*).
  settings(
    name := "sbt-util",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-sts" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-cloudformation" % awsSdkVersion
    )
  )

lazy val cloudFormation = (project in file("cloudformation")).
  enablePlugins(SbtPlugin).
  settings(commonSettings: _*).
  settings(
    name := "sbt-cloudformation",
    sbtPlugin := true
  ).
  dependsOn(util, util % "test->test")

lazy val lambda = (project in file("lambda")).
  enablePlugins(SbtPlugin).
  settings(commonSettings: _*).
  settings(
    name := "sbt-lambda",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion
    ),

    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
    },
    scriptedBufferLog := false
  ).
  dependsOn(util, util % "test->test")

lazy val apiGateway = (project in file("api-gateway")).
  settings(commonSettings: _*).
  settings(
    name := "sbt-api-gateway",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-api-gateway" % awsSdkVersion
    )
  ).
  dependsOn(util, lambda, cloudFormation)

