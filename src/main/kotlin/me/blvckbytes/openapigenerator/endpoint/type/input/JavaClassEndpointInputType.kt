package me.blvckbytes.openapigenerator.endpoint.type.input

import me.blvckbytes.openapigenerator.JavaClassFile

class JavaClassEndpointInputType(
  val javaClass: JavaClassFile,
  override var inputSource: InputSource,
  override val name: String,
) : EndpointInputType {

  override fun toString(): String {
    return "$name ${javaClass.classNode.name} ($inputSource)"
  }
}