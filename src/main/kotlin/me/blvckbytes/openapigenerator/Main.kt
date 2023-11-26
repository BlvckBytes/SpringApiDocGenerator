package me.blvckbytes.openapigenerator

import me.blvckbytes.openapigenerator.endpoint.EndpointParser
import me.blvckbytes.openapigenerator.generator.OpenApiGenerator
import kotlin.math.roundToInt

fun main() {
  fun extractClassNameFromPath(path: String): String {
    return path.substring(path.lastIndexOf('/') + 1)
  }

  val start = System.nanoTime()
  val jar = JarReader.readJar(
    "/Users/blvckbytes/Desktop/tagnet/tagnet-rest/app/build/libs/app-0.0.1-SNAPSHOT.jar",
    { extractClassNameFromPath(it).endsWith("Controller") },
    { extractClassNameFromPath(it).contains("Dto") }
  )
  val endpoints = EndpointParser.parseEndpoints(jar)
  val end = System.nanoTime()

  endpoints.forEach(::println)

  OpenApiGenerator.generate(jar, endpoints)

  println("endpoint parsing took ${((end - start) / 1000.0 / 1000.0).roundToInt()}ms")
}