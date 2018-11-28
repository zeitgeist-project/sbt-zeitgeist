enablePlugins(AWSLambdaPlugin)
enablePlugins(SbtPlugin)

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
}

region := "not-important"

val testLambdas = inputKey[Unit]("testLambdas")

testLambdas := {
  val expected = Def.spaceDelimited().parsed
  val found: Seq[String] = discoverAWSLambdaClasses.value
  assert(expected == found, s"Expected: $expected but found: $found")
}