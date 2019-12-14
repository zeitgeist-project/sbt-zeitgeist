import sbt.Keys.publishTo
import sbt.ScriptedPlugin.autoImport.scriptedBufferLog

val projectVersion          = "0.1.2-SNAPSHOT"
val projectOrg              = "com.virtuslab.zeitgeist"
val awsSdkVersion           = "1.11.458"

lazy val commonSettings = Seq(
  organization := projectOrg,
  version := projectVersion,
  scalaVersion := "2.12.8",
  retrieveManaged := true,

  bintrayOrganization := Some("virtuslab"),
  bintrayRepository := "sbt-plugins",
  licenses += "MIT" -> url("http://opensource.org/licenses/MIT"),

  fork in (Test, run) := true,

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.3" % Test,
    "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test
  ),

  coverageHighlighting := false,

  addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.8")
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "zeitgeist-sbt",
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))),
    bintrayRelease := {}
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
      "com.amazonaws"  % "aws-java-sdk-iam"            % awsSdkVersion,
      "com.amazonaws"  % "aws-java-sdk-sts"            % awsSdkVersion,
      "com.amazonaws"  % "aws-java-sdk-s3"             % awsSdkVersion,
      "com.amazonaws"  % "aws-java-sdk-cloudformation" % awsSdkVersion
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
      "com.amazonaws"  % "aws-java-sdk-lambda"      % awsSdkVersion
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
      "com.amazonaws"  % "aws-java-sdk-api-gateway" % awsSdkVersion
    )
  ).
  dependsOn(util, lambda, cloudFormation)

