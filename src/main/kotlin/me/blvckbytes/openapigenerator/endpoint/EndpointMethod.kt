package me.blvckbytes.openapigenerator.endpoint

import me.blvckbytes.openapigenerator.endpoint.type.input.EndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.output.JavaClassEndpointOutputType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RequestMethod

class EndpointMethod(
  val returnType: JavaClassEndpointOutputType?,
  val parameterTypes: List<EndpointInputType>,
  val requestMethod: RequestMethod,
  val requestContentType: String,
  val absoluteRequestPath: String,
  val successResponseCode: HttpStatus
) {
  override fun toString(): String {
    val result = StringBuilder()

    result.append("EndpointMethod {")
    result.append("\nendpoint: $requestMethod $absoluteRequestPath ($successResponseCode)")
    result.append("\ncontent-type: $requestContentType")

    for (parameterIndex in parameterTypes.indices) {
      result.append("\nparameter $parameterIndex: ${parameterTypes[parameterIndex]}")
    }

    result.append("\nreturn: $returnType")
    result.append("\n}")

    return result.toString()
  }
}