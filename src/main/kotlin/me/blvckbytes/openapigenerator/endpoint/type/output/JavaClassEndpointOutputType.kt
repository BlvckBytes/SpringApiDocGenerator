package me.blvckbytes.openapigenerator.endpoint.type.output

import me.blvckbytes.openapigenerator.JavaClassFile

class JavaClassEndpointOutputType(
  val javaClass: JavaClassFile,
  val generics: Array<JavaClassFile>?
) {
  override fun toString(): String {
    if (generics == null)
      return javaClass.classNode.name
    return "${javaClass.classNode.name}<${generics.joinToString { it.classNode.name }}>"
  }
}