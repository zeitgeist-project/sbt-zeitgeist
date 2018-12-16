[![Build Status](https://travis-ci.org/zeitgeist-project/zeitgeist-sbt.svg)](https://travis-ci.org/zeitgeist-project/zeitgeist-sbt)
[![Coverage Status](https://coveralls.io/repos/github/zeitgeist-project/zeitgeist-sbt/badge.svg)](https://coveralls.io/github/zeitgeist-project/zeitgeist-sbt)

# zeitgeist-sbt
SBT Plugin for advanced AWS Lambda Deployment and Integration.

# Usage

This section contains description on how to configure your Sbt project and what tasks can be used afterwards.

## Configuring Sbt

In your `plugins.sbt` file you need to add following resolver & dependency:

```
Resolver.bintrayIvyRepo("zeitgeist", "sbt-plugins"),
```

```
addSbtPlugin("com.virtuslab.zeitgeist" % "sbt-lambda" % "0.0.4")
```

This Sbt plugin allows you to specify bunch of parameters that will be used for packaging & deploying your Lambda.

Here is an example:

```
  s3Bucket := "com.mycompany.myproject.lambda.{userid}",

  createAutomatically := true,

  awsLambdaMemory := 512,
  awsLambdaTimeout := 60,
  region := "eu-west-1",

  role := "data-pipeline-lambda"
```

* s3Bucket - according to AWS it's more reliable to have your Lambda package deployed on S3 before using it (this is
seemingly only for Lambdas larger then 10MB; we are using Scala here which needs to be wrapped within the JAR; it most
likely always exceeds that size).

S3 bucket names are global however. That means that you need to keep your names globally unique.

First of all you need to make sure that your Lambda project is globally unique. You can achieve this in the same way we
deal with package names - just use your company/project reversed URI format (e.g. com.mycompany.myproject.mylambda).

This does not solve however problem with multiple devs working together on same project. To deal with that there are
placeholder possible that would ensure uniqueness. Here are allowed placeholders:
* username - AWS username, as retrieved by call to: `aws iam get-user` [SDK link](https://docs.aws.amazon.com/IAM/latest/APIReference/API_GetUser.html)

* userid - AWS userId, as retrieve by call to: `aws iam get-user` [SDK link](https://docs.aws.amazon.com/IAM/latest/APIReference/API_GetUser.html)

* hostname - local hostname as returned by Java call to: `InetAddress.getLocalHost().getHostName`

Placeholder names are case-insensitive.

# Based upon:

* [Gilt Groupe's sbt-aws-lambda](https://github.com/gilt/sbt-aws-lambda)
* [Brendan McAdams' quaich-project](https://github.com/quaich-project)

# Authors

 * Pawel (Paul) Dolega(https://github.com/pdolega) (only continuing the steps initiated by two projects above)


# Publishing
To publish artifacts use:
`sbt publish`