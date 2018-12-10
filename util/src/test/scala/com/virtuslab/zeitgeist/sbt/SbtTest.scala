package com.virtuslab.zeitgeist.sbt

import sbt.{Level, Logger}

trait SbtTest {
  implicit val logger = new Logger {
    override def log(level: Level.Value, message: => String): Unit = println(s"${level.toString.toUpperCase}:\t${message}")
    override def trace(t: => Throwable): Unit = t.printStackTrace()
    override def success(message: => String): Unit = println(message)
  }
}
