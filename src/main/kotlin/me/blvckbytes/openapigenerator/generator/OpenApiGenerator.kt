package me.blvckbytes.openapigenerator.generator

import com.fasterxml.jackson.annotation.JsonIgnore
import me.blvckbytes.openapigenerator.DiscriminatorEnum
import me.blvckbytes.openapigenerator.JarContainer
import me.blvckbytes.openapigenerator.JavaClassFile
import me.blvckbytes.openapigenerator.endpoint.EndpointMethod
import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType
import me.blvckbytes.openapigenerator.endpoint.type.input.BuiltInEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.EndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.InputSource
import me.blvckbytes.openapigenerator.endpoint.type.input.JavaClassEndpointInputType
import me.blvckbytes.openapigenerator.util.JsonObjectBuilder
import me.blvckbytes.openapigenerator.util.Util
import org.codehaus.jettison.json.JSONArray
import org.codehaus.jettison.json.JSONObject
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.net.URLEncoder

object OpenApiGenerator {

  private val JSON_IGNORE_DESCRIPTOR = Util.makeDescriptor(JsonIgnore::class)
  private val DISCRIMINATOR_ENUM_NAME = Util.makeName(DiscriminatorEnum::class)

  fun generate(jar: JarContainer, endpoints: List<EndpointMethod>): String {
    val rootNode = JSONObject()

    JsonObjectBuilder.from(rootNode) {
      addString("openapi", "3.0.1")
      addObject("info") {
        addString("title", "OpenAPI Definition")
        addString("version", "v0")
      }
      addArray("servers") {
        addObject {
          addString("url", "http://localhost:8000")
          addString("description", "Local development server")
        }
      }
    }

    val pathsNode = JSONObject()
    rootNode.put("paths", pathsNode)

    val componentsNode = JSONObject()
    val schemasNode = JSONObject()
    componentsNode.put("schemas", schemasNode)
    rootNode.put("components", componentsNode)

    val endpointNodeByAbsolutePath = mutableMapOf<String, JSONObject>()
    val generatorState = GeneratorState(jar, mutableMapOf(), pathsNode, schemasNode)

    for (endpoint in endpoints) {
      val pathNode = endpointNodeByAbsolutePath.computeIfAbsent(endpoint.absoluteRequestPath) {
        val node = JSONObject()
        pathsNode.put(endpoint.absoluteRequestPath, node)
        node
      }

      appendEndpointToPathNode(generatorState, endpoint, pathNode)
    }

    // TODO: This is a stupid hack, but I have no idea why this library insists of escaping /
    return rootNode.toString(2).replace("\\/", "/")
  }

  private fun generateSchemaAndPutRefValue(
    generatorState: GeneratorState,
    javaClass: JavaClassFile,
    genericTypes: Array<JavaClassFile>?,
    node: JSONObject,
  ) {
    node.put("\$ref", makeRefValue(generateSchema(generatorState, javaClass, genericTypes)))
  }

  private fun appendParameterToEndpoint(
    generatorState: GeneratorState,
    parameterType: EndpointInputType,
    parametersNode: JSONArray,
    methodNode: JSONObject,
    endpoint: EndpointMethod,
    bodyIsSingleJavaClassRef: Boolean,
  ) {
    if (parameterType.inputSource == InputSource.BODY) {
      JsonObjectBuilder.fromKeyOrCreate(methodNode, "requestBody") {
        addObject("content", extend = true) {
          addObject(endpoint.requestContentType, extend = true) {
            addObject("schema", extend = true) schemaObject@ {

              if (bodyIsSingleJavaClassRef) {
                if (parameterType !is JavaClassEndpointInputType)
                  throw IllegalStateException("Expected java class input type")

                generateSchemaAndPutRefValue(generatorState, parameterType.javaClass, null, jsonObject)
                return@schemaObject
              }

              addString("type", "object")
              addObject("properties", extend = true) {
                addObject(parameterType.name) {
                  when (parameterType) {
                    is BuiltInEndpointInputType -> appendTypeAndFormatForBuiltIn(parameterType.type, this.jsonObject)
                    is JavaClassEndpointInputType -> generateSchemaAndPutRefValue(generatorState, parameterType.javaClass, null, jsonObject)
                    else -> throw IllegalStateException("Unimplemented parameter type ${parameterType.javaClass}")
                  }
                }
              }
            }
          }
        }
      }
      return
    }

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
      return
    }

    // PARAMETER

    parameterNode.put("in", "query")
    // TODO: Field(s) required flag

