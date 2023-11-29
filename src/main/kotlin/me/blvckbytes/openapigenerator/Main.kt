package me.blvckbytes.openapigenerator

import me.blvckbytes.openapigenerator.endpoint.EndpointParser
import me.blvckbytes.openapigenerator.generator.OpenApiGenerator
import java.io.File
import kotlin.math.roundToInt

fun main() {
  val inputJarPath = "/Users/blvckbytes/Desktop/tagnet/tagnet-rest/app/build/libs/app-0.0.1-SNAPSHOT.jar"
  val outputDocumentPath = "/Users/blvckbytes/Desktop/generated_openapi.json"

  fun extractClassNameFromPath(path: String): String {
    return path.substring(path.lastIndexOf('/') + 1)
  }

  val start = System.nanoTime()
  val jar = JarReader.readJar(
    inputJarPath,
    { filePath -> filePath.startsWith("me/blvckbytes/") },
    { extractClassNameFromPath(it).endsWith("Controller") },
    { extractClassNameFromPath(it).contains("Dto") }
  )
  val endpoints = EndpointParser.parseEndpoints(jar)
  val end = System.nanoTime()

  val generatedDocument = OpenApiGenerator.generate(jar, endpoints)

  val outputFile = File(outputDocumentPath)

  if (!outputFile.exists())
    outputFile.createNewFile()

  outputFile.writeBytes(generatedDocument.toByteArray(Charsets.UTF_8))

  println("took ${((end - start) / 1000.0 / 1000.0).roundToInt()}ms")
}