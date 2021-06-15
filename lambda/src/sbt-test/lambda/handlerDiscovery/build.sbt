enablePlugins(AWSLambdaPlugin)

scalaVersion := "2.12.14"

region := "not-important"

InputKey[Unit]("testLambdas") := {
  val expected = Def.spaceDelimited().parsed
  val found: Seq[String] = discoverAWSLambdaClasses.value
  assert(expected == found, s"Expected: $expected but found: $found")
}
