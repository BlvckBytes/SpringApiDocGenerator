package me.blvckbytes.openapigenerator

import me.blvckbytes.openapigenerator.endpoint.EndpointParser
import kotlin.math.roundToInt

fun main() {
  val start = System.nanoTime()
  val endpoints = EndpointParser.parseEndpoints(
    "/Users/blvckbytes/Desktop/tagnet/tagnet-rest/app/build/libs/app-0.0.1-SNAPSHOT.jar",
    listOf("me.blvckbytes.tagnet.rest.controller")
  )
  val end = System.nanoTime()

  endpoints.forEach(::println)

  println("endpoint parsing took ${((end - start) / 1000.0 / 1000.0).roundToInt()}ms")
}