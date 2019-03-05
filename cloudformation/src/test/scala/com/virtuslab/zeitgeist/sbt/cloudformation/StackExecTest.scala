package com.virtuslab.zeitgeist.sbt.cloudformation

import com.virtuslab.zeitgeist.sbt.SbtTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}

import scala.collection.mutable
import scala.util.{Random, Success}

class StackExecTest extends WordSpec with MustMatchers with MockFactory with SbtTest {

  "Several stacks" should {
    "work in proper order" in {
      val resultAccumulator = mutable.Buffer[String]()

      val stackExecution = for {
        stack1 <- StackExec("stack1", "path/path1")
        stack2 <- StackExec("stack2", "path/path2")
      } yield {
        resultAccumulator.append("final")
        StackExec.done
      }

      resultAccumulator.toList must be(Nil)

      val traversal = new StackTraversal
      traversal.traverse(stackExecution) { desc =>
        resultAccumulator.append(desc.name)
        Success(StackResults())
      }


      resultAccumulator.toList must be(List("stack1", "stack2", "final"))
    }

    "skip dummy exec steps" in {
      val resultAccumulator = mutable.Buffer[String]()

      val randomRetVal = Random.alphanumeric.take(5).mkString

      val stackExecution = for {
        _ <- StackExec("stack1", "path/path1", Map("param" -> "value "))
        dummyStack <- StackExec {
          StackResults("param" -> randomRetVal)
        }
        stack3 <- StackExec("stack3", "path/path3", Map("param" -> dummyStack.value("param")))
      } yield {
        resultAccumulator.append("final")
        StackResults("param" -> stack3.value("param"))
      }

      val traversal = new StackTraversal
      val finalResults = traversal.traverse(stackExecution) { desc =>
        resultAccumulator.append(desc.name)
        Success(StackResults(desc.params.toList :_*))
      }

      // dummy step should be skipped
      resultAccumulator.toList must be(List("stack1", "stack3", "final"))

      finalResults.isSuccess must be(true)
      finalResults.get.results("param").keyValue must be(randomRetVal)
    }
  }
}
