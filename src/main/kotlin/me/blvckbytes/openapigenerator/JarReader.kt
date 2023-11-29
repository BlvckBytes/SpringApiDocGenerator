package me.blvckbytes.openapigenerator

import java.io.File
import java.util.zip.ZipInputStream

object JarReader {

  fun readJar(
    jarPath: String,
    methodInvocationOwnerPredicate: (classFilePath: String) -> Boolean,
    controllerPredicate: (classFilePath: String) -> Boolean,
    returnTypePredicate: (classFilePath: String) -> Boolean
  ): JarContainer {
    return JarContainer(
      File(jarPath).inputStream().use {
        val result = mutableMapOf<String, JavaClassFile>()
        collectClassFiles(ZipInputStream(it), result)
        result
      },
      methodInvocationOwnerPredicate,
      controllerPredicate, returnTypePredicate
    )
  }

  private fun collectClassFiles(stream: ZipInputStream, map: MutableMap<String, JavaClassFile>) {
    while (true) {
      val entry = stream.nextEntry ?: break

      if (entry.isDirectory)
        continue

      val name = entry.name

      if (name.endsWith(".jar")) {
        collectClassFiles(ZipInputStream(stream), map)
        continue
      }

      if (!name.endsWith(".class"))
        continue

      if (name.startsWith("META-INF") || name.contains("module-info"))
        continue

      val className = name.substring(0, name.indexOf('.'))

      if (map.put(className, JavaClassFile(name, stream.readAllBytes())) != null)
        throw IllegalStateException("Duplicate class name encountered while reading jar: $className")
    }
  }
}