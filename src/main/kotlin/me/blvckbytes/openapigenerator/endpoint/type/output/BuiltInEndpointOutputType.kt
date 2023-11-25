package me.blvckbytes.openapigenerator.endpoint.type.output

import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType

class BuiltInEndpointOutputType(
  val type: BuiltInType
) : EndpointOutputType {

  override fun toString(): String {
    return type.name
  }
}