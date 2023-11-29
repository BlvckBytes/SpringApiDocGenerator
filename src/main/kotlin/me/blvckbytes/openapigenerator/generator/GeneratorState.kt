package me.blvckbytes.openapigenerator.generator

import me.blvckbytes.openapigenerator.JarContainer
import me.blvckbytes.openapigenerator.JavaClassFile
import org.codehaus.jettison.json.JSONObject

data class GeneratorState(
  val jar: JarContainer,
  val createdSchemasByName: MutableMap<String, JavaClassFile>,
  val pathsNode: JSONObject,
  val schemasNode: JSONObject
)