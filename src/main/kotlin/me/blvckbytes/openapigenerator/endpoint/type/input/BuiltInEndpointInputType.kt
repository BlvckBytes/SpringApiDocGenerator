package me.blvckbytes.openapigenerator.endpoint.type.input

import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType

class BuiltInEndpointInputType(
  val type: BuiltInType,
  override var inputSource: InputSource,
  override val name: String,
) : EndpointInputType {

  override fun toString(): String {
    return "$name ${type.name} ($inputSource)"
  }
}