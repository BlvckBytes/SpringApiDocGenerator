package me.blvckbytes.openapigenerator.endpoint

import me.blvckbytes.openapigenerator.JarContainer
import me.blvckbytes.openapigenerator.JavaClassFile
import me.blvckbytes.openapigenerator.Util
import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType
import me.blvckbytes.openapigenerator.endpoint.type.input.BuiltInEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.EndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.InputSource
import me.blvckbytes.openapigenerator.endpoint.type.input.JavaClassEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.output.JavaClassEndpointOutputType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import kotlin.collections.ArrayList

object EndpointParser {

  private val requestMethodBySpecificDescriptor = mapOf(
    Pair(Util.makeDescriptor(GetMapping::class), RequestMethod.GET),
    Pair(Util.makeDescriptor(PostMapping::class), RequestMethod.POST),
    Pair(Util.makeDescriptor(PutMapping::class), RequestMethod.PUT),
    Pair(Util.makeDescriptor(DeleteMapping::class), RequestMethod.DELETE),
    Pair(Util.makeDescriptor(PatchMapping::class), RequestMethod.PATCH),
  )

  private val requestMappingDescriptor = Util.makeDescriptor(RequestMapping::class)
  private val responseStatusDescriptor = Util.makeDescriptor(ResponseStatus::class)
  private val httpStatusDescriptor = Util.makeDescriptor(HttpStatus::class)

  private fun parseMappingAnnotationPath(mappingAnnotation: AnnotationNode): String {
    val annotationValues = parseAnnotationValues(mappingAnnotation)

    if (annotationValues != null) {
      for (annotationValueEntry in annotationValues) {
        val annotationValueName = annotationValueEntry.key
        if (annotationValueName == "value" || annotationValueName == "path") {
          val path = when (val annotationValue = annotationValueEntry.value) {
            is ArrayList<*> -> annotationValue.fold("") { accumulator, value -> joinPaths(accumulator, value as String) }
            else -> throw IllegalStateException("Unexpected value type for $annotationValueName: ${annotationValue.javaClass}")
          }

          if (path.isBlank())
            continue

          return path
        }
      }
    }

    return ""
  }

  private fun parseRequestMethodAndPath(methodNode: MethodNode): Pair<String, RequestMethod>? {
    if (methodNode.visibleAnnotations == null)
      return null

    for (methodAnnotation in methodNode.visibleAnnotations) {
      val requestMethod = requestMethodBySpecificDescriptor[methodAnnotation.desc] ?: continue
      return Pair(parseMappingAnnotationPath(methodAnnotation), requestMethod)
    }

    return null
  }

  private fun resolveInputSource(annotationNodes: List<AnnotationNode>?): InputSource {
    if (annotationNodes != null) {
      for (argumentAnnotation in annotationNodes) {
        when (argumentAnnotation.desc) {
          'L' + PathVariable::class.qualifiedName!!.replace('.', '/') + ';' -> {
            return InputSource.PATH
          }
          'L' + RequestParam::class.qualifiedName!!.replace('.', '/') + ';' -> {
            return InputSource.PARAMETER
          }
          'L' + RequestBody::class.qualifiedName!!.replace('.', '/') + ';' -> {
            return InputSource.BODY
          }
          "Ljakarta/validation/Valid;" -> {
            // noop for now
          }
          else -> throw IllegalStateException("Encountered unknown argument annotation: ${argumentAnnotation.desc}")
        }
      }
    }

    // I *think* that this is the default, if not specified otherwise by an annotation
    return InputSource.PARAMETER
  }

  private fun parseSuccessResponseCode(methodNode: MethodNode): HttpStatus {
    if (methodNode.visibleAnnotations != null) {
      for (annotation in methodNode.visibleAnnotations) {
        if (annotation.desc != responseStatusDescriptor)
          continue

        val annotationValues = parseAnnotationValues(annotation) ?: continue
        val codeValue = annotationValues["code"] ?: annotationValues["value"]

        if (codeValue != null && codeValue is Array<*> && codeValue.size >= 2) {
          if (codeValue[0] != httpStatusDescriptor)
            throw IllegalStateException("Expected value of type $httpStatusDescriptor")
          return HttpStatus.valueOf(codeValue[1] as String)
        }
      }
    }

    // If not specified otherwise by an annotation, 200 OK is to be assumed
    return HttpStatus.OK
  }

