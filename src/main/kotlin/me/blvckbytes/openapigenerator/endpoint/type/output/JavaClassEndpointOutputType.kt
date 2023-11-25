package me.blvckbytes.openapigenerator.endpoint.type.output

import me.blvckbytes.openapigenerator.JavaClassFile

class JavaClassEndpointOutputType(
  val javaClass: JavaClassFile
) : EndpointOutputType {
  override fun toString(): String {
    return javaClass.classNode.name
  }
}