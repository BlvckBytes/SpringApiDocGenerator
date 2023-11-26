package me.blvckbytes.openapigenerator.generator

import com.fasterxml.jackson.annotation.JsonIgnore
import me.blvckbytes.openapigenerator.JarContainer
import me.blvckbytes.openapigenerator.JavaClassFile
import me.blvckbytes.openapigenerator.Util
import me.blvckbytes.openapigenerator.endpoint.EndpointMethod
import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType
import org.json.JSONObject
import org.objectweb.asm.Opcodes
import org.springframework.web.bind.annotation.RequestMethod

object OpenApiGenerator {

  private val jsonIgnoreDescriptor = Util.makeDescriptor(JsonIgnore::class)

  fun generate(jar: JarContainer, endpoints: List<EndpointMethod>) {
    val schemaBlocks = mutableListOf<JSONObject>()
    val seenSchemaNames = mutableSetOf<String>()

    for (endpoint in endpoints) {
      if (endpoint.requestMethod != RequestMethod.GET || endpoint.absoluteRequestPath != "/base-tag/tag/{id}")
        continue

      val returnType = endpoint.returnType ?: continue

      generateSchema(returnType.javaClass, returnType.generics, jar, schemaBlocks, seenSchemaNames)
      break
    }

    schemaBlocks.forEach { println(it.toString(2)) }
  }

  private fun parseGenericPlaceholders(javaClass: JavaClassFile): List<String>? {
    val signature = javaClass.classNode.signature

    if (signature == null || !signature.startsWith('<'))
      return null

    var genericsString = signature.substring(1, signature.indexOf('>'))
    val placeholders = mutableListOf<String>()

    while (true) {
      val separatorIndex = genericsString.indexOf(':')
      val genericEnd = genericsString.indexOf(';')
      val genericName = genericsString.substring(0, separatorIndex)

      placeholders.add(genericName)

      if (genericEnd + 1 == genericsString.length)
        break

      genericsString = genericsString.substring(genericEnd + 1)
    }

    return placeholders
  }

  private fun generateSchema(
    javaClass: JavaClassFile,
    genericTypes: Array<JavaClassFile>?,
    jar: JarContainer,
    schemaBlocks: MutableList<JSONObject>,
    seenSchemaNames: MutableSet<String>
  ) {
    val rootNode = JSONObject()
    val schemaNode = JSONObject()
    val schemaName = javaClass.simpleName

    val genericPlaceholders = parseGenericPlaceholders(javaClass)

    rootNode.put(schemaName, schemaNode)

    if (!seenSchemaNames.add(schemaName))
      throw IllegalStateException("Duplicate schema name $schemaName")

    val classNode = javaClass.classNode

    schemaNode.put("type", "object")

    val propertyMap = JSONObject()

    fieldIterator@ for (field in classNode.fields) {
      if (field.visibleAnnotations != null) {
        for (fieldAnnotation in field.visibleAnnotations) {
          if (fieldAnnotation.desc == jsonIgnoreDescriptor)
            continue@fieldIterator
        }
      }

      if (field.access and Opcodes.ACC_STATIC != 0)
        continue

      val property = JSONObject()

      when (val builtInType = BuiltInType.getByDescriptor(field.desc)) {
        BuiltInType.TYPE_UUID -> {
          property.put("type", "string")
          property.put("format", "uuid")
        }
        BuiltInType.TYPE_LOCAL_DATE_TIME -> {
          property.put("type", "string")
          property.put("format", "date-time")
        }
        BuiltInType.TYPE_STRING,
        BuiltInType.TYPE_CHAR -> {
          property.put("type", "string")
        }
        BuiltInType.TYPE_BYTE,
        BuiltInType.TYPE_INTEGER,
        BuiltInType.TYPE_SHORT -> {
          property.put("type", "integer")
          property.put("format", "int32")
        }
        BuiltInType.TYPE_LONG -> {
          property.put("type", "integer")
          property.put("format", "int64")
        }
        BuiltInType.TYPE_FLOAT -> {
          property.put("type", "number")
          property.put("format", "float")
        }
        BuiltInType.TYPE_DOUBLE -> {
          property.put("type", "number")
          property.put("format", "double")
        }
        BuiltInType.TYPE_BOOLEAN -> {
          property.put("type", "boolean")
        }
        null -> {
          if (!field.desc.startsWith('L'))
            throw IllegalStateException("Don't know how to map ${field.desc} (${field.name})")

          if (field.desc == "Ljava/util/List;") {
            property.put("type", "array")

            val itemsObject = JSONObject()
            val refObject = JSONObject()

            if (genericPlaceholders == null)
              throw IllegalStateException("Need generic placeholders")

            if (genericTypes == null)
              throw IllegalStateException("Need generic types")

            val genericPlaceholder = field.signature.substring(
              field.signature.indexOf('<') + 2, // <T...;>
              field.signature.indexOf('>') - 1, // Don't need the ;
            )

            val genericPlaceholderIndex = genericPlaceholders.indexOf(genericPlaceholder)

            if (genericPlaceholderIndex < 0)
              throw IllegalStateException("Could not decide generic placeholder index of $genericPlaceholder")

            val genericType = genericTypes.getOrNull(genericPlaceholderIndex)
              ?: throw IllegalStateException("No match for generic placeholder index $genericPlaceholderIndex")

            generateSchema(genericType, null, jar, schemaBlocks, seenSchemaNames)

            refObject.put("\$ref", "#components/schemas/${genericType.simpleName}")

            property.put("type", refObject)
            property.put("items", itemsObject)
            break
          }

          // TODO: Enums

          val subClass = jar.locateClassByDescriptor(field.desc)

          generateSchema(subClass, null, jar, schemaBlocks, seenSchemaNames)
          // TODO: Make use of isArray
          property.put("\$ref", "#/components/schemas/${subClass.simpleName}")
        }

        else -> throw IllegalStateException("Unimplemented type: $builtInType")
      }

      // TODO: Figure out nullability and add that flag
      propertyMap.put(field.name, property)
    }

    schemaNode.put("properties", propertyMap)
    schemaBlocks.add(rootNode)
  }
}