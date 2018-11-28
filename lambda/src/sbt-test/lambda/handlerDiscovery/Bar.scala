package bar

@cloud.zeitgeist.lambda.api.http.macros.LambdaHTTPApiInternal
class BarFQN

import cloud.zeitgeist.lambda.api.http.macros._

@LambdaHTTPApiInternal
class BarImported

import cloud.zeitgeist.lambda.api.http.macros.{LambdaHTTPApiInternal => MyLambda}

@MyLambda
class BarRenamed