package me.blvckbytes.openapigenerator.endpoint.type.input

import me.blvckbytes.openapigenerator.endpoint.type.EndpointType

interface EndpointInputType : EndpointType {
  val inputSource: InputSource
  val name: String
}