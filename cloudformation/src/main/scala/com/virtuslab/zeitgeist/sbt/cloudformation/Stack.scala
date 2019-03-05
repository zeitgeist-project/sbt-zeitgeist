package com.virtuslab.zeitgeist.sbt.cloudformation

import com.amazonaws.services.cloudformation.model.Output

import scala.util.Try

sealed trait StackExec {

  def map(f: StackResults => StackResults): StackExec = {
    StackMap(this, f)
  }

  def flatMap(f: StackResults => StackExec): StackExec = {
    StackFlatMap(this, f)
  }
}


object StackExec {
  def apply(name: String, path: String, params: Map[String, String] = Map()): StackExec = {
    StackDescriptor(name, path, params)
  }

  def apply(f: => StackResults): StackExec = StackDummyResults { Unit => f }

  def done: StackResults = new StackResults(StackArn.dummy)
}

private[cloudformation] final case class StackMap(stackExec: StackExec,
                                                  f: StackResults => StackResults) extends StackExec

private[cloudformation] final case class StackFlatMap(stackExec: StackExec,
                                                      f: StackResults => StackExec) extends StackExec

private[cloudformation] final case class StackDummyResults(results: Unit => StackResults) extends StackExec

private[cloudformation] final case class StackDescriptor(name: String,
                                                         path: String,
                                                         params: Map[String, String] = Map()) extends StackExec


case class StackArn(value: String) extends AnyVal
object StackArn {
  def dummy = StackArn("--dummy--")
}

class StackResults private[cloudformation] (stackArn: StackArn, val results: Map[String, StackOutput] = Map.empty) {
  def value(outputKey: String): String = results(outputKey).keyValue
}

object StackResults {
  def apply(resultList: (String, String)*): StackResults = {
    new StackResults(
      StackArn.dummy,
      resultList.map { case (key, value) => (key, StackOutput(key, value, "")) }.toMap
    )
  }
}

object StackOutput {
  def apply(awsOutput: Output): StackOutput = StackOutput(
    awsOutput.getOutputKey,
    awsOutput.getOutputValue,
    awsOutput.getDescription
  )
}
case class StackOutput(keyName: String, keyValue: String, description: String)

class StackTraversal {
  def traverse(stackExec: StackExec)(executor: StackDescriptor => Try[StackResults]): Try[StackResults] = {
    stackExec match {
      case StackMap(exec, f) =>
        traverse(exec)(executor).map(f)

      case StackFlatMap(exec, f) =>
        traverse(exec)(executor).flatMap { prevResults =>
          traverse(f(prevResults))(executor)
        }

      case dummy: StackDummyResults =>
        Try(dummy.results(()))

      case descriptor: StackDescriptor =>
        executor(descriptor)
    }
  }
}