package bar

@com.virtuslab.zeitgeist.lambda.api.http.macros.LambdaHTTPApiInternal
class BarFQN

import com.virtuslab.zeitgeist.lambda.api.http.macros._

@LambdaHTTPApiInternal
class BarImported

import com.virtuslab.zeitgeist.lambda.api.http.macros.{LambdaHTTPApiInternal => MyLambda}

@MyLambda
class BarRenamed