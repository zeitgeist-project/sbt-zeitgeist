package cloud.zeitgeist.sbt.lambda

import sbt.internal.inc.Analysis
import xsbti.api._
import xsbti.compile.CompileAnalysis

private[lambda] object LambdaClassDiscovery {
  def perform(analysis: CompileAnalysis): Seq[String] = {
    val can = analysis.asInstanceOf[Analysis]

    val classes = for {
      (_, clazz) <- can.apis.internal

      classApi = clazz.api().classApi()
      annotation <- classApi.annotations() if isAnyLambdaAnnotation(annotation)
    } yield classApi.name()

    classes.toSeq.sorted
  }

  private def isAnyLambdaAnnotation(annotation: Annotation) = annotation.base() match {
    case base: Projection => isHttpApiAnnotation(base) || isDirectAnnotation(base)
    case _ => false
  }

  private val httpAnnotationPackage = "cloud.zeitgeist.lambda.api.http.macros"
  private val httpAnnotationName = "LambdaHTTPApiInternal"
  private val httpAnnotationPackageType = Singleton.of(
    Path.of(httpAnnotationPackage.split('.').map(Id.of) :+ This.create())
  )
  private val httpAnnotationFQN = Projection.of(httpAnnotationPackageType, httpAnnotationName)

  private def isHttpApiAnnotation(annotation: Projection): Boolean = annotation == httpAnnotationFQN


  private val directAnnotationPackage = "cloud.zeitgeist.lambda.api.direct"
  private val directAnnotationName = "DirectLambda"
  private val directAnnotationPackageType = Singleton.of(
    Path.of(directAnnotationPackage.split('.').map(Id.of) :+ This.create())
  )
  private val directAnnotationFQN = Projection.of(directAnnotationPackageType, directAnnotationName)

  private def isDirectAnnotation(annotation: Projection): Boolean = annotation == directAnnotationFQN
}
