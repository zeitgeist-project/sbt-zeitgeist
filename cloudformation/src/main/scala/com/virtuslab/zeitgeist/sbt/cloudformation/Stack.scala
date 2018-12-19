package com.virtuslab.zeitgeist.sbt.cloudformation

case class Stack(name: String,
                 path: String,
                 params: Map[String, String] = Map())

