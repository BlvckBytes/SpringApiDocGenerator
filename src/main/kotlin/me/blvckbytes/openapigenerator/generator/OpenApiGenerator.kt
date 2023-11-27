package me.blvckbytes.openapigenerator.generator

import com.fasterxml.jackson.annotation.JsonIgnore
import me.blvckbytes.openapigenerator.*
import me.blvckbytes.openapigenerator.endpoint.EndpointMethod
import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType
import me.blvckbytes.openapigenerator.endpoint.type.input.BuiltInEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.InputSource
import me.blvckbytes.openapigenerator.endpoint.type.input.JavaClassEndpointInputType
import org.codehaus.jettison.json.JSONArray
import org.codehaus.jettison.json.JSONObject
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.net.URLEncoder

object OpenApiGenerator {

  private val jsonIgnoreDescriptor = Util.makeDescriptor(JsonIgnore::class)
  private val discriminatorEnumName = Util.makeName(DiscriminatorEnum::class)

  fun generate(jar: JarContainer, endpoints: List<EndpointMethod>): String {
    val rootNode = JSONObject()
    rootNode.put("openapi", "3.0.1")

    val infoNode = JSONObject()
    rootNode.put("info", infoNode)
    infoNode.put("title", "OpenAPI Definition")
    infoNode.put("version", "v0")

    val serversNode = JSONArray()
    val serverNode = JSONObject()
    serversNode.put(serverNode)
    rootNode.put("servers", serversNode)
    serverNode.put("url", "http://localhost:8000")
    serverNode.put("description", "Local development server")

    val pathsNode = JSONObject()
    rootNode.put("paths", pathsNode)

    val componentsNode = JSONObject()
    val schemasNode = JSONObject()
    componentsNode.put("schemas", schemasNode)
    rootNode.put("components", componentsNode)

    val endpointNodeByAbsolutePath = mutableMapOf<String, JSONObject>()
    val createdSchemasByName = mutableMapOf<String, JavaClassFile>()

    for (endpoint in endpoints) {
      val path = endpoint.absoluteRequestPath
      val pathNode = endpointNodeByAbsolutePath.computeIfAbsent(path) {
        val node = JSONObject()
        pathsNode.put(endpoint.absoluteRequestPath, node)
        node
      }

      val methodNode = JSONObject()
      pathNode.put(endpoint.requestMethod.name.lowercase(), methodNode)

      val tagsNode = JSONArray()
      val nextPathSlashIndex = path.indexOf('/', 1)

      tagsNode.put(
        if (nextPathSlashIndex < 0)
          path.substring(1)
        else
          path.substring(1, nextPathSlashIndex)
      )

      methodNode.put("tags", tagsNode)

      val parametersNode = JSONArray()
      methodNode.put("parameters", parametersNode)

      for (parameterType in endpoint.parameterTypes) {
        when (parameterType.inputSource) {
          InputSource.PATH,
          InputSource.PARAMETER -> {
            val parameterNode = JSONObject()
            parametersNode.put(parameterNode)

            parameterNode.put("name", parameterType.name)

            val parameterSchemaNode = JSONObject()
            parameterNode.put("schema", parameterSchemaNode)

            if (parameterType.inputSource == InputSource.PATH) {
              parameterNode.put("in", "path")
              parameterNode.put("required", true)

              if (parameterType !is BuiltInEndpointInputType)
                throw IllegalStateException("Non-builtins are not supported in source PATH")

              appendTypeAndFormatForBuiltIn(parameterType.type, parameterSchemaNode)
              continue
            }

            // PARAMETER

            parameterNode.put("in", "query")
            // TODO: Field(s) required flag

            when (parameterType) {
              is BuiltInEndpointInputType -> {
                if (parameterType.type == BuiltInType.TYPE_MULTIPART_FILE) {
                  /*
                    requestBody:
                      content:
                        multipart/form-data:
                          schema:
                            type: object
                            properties:
                              ...
                   */
                  // TODO: Create a body entry, remember that it's form-data and
                  //       always append if this type is found. Also, what happens to other parameters
                  //       which have no annotation? Do they automatically need to go in here?
                  continue
                }

                appendTypeAndFormatForBuiltIn(parameterType.type, parameterSchemaNode)
              }
              is JavaClassEndpointInputType -> {
                val schemaName = generateSchema(parameterType.javaClass, null, jar, schemasNode, createdSchemasByName)
                parameterSchemaNode.put("\$ref", makeRefValue(schemaName))
              }
              else -> throw IllegalStateException("Unimplemented parameter type $parameterType")
            }
          }

          InputSource.BODY -> {
            if (methodNode.has("requestBody"))
              throw IllegalStateException("A single endpoint cannot have multiple request bodies")

            val requestBodyNode = JSONObject()
            methodNode.put("requestBody", requestBodyNode)

            val contentNode = JSONObject()
            requestBodyNode.put("content", contentNode)

            val contentTypeNode = JSONObject()
            contentNode.put("application/json", contentTypeNode)

            val schemaNode = JSONObject()
            contentTypeNode.put("schema", schemaNode)

            if (parameterType !is JavaClassEndpointInputType)
              throw IllegalStateException("Non-java-class are not supported in source BODY")

            val schemaName = generateSchema(parameterType.javaClass, null, jar, schemasNode, createdSchemasByName)
            schemaNode.put("\$ref", makeRefValue(schemaName))
          }
        }
      }

      val responsesNode = JSONObject()
      methodNode.put("responses", responsesNode)

      val responseNode = JSONObject()
      responsesNode.put(endpoint.successResponseCode.value().toString(), responseNode)

      responseNode.put("description", endpoint.successResponseCode.name)

      val returnType = endpoint.returnType ?: continue

      val responseContentNode = JSONObject()
      responseNode.put("content", responseContentNode)

      val responseContentContentTypeNode = JSONObject()
      responseContentNode.put("application/json", responseContentContentTypeNode)

      val responseContentSchemaNode = JSONObject()
      responseContentContentTypeNode.put("schema", responseContentSchemaNode)

      val schemaName = generateSchema(returnType.javaClass, returnType.generics, jar, schemasNode, createdSchemasByName)
      responseContentSchemaNode.put("\$ref", makeRefValue(schemaName))
    }

    // TODO: This is a stupid hack, but I have no idea why this library insists of escaping /
    return rootNode.toString(2).replace("\\/", "/")
  }

