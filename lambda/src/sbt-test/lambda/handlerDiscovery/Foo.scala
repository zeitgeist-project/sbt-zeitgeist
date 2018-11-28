package foo

import scala.annotation.Annotation
import scala.annotation.StaticAnnotation

class LambdaHTTPApiInternal extends StaticAnnotation

@LambdaHTTPApiInternal
class Foo