    when (parameterType) {
      is BuiltInEndpointInputType -> appendTypeAndFormatForBuiltIn(parameterType.type, parameterSchemaNode)
      is JavaClassEndpointInputType -> generateSchemaAndPutRefValue(generatorState, parameterType.javaClass, null, parameterSchemaNode)
      else -> throw IllegalStateException("Unimplemented parameter type $parameterType")
    }
  }

  private fun appendEndpointToPathNode(generatorState: GeneratorState, endpoint: EndpointMethod, pathNode: JSONObject) {
    val methodNode = JSONObject()
    pathNode.put(endpoint.requestMethod.name.lowercase(), methodNode)

    val tagsNode = JSONArray()
    methodNode.put("tags", tagsNode)
    tagsNode.put(makeTag(endpoint))

    // Parameters

    val parametersNode = JSONArray()
    methodNode.put("parameters", parametersNode)

    val bodyParameters = endpoint.parameterTypes.filter { it.inputSource == InputSource.BODY }
    val bodyIsSingleJavaClassRef = bodyParameters.size == 1 && bodyParameters[0] is JavaClassEndpointInputType

    for (parameterType in endpoint.parameterTypes) {
      appendParameterToEndpoint(
        generatorState, parameterType, parametersNode,
        methodNode, endpoint, bodyIsSingleJavaClassRef,
      )
    }

    // Responses

    val responsesNode = JSONObject()
    methodNode.put("responses", responsesNode)

    JsonObjectBuilder.from(responsesNode) {
      addObject(endpoint.successResponseCode.value().toString()) {
        addString("description", endpoint.successResponseCode.name)

        endpoint.returnType?.let {
          addObject("content") {
            addObject("application/json") {
              addObject("schema") {
                generateSchemaAndPutRefValue(generatorState, it.javaClass, it.generics, jsonObject)
              }
            }
          }
        }
      }
    }
  }

  private fun makeTag(endpoint: EndpointMethod): String {
    val nextPathSlashIndex = endpoint.absoluteRequestPath.indexOf('/', 1)

    if (nextPathSlashIndex < 0)
      return endpoint.absoluteRequestPath.substring(1)

    return endpoint.absoluteRequestPath.substring(1, nextPathSlashIndex)
  }

  private fun makeRefValue(schemaName: String): String {
    val encodedRefValue = URLEncoder.encode(schemaName, Charsets.UTF_8)
    return "#/components/schemas/$encodedRefValue"
  }

  private fun appendTypeAndEnumConstantsForEnum(javaClass: JavaClassFile, schemaNode: JSONObject) {
    schemaNode.put("type", "string")

    val enumValueArray = JSONArray()

    for (enumField in javaClass.classNode.fields) {
      if (enumField.access and Opcodes.ACC_ENUM != 0)
        enumValueArray.put(enumField.name)
    }

    schemaNode.put("enum", enumValueArray)
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
      BuiltInType.TYPE_MULTIPART_FILE -> {
        node.put("type", "string")
        node.put("format", "binary")
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
    generatorState: GeneratorState,
    extendingClasses: List<JavaClassFile>,
    schemaNode: JSONObject,
  ) {
    val possibleSchemasArray = JSONArray()
    schemaNode.put("oneOf", possibleSchemasArray)

    for (extendingClass in extendingClasses) {
      val possibleSchema = JSONObject()
      val generatedSchemaName = generateSchema(generatorState, extendingClass, null)
      possibleSchema.put("\$ref", makeRefValue(generatedSchemaName))
      possibleSchemasArray.put(possibleSchema)
    }
  }

  private fun createAndAppendDiscriminatorNode(
    generatorState: GeneratorState,
    extendingClasses: List<JavaClassFile>,
    schemaNode: JSONObject,
  ) {
    var commonDiscriminatorField: FieldNode? = null

    for (extendingClass in extendingClasses) {
      var discriminatorField: FieldNode? = null

      for (extendingClassField in extendingClass.classNode.fields) {
        val fieldClass = generatorState.jar.tryLocateClassByDescriptor(extendingClassField.desc) ?: continue

        if (generatorState.jar.doesExtend(fieldClass, DISCRIMINATOR_ENUM_NAME)) {
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

    val discriminatorClass = generatorState.jar.locateClassByDescriptor(commonDiscriminatorField.desc)

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
          val typeSchemaName = generateSchema(generatorState, generatorState.jar.locateClassByDescriptor(typeDescriptor), null)
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

  private fun createAndAppendTypedArray(
    generatorState: GeneratorState,
    property: JSONObject,
    field: FieldNode
  ) {
    property.put("type", "array")

    val builtInArrayType = BuiltInType.getByDescriptor(field.desc.substring(1))

    if (builtInArrayType != null) {
      val arrayItemsNode = JSONObject()
      appendTypeAndFormatForBuiltIn(builtInArrayType, arrayItemsNode)
      property.put("items", arrayItemsNode)
      return
    }

    val arrayType = generatorState.jar.locateClassByDescriptor(field.desc.substring(1))

    val refObject = JSONObject()
    val generatedSchemaName = generateSchema(generatorState, arrayType, null)
    refObject.put("\$ref", makeRefValue(generatedSchemaName))

    property.put("type", "array")
    property.put("items", refObject)
  }

  private fun createAndAppendGenericArray(
    generatorState: GeneratorState,
    javaClass: JavaClassFile,
    genericTypes: Array<JavaClassFile>?,
    property: JSONObject,
    field: FieldNode
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
      genericType = generatorState.jar.locateClassByDescriptor(genericPlaceholderDescriptor)

    val generatedSchemaName = generateSchema(generatorState, genericType, null)
    refObject.put("\$ref", makeRefValue(generatedSchemaName))

    property.put("type", "array")
    property.put("items", refObject)
  }

  private fun createAndAppendObjectProperties(
    generatorState: GeneratorState,
    javaClass: JavaClassFile,
    genericTypes: Array<JavaClassFile>?,
    schemaNode: JSONObject,
  ) {
    schemaNode.put("type", "object")

    val propertyMap = JSONObject()

    fieldIterator@ for (field in javaClass.classNode.fields) {
      if (field.visibleAnnotations?.any { it.desc == JSON_IGNORE_DESCRIPTOR } == true)
        continue@fieldIterator

      if (field.access and Opcodes.ACC_STATIC != 0)
        continue

      val property = JSONObject()

      val builtInType = BuiltInType.getByDescriptor(field.desc)

      if (builtInType != null)
        appendTypeAndFormatForBuiltIn(builtInType, property)

      else {
        if (field.desc == "Ljava/util/List;" || field.desc == "Ljava/util/Collection;" || field.desc == "Ljava/util/Set;")
          createAndAppendGenericArray(generatorState, javaClass, genericTypes, property, field)

        else if (field.desc.startsWith('['))
          createAndAppendTypedArray(generatorState, property, field)

        else {
          property.put("\$ref", makeRefValue(generateSchema(
            generatorState, generatorState.jar.locateClassByDescriptor(field.desc), null,
          )))
        }
      }

      // TODO: Figure out nullability and add that flag
      propertyMap.put(field.name, property)
    }

    schemaNode.put("properties", propertyMap)
  }

  private fun makeSchemaName(javaClass: JavaClassFile, genericTypes: Array<JavaClassFile>?): String {
    if (genericTypes == null)
      return javaClass.simpleName
    return "${javaClass.simpleName}__" + genericTypes.joinToString(separator = "_") { it.simpleName }
  }

  private fun generateSchema(
    generatorState: GeneratorState,
    javaClass: JavaClassFile,
    genericTypes: Array<JavaClassFile>?,
  ): String {
    val schemaName = makeSchemaName(javaClass, genericTypes)
    val existingSchemaWithThisName = generatorState.createdSchemasByName.put(schemaName, javaClass)

    if ((existingSchemaWithThisName) != null) {
      if (existingSchemaWithThisName == javaClass)
        return schemaName

      throw IllegalStateException("Schema name $schemaName was already taken by ${existingSchemaWithThisName.classNode.name}")
    }

    val schemaNode = JSONObject()

    if (javaClass.classNode.access and Opcodes.ACC_ENUM != 0)
      appendTypeAndEnumConstantsForEnum(javaClass, schemaNode)

    else if (javaClass.classNode.access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) != 0) {
      val extendingClasses = generatorState.jar.findTypesThatExtendReturnType(javaClass)

      if (extendingClasses.isEmpty())
        throw IllegalStateException("$javaClass has no implementations")

      createAndAppendOneOfNode(generatorState, extendingClasses, schemaNode)
      createAndAppendDiscriminatorNode(generatorState, extendingClasses, schemaNode)
    }

    else
      createAndAppendObjectProperties(generatorState, javaClass, genericTypes, schemaNode)

    generatorState.schemasNode.put(schemaName, schemaNode)
    return schemaName
  }
}