  private fun parseReturnGenerics(methodNode: MethodNode, jar: JarContainer): Array<JavaClassFile>? {
    val signature = methodNode.signature ?: return null
    val returnType = signature.substring(signature.indexOf(')') + 1)
    val genericsBegin = returnType.indexOf('<')

    if (genericsBegin < 0)
      return null

    val genericValue = returnType.substring(genericsBegin + 1, returnType.indexOf('>'))

    return Type.getMethodType("($genericValue)V")
      .argumentTypes
      .map{ jar.locateClassByPath(it.internalName) }
      .toTypedArray()
  }

  private fun parseEndpoint(basePath: String, methodNode: MethodNode, jar: JarContainer): EndpointMethod? {
    val (requestPath, requestMethod) = parseRequestMethodAndPath(methodNode) ?: return null
    val absoluteRequestPath = joinPaths(basePath, requestPath)
    val methodType = Type.getMethodType(methodNode.desc)

    val returnType = (
      if (methodType.returnType.descriptor == "V")
        null
      else
        JavaClassEndpointOutputType(
          jar.locateClassByPath(methodType.returnType.internalName),
          parseReturnGenerics(methodNode, jar)
        )
      )

    val argumentTypes = mutableListOf<EndpointInputType>()
    val argumentNames = (methodNode.localVariables ?: emptyList()).map { it.name }
    val argumentAnnotationLists = methodNode.visibleParameterAnnotations ?: emptyArray()

    for (argumentTypeIndex in methodType.argumentTypes.indices) {
      val argumentType = methodType.argumentTypes[argumentTypeIndex]
      val builtInArgumentType = BuiltInType.getByDescriptor(argumentType.descriptor)

      val argumentAnnotations = argumentAnnotationLists.getOrNull(argumentTypeIndex)
      val inputSource = resolveInputSource(argumentAnnotations)

      // [0] = this
      val argumentName = argumentNames.getOrNull(argumentTypeIndex + 1)
        ?: throw IllegalStateException("Could not resolve argument name for index $argumentTypeIndex")

      argumentTypes.add(
        if (builtInArgumentType != null)
          BuiltInEndpointInputType(builtInArgumentType, inputSource, argumentName)
        else
          JavaClassEndpointInputType(
            jar.locateClassByPath(argumentType.internalName),
            inputSource, argumentName
          )
      )
    }

    // TODO: Recurse down whatever this method calls to find all thrown exceptions

    return EndpointMethod(
      returnType,
      argumentTypes,
      requestMethod,
      absoluteRequestPath,
      parseSuccessResponseCode(methodNode)
    )
  }

  private fun processController(classNode: ClassNode, jar: JarContainer, endpoints: MutableList<EndpointMethod>) {
    var basePath: String? = null

    if (classNode.visibleAnnotations != null) {
      for (methodAnnotation in classNode.visibleAnnotations) {
        if (methodAnnotation.desc == requestMappingDescriptor) {
          basePath = parseMappingAnnotationPath(methodAnnotation)
          break
        }
      }
    }

    if (basePath == null)
      return

    for (method in classNode.methods) {
      if (method.access and Opcodes.ACC_PUBLIC != 0)
        endpoints.add(parseEndpoint(basePath, method, jar) ?: continue)
    }
  }

  fun parseEndpoints(jar: JarContainer): List<EndpointMethod> {
    val endpoints = mutableListOf<EndpointMethod>()

    for (controllerPackagePath in jar.controllerPackagePaths) {
      for (entry in jar.classes) {
        if (!entry.key.startsWith(controllerPackagePath))
          continue

        processController(entry.value.classNode, jar, endpoints)
      }
    }

    return endpoints
  }

  private fun joinPaths(a: String, b: String): String {
    val aHasTrailing = a.endsWith('/')
    val bHasLeading = b.startsWith('/')

    val result = (
      if (aHasTrailing && bHasLeading)
        a.substring(1) + b
      else if (!aHasTrailing && !bHasLeading)
        "$a/$b"
      else
        a + b
      )

    if (result.endsWith('/'))
      return result.substring(0, result.length - 1)

    return result
  }

  private fun parseAnnotationValues(annotation: AnnotationNode): Map<String, Any>? {
    if (annotation.values == null)
      return null

    val result = mutableMapOf<String, Any>()

    for (i in annotation.values.indices step 2) {
      val valueName = annotation.values[i]
      val valueValue = annotation.values[i + 1]

      result[valueName as String] = valueValue
    }

    return result
  }
}