  private fun makeRefValue(schemaName: String): String {
    val encodedRefValue = URLEncoder.encode(schemaName, Charsets.UTF_8)
    return "#/components/schemas/$encodedRefValue"
  }

  private fun appendTypeAndFormatForBuiltIn(type: BuiltInType, node: JSONObject) {
    when (type) {
      BuiltInType.TYPE_UUID -> {
        node.put("type", "string")
        node.put("format", "uuid")
      }
      BuiltInType.TYPE_LOCAL_DATE_TIME -> {
        node.put("type", "string")
        node.put("format", "date-time")
      }
      BuiltInType.TYPE_STRING,
      BuiltInType.TYPE_CHAR -> {
        node.put("type", "string")
      }
      BuiltInType.TYPE_BYTE,
      BuiltInType.TYPE_INTEGER,
      BuiltInType.TYPE_SHORT -> {
        node.put("type", "integer")
        node.put("format", "int32")
      }
      BuiltInType.TYPE_LONG -> {
        node.put("type", "integer")
        node.put("format", "int64")
      }
      BuiltInType.TYPE_FLOAT -> {
        node.put("type", "number")
        node.put("format", "float")
      }
      BuiltInType.TYPE_DOUBLE -> {
        node.put("type", "number")
        node.put("format", "double")
      }
      BuiltInType.TYPE_BOOLEAN -> {
        node.put("type", "boolean")
      }
      else -> throw IllegalStateException("Type and format not implemented for $type")
    }
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

  private fun createAndAppendOneOfNode(
    extendingClasses: List<JavaClassFile>,
    schemaNode: JSONObject,
    jar: JarContainer,
    schemasNode: JSONObject,
    createdSchemasByName: MutableMap<String, JavaClassFile>
  ) {
    val possibleSchemasArray = JSONArray()
    schemaNode.put("oneOf", possibleSchemasArray)

    for (extendingClass in extendingClasses) {
      val possibleSchema = JSONObject()
      val generatedSchemaName = generateSchema(extendingClass, null, jar, schemasNode, createdSchemasByName)
      possibleSchema.put("\$ref", makeRefValue(generatedSchemaName))
      possibleSchemasArray.put(possibleSchema)
    }
  }

  private fun createAndAppendDiscriminatorNode(
    extendingClasses: List<JavaClassFile>,
    schemaNode: JSONObject,
    jar: JarContainer,
    schemasNode: JSONObject,
    createdSchemasByName: MutableMap<String, JavaClassFile>
  ) {
    var commonDiscriminatorField: FieldNode? = null

    for (extendingClass in extendingClasses) {
      var discriminatorField: FieldNode? = null

      for (extendingClassField in extendingClass.classNode.fields) {
        val fieldClass = jar.tryLocateClassByDescriptor(extendingClassField.desc) ?: continue

        if (jar.doesExtend(fieldClass, discriminatorEnumName)) {
          if (discriminatorField != null)
            throw IllegalStateException("$extendingClass has more than one discriminator field")
          discriminatorField = extendingClassField
        }
      }

      if (discriminatorField == null)
        throw IllegalStateException("$extendingClass does not contain a discriminator field")

      if (commonDiscriminatorField == null) {
        commonDiscriminatorField = discriminatorField
        continue
      }

      if (commonDiscriminatorField.desc != discriminatorField.desc)
        throw IllegalStateException("$extendingClass deviated from the common discriminator type ${discriminatorField.desc}")
    }

    if (commonDiscriminatorField == null)
      throw IllegalStateException("Could not decide on the discriminator field of $javaClass")

    val discriminatorClass = jar.locateClassByDescriptor(commonDiscriminatorField.desc)

    if (discriminatorClass.classNode.access and Opcodes.ACC_ENUM == 0)
      throw IllegalStateException("Discriminator field ${commonDiscriminatorField.name} of $javaClass is not an enum")

    val discriminatorMemberFields = discriminatorClass.classNode.fields.filter {
      it.access and (Opcodes.ACC_STATIC or Opcodes.ACC_ENUM) == 0
    }

    val discriminatorTypeFieldIndex = discriminatorMemberFields.indexOfFirst {
      it.name == DiscriminatorEnum::type.name && it.desc == Util.makeDescriptor(Class::class)
    }

    if (discriminatorTypeFieldIndex < 0)
      throw IllegalStateException("Could not locate the discriminator field ${DiscriminatorEnum::type.name}")

    val classInitInstructions = discriminatorClass.classNode.methods.firstOrNull { it.name == "<clinit>" }?.instructions
      ?: throw IllegalStateException("Could not locate <clinit> instructions of $discriminatorClass")

    val discriminatorConstantToSchemaName = mutableMapOf<String, String>()
    var ldcOccurrenceCounter = 0
    var lastConstantName: String? = null

    for (instruction in classInitInstructions) {
      // new call of a enum constant
      if (instruction is MethodInsnNode && instruction.name == "<init>") {
        ldcOccurrenceCounter = 0
        continue
      }

      // First ldc is the constant name
      // Then, I >guess<, all enum fields are loaded, in order
      if (instruction is LdcInsnNode) {
        val constant = instruction.cst

        if (ldcOccurrenceCounter == 0) {
          if (constant !is String)
            throw IllegalStateException("Expected a String constant to be loaded")
          lastConstantName = constant
        }

        if (ldcOccurrenceCounter == discriminatorTypeFieldIndex + 1) {
          if (constant !is Type)
            throw IllegalStateException("Expected a Type constant to be loaded")

          if (lastConstantName == null)
            throw IllegalStateException("Encountered a type constant before it's matching string constant")

          val typeDescriptor = constant.toString()
          val typeSchemaName = generateSchema(jar.locateClassByDescriptor(typeDescriptor), null, jar, schemasNode, createdSchemasByName)
          discriminatorConstantToSchemaName[lastConstantName] = typeSchemaName
        }

        ++ldcOccurrenceCounter
      }
    }

    val discriminatorNode = JSONObject()
    schemaNode.put("discriminator", discriminatorNode)

    discriminatorNode.put("propertyName", commonDiscriminatorField.name)

    val mappingNode = JSONObject()
    discriminatorNode.put("mapping", mappingNode)

    for (discriminatorEntry in discriminatorConstantToSchemaName)
      mappingNode.put(discriminatorEntry.key, makeRefValue(discriminatorEntry.value))
  }

  private fun generateSchema(
    javaClass: JavaClassFile,
    genericTypes: Array<JavaClassFile>?,
    jar: JarContainer,
    schemasNode: JSONObject,
    createdSchemasByName: MutableMap<String, JavaClassFile>
  ): String {
    val schemaName = (
      if (genericTypes != null)
        "${javaClass.simpleName}__" + genericTypes.joinToString(separator = "_") { it.simpleName }
      else
        javaClass.simpleName
    )

    val existingSchemaWithThisName = createdSchemasByName.put(schemaName, javaClass)

    if ((existingSchemaWithThisName) != null) {
      if (existingSchemaWithThisName == javaClass)
        return schemaName

      throw IllegalStateException("Schema name $schemaName was already taken by ${existingSchemaWithThisName.classNode.name}")
    }

    val schemaNode = JSONObject()
    val classNode = javaClass.classNode

    if (classNode.access and Opcodes.ACC_ENUM != 0) {
      schemaNode.put("type", "string")

      val enumValueArray = JSONArray()

      for (enumField in classNode.fields) {
        if (enumField.access and Opcodes.ACC_ENUM != 0)
          enumValueArray.put(enumField.name)
      }

      schemaNode.put("enum", enumValueArray)
    }

    else if (classNode.access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) != 0) {
      val extendingClasses = jar.findTypesThatExtendReturnType(javaClass)

      if (extendingClasses.isEmpty())
        throw IllegalStateException("$javaClass has no implementations")

      createAndAppendOneOfNode(extendingClasses, schemaNode, jar, schemasNode, createdSchemasByName)
      createAndAppendDiscriminatorNode(extendingClasses, schemaNode, jar, schemasNode, createdSchemasByName)
    }

    else {

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

        val builtInType = BuiltInType.getByDescriptor(field.desc)

        if (builtInType != null) {
          appendTypeAndFormatForBuiltIn(builtInType, property)
        }

        else {
          if (
            field.desc == "Ljava/util/List;" ||
            field.desc == "Ljava/util/Collection;" ||
            field.desc == "Ljava/util/Set;"
          ) {
            property.put("type", "array")

            val refObject = JSONObject()

            val genericPlaceholderDescriptor = field.signature.substring(
              field.signature.indexOf('<') + 1,
              field.signature.indexOf('>'),
            )

            val genericPlaceholders = parseGenericPlaceholders(javaClass)
            val genericType: JavaClassFile

            if (genericPlaceholderDescriptor.startsWith("T")) {
              if (genericPlaceholders == null)
                throw IllegalStateException("Need generic placeholders for schema ${javaClass.classNode.name}")

              if (genericTypes == null)
                throw IllegalStateException("Need generic types")

              val genericPlaceholderPath =
                genericPlaceholderDescriptor.substring(1, genericPlaceholderDescriptor.length - 1)
              val genericPlaceholderIndex = genericPlaceholders.indexOf(genericPlaceholderPath)

              if (genericPlaceholderIndex < 0)
                throw IllegalStateException("Could not decide generic placeholder index of $genericPlaceholderDescriptor")

              genericType = genericTypes.getOrNull(genericPlaceholderIndex)
                ?: throw IllegalStateException("No match for generic placeholder index $genericPlaceholderIndex")
            } else
              genericType = jar.locateClassByDescriptor(genericPlaceholderDescriptor)

            val generatedSchemaName = generateSchema(genericType, null, jar, schemasNode, createdSchemasByName)
            refObject.put("\$ref", makeRefValue(generatedSchemaName))

            property.put("type", "array")
            property.put("items", refObject)
          }

          // Array type
          else if (field.desc.startsWith('[')) {

            property.put("type", "array")

            val builtInArrayType = BuiltInType.getByDescriptor(field.desc.substring(1))
            if (builtInArrayType != null) {
              val arrayItemsNode = JSONObject()
              appendTypeAndFormatForBuiltIn(builtInArrayType, arrayItemsNode)
              property.put("items", arrayItemsNode)
            }

            else {
              val arrayType = jar.locateClassByDescriptor(field.desc.substring(1))

              val refObject = JSONObject()
              val generatedSchemaName = generateSchema(arrayType, null, jar, schemasNode, createdSchemasByName)
              refObject.put("\$ref", makeRefValue(generatedSchemaName))

              property.put("type", "array")
              property.put("items", refObject)
            }
          }

          else {
            if (!field.desc.startsWith('L'))
              throw IllegalStateException("Don't know how to map ${field.desc} (${field.name})")

            val subClass = jar.locateClassByDescriptor(field.desc)
            generateSchema(subClass, null, jar, schemasNode, createdSchemasByName)
            property.put("\$ref", makeRefValue(subClass.simpleName))
          }
        }

        // TODO: Figure out nullability and add that flag
        propertyMap.put(field.name, property)
      }

      schemaNode.put("properties", propertyMap)
    }

    schemasNode.put(schemaName, schemaNode)
    return schemaName
  }
}