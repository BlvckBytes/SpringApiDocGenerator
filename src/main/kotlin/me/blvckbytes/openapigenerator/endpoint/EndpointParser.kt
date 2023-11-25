package me.blvckbytes.openapigenerator.endpoint

import me.blvckbytes.openapigenerator.JavaClassFile
import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType
import me.blvckbytes.openapigenerator.endpoint.type.input.BuiltInEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.EndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.InputSource
import me.blvckbytes.openapigenerator.endpoint.type.input.JavaClassEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.output.BuiltInEndpointOutputType
import me.blvckbytes.openapigenerator.endpoint.type.output.JavaClassEndpointOutputType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.collections.ArrayList

object EndpointParser {

  private val requestMethodBySpecificDescriptor = mapOf(
    Pair('L' + GetMapping::class.qualifiedName!!.replace('.', '/') + ';', RequestMethod.GET),
    Pair('L' + PostMapping::class.qualifiedName!!.replace('.', '/') + ';', RequestMethod.POST),
    Pair('L' + PutMapping::class.qualifiedName!!.replace('.', '/') + ';', RequestMethod.PUT),
    Pair('L' + DeleteMapping::class.qualifiedName!!.replace('.', '/') + ';', RequestMethod.DELETE),
    Pair('L' + PatchMapping::class.qualifiedName!!.replace('.', '/') + ';', RequestMethod.PATCH),
  )

  private val requestMappingDescriptor = 'L' + RequestMapping::class.qualifiedName!!.replace('.', '/') + ';'
  private val responseStatusDescriptor = 'L' + ResponseStatus::class.qualifiedName!!.replace('.', '/') + ';'
  private val httpStatusDescriptor = 'L' + HttpStatus::class.qualifiedName!!.replace('.', '/') + ';'

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

  private fun parseMappingAnnotationPath(mappingAnnotation: AnnotationNode): String {
    val annotationValues = parseAnnotationValues(mappingAnnotation)

    if (annotationValues != null) {
      for (annotationValueEntry in annotationValues) {
        val annotationValueName = annotationValueEntry.key
        if (annotationValueName == "value" || annotationValueName == "path") {
          val path = when (val annotationValue = annotationValueEntry.value) {
            is Array<*> -> annotationValue.fold("") { accumulator, value -> joinPaths(accumulator, value as String) }
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

  private fun parseEndpoint(basePath: String, methodNode: MethodNode, classFileByName: Map<String, JavaClassFile>): EndpointMethod? {
    val (requestPath, requestMethod) = parseRequestMethodAndPath(methodNode) ?: return null
    val absoluteRequestPath = joinPaths(basePath, requestPath)

    val methodType = Type.getMethodType(methodNode.desc)
    val returnTypeClassName = methodType.returnType.className
    val builtInReturnType = BuiltInType.getByClassName(returnTypeClassName)

    val returnType = (
      if (builtInReturnType != null)
        BuiltInEndpointOutputType(builtInReturnType)
      else if (returnTypeClassName == "void")
        null
      else
        JavaClassEndpointOutputType(
          classFileByName[returnTypeClassName.replace('.', '/')]
            ?: throw IllegalStateException("Could not locate class $returnTypeClassName")
        )
      )

    val argumentTypes = mutableListOf<EndpointInputType>()
    val argumentNames = (methodNode.localVariables ?: emptyList()).map { it.name }
    val argumentAnnotationLists = methodNode.visibleParameterAnnotations ?: emptyArray()

    for (argumentTypeIndex in methodType.argumentTypes.indices) {
      val argumentType = methodType.argumentTypes[argumentTypeIndex]
      val argumentTypeClassName = argumentType.className
      val builtInArgumentType = BuiltInType.getByClassName(argumentTypeClassName)

      val argumentAnnotations = argumentAnnotationLists.getOrNull(argumentTypeIndex)
      val inputSource = resolveInputSource(argumentAnnotations)

      // [0] = this
      val argumentName = argumentNames.getOrNull(argumentTypeIndex + 1)
        ?: throw IllegalStateException("Could not resolve argument name index $argumentTypeIndex")

      argumentTypes.add(
        if (builtInArgumentType != null)
          BuiltInEndpointInputType(builtInArgumentType, inputSource, argumentName)
        else
          JavaClassEndpointInputType(
            classFileByName[argumentTypeClassName.replace('.', '/')]
              ?: throw IllegalStateException("Could not locate class $returnTypeClassName"),
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

  private fun processController(classNode: ClassNode, classFileByName: Map<String, JavaClassFile>): List<EndpointMethod>? {
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
      return null

    val endpoints = mutableListOf<EndpointMethod>()

    for (method in classNode.methods) {
      if (method.access and Opcodes.ACC_PUBLIC == 0)
        continue

      val methodEndpoint = parseEndpoint(basePath, method, classFileByName) ?: continue
      endpoints.add(methodEndpoint)
    }

    if (endpoints.isEmpty())
      return null

    return endpoints
  }

  fun parseEndpoints(jarPath: String, controllerPackages: List<String>): List<EndpointMethod> {
    val classFileByClassName = File(jarPath).inputStream().use {
      val result = mutableMapOf<String, JavaClassFile>()
      collectClassFiles(ZipInputStream(it), result)
      result
    }

    val endpoints = mutableListOf<EndpointMethod>()

    for (controllerPackage in controllerPackages) {
      val controllerPackagePath = controllerPackage.replace('.', '/')
      for (entry in classFileByClassName) {
        if (!entry.key.startsWith(controllerPackagePath))
          continue

        val controllerEndpoints = processController(entry.value.classNode, classFileByClassName) ?: continue
        endpoints.addAll(controllerEndpoints)
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

  private fun collectClassFiles(stream: ZipInputStream, list: MutableMap<String, JavaClassFile>) {
    while (true) {
      val entry = stream.nextEntry ?: break

      if (entry.isDirectory)
        continue

      val name = entry.name

      if (name.endsWith(".class")) {
        if (!name.startsWith("me/blvckbytes"))
          continue

        val className = name.substring(0, name.indexOf('.'))

        if (list.put(className, JavaClassFile(name, stream.readAllBytes())) != null)
          throw IllegalStateException("Duplicate class name: $className")

        continue
      }

      if (name.endsWith(".jar")) {
        collectClassFiles(ZipInputStream(stream), list)
        continue
      }
    }
  }
}