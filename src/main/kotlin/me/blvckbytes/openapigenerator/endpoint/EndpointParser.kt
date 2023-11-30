package me.blvckbytes.openapigenerator.endpoint

import me.blvckbytes.openapigenerator.JarContainer
import me.blvckbytes.openapigenerator.JavaClassFile
import me.blvckbytes.openapigenerator.util.Util
import me.blvckbytes.openapigenerator.endpoint.type.BuiltInType
import me.blvckbytes.openapigenerator.endpoint.type.input.BuiltInEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.EndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.input.InputSource
import me.blvckbytes.openapigenerator.endpoint.type.input.JavaClassEndpointInputType
import me.blvckbytes.openapigenerator.endpoint.type.output.JavaClassEndpointOutputType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.Stack
import java.util.logging.*
import kotlin.collections.ArrayList

object EndpointParser {

  private val opcodeNameByOpcode = arrayOfNulls<String>(0xFF)

  private val logger = Logger.getLogger(EndpointParser::class.qualifiedName)

  init {
    logger.level = Level.INFO
    logger.addHandler(object : Handler() {

      override fun publish(record: LogRecord?) {
        if (record == null)
          return

        println("[${record.level}] ${record.message}")
      }

      override fun flush() {}

      override fun close() {}
    })

    val ignorePrefixes = listOf(
      "ASM", "H_", "T_", "F_", "V_", "ACC_",
      "V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9",
    )

    val ignoreEquals = listOf(
      "TOP", "INTEGER", "FLOAT", "DOUBLE", "LONG", "NULL", "UNINITIALIZED_THIS",
      "SOURCE_MASK", "SOURCE_DEPRECATED"
    )

    for (field in Opcodes::class.java.declaredFields) {
      if (field.type.simpleName != "int")
        continue

      val name = field.name

      if (ignorePrefixes.any{ name.startsWith(it) })
        continue

      if (ignoreEquals.any{ name == it })
        continue

      opcodeNameByOpcode[field.get(null) as Int] = field.name
    }
  }

  private val requestMethodBySpecificDescriptor = mapOf(
    Pair(Util.makeDescriptor(GetMapping::class), RequestMethod.GET),
    Pair(Util.makeDescriptor(PostMapping::class), RequestMethod.POST),
    Pair(Util.makeDescriptor(PutMapping::class), RequestMethod.PUT),
    Pair(Util.makeDescriptor(DeleteMapping::class), RequestMethod.DELETE),
    Pair(Util.makeDescriptor(PatchMapping::class), RequestMethod.PATCH),
  )

  private val requestMappingDescriptor = Util.makeDescriptor(RequestMapping::class)
  private val responseStatusDescriptor = Util.makeDescriptor(ResponseStatus::class)
  private val requestBodyDescriptor = Util.makeDescriptor(RequestBody::class)
  private val requestParamDescriptor = Util.makeDescriptor(RequestParam::class)
  private val requestPartDescriptor = Util.makeDescriptor(RequestPart::class)
  private val pathVariableDescriptor = Util.makeDescriptor(PathVariable::class)
  private val httpStatusDescriptor = Util.makeDescriptor(HttpStatus::class)
  private val functionalInterfaceDescriptor = Util.makeDescriptor(FunctionalInterface::class)
  private const val kotlinLambdaName = "kotlin/jvm/internal/Lambda"

  private fun parseMappingAnnotationPath(annotationValues: Map<String, Any>?): String {
    return (
      annotationValues?.let {
        Util.extractAnnotationValue(it, { annotationValue ->
          (annotationValue as ArrayList<*>).fold("") { accumulator, value ->
            joinPaths(accumulator, value as String)
          }
        }, RequestMapping::value.name, RequestMapping::path.name)
      }
    ) ?: ""
  }

  private fun parseMappingAnnotationConsumes(annotationValues: Map<String, Any>?): String {
    return (
      annotationValues?.let {
        Util.extractAnnotationValue(annotationValues, { annotationValue ->
          if (annotationValue !is ArrayList<*>)
            throw IllegalStateException("Expected a list of values")

          if (annotationValue.size > 1)
            throw IllegalStateException("Currently not handling multiple request content types")

          if (annotationValue.size == 0)
            return@extractAnnotationValue null

          return@extractAnnotationValue annotationValue[0] as String
        }, RequestMapping::consumes.name)
      }
    ) ?: MediaType.APPLICATION_JSON_VALUE
  }

  private fun resolveInputSource(annotationNodes: List<AnnotationNode>?): Pair<InputSource, String?> {
    if (annotationNodes != null) {
      for (argumentAnnotation in annotationNodes) {
        val argumentAnnotationValues = Util.parseAnnotationValues(argumentAnnotation)

        when (argumentAnnotation.desc) {
          pathVariableDescriptor -> {
            return Pair(
              InputSource.PATH,
              argumentAnnotationValues?.let {
                Util.extractAnnotationValue(
                  it,
                  { annotationValue -> annotationValue as String },
                  PathVariable::name.name, PathVariable::value.name
                )
              }
            )
          }
          requestParamDescriptor -> {
            return Pair(
              InputSource.PARAMETER,
              argumentAnnotationValues?.let {
                Util.extractAnnotationValue(
                  it,
                  { annotationValue -> annotationValue as String },
                  RequestParam::name.name, RequestParam::value.name
                )
              }
            )
          }
          requestBodyDescriptor -> {
            return Pair(InputSource.BODY, null)
          }
          requestPartDescriptor -> {
            return Pair(
              InputSource.BODY,
              argumentAnnotationValues?.let {
                Util.extractAnnotationValue(
                  it,
                  { annotationValue -> annotationValue as String },
                  RequestPart::name.name, RequestPart::value.name
                )
              }
            )
          }
          "Ljakarta/validation/Valid;" -> {
            // noop for now
          }
          else -> throw IllegalStateException("Encountered unknown argument annotation: ${argumentAnnotation.desc}")
        }
      }
    }

    // I *think* that this is the default, if not specified otherwise by an annotation
    return Pair(InputSource.PARAMETER, null)
  }

  private fun parseSuccessResponseCode(methodNode: MethodNode): HttpStatus {
    if (methodNode.visibleAnnotations != null) {
      for (annotation in methodNode.visibleAnnotations) {
        if (annotation.desc != responseStatusDescriptor)
          continue

        val annotationValues = Util.parseAnnotationValues(annotation) ?: continue
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

  private fun parseEndpoint(basePath: String, owner: JavaClassFile, methodNode: MethodNode, jar: JarContainer): EndpointMethod? {
    if (methodNode.visibleAnnotations == null)
      return null

    class MappingDetails(
      val method: RequestMethod,
      val path: String,
      val contentType: String,
    )

    var mappingDetails: MappingDetails? = null

    for (methodAnnotation in methodNode.visibleAnnotations) {
      val annotationValues = Util.parseAnnotationValues(methodAnnotation)

      mappingDetails = MappingDetails(
        requestMethodBySpecificDescriptor[methodAnnotation.desc] ?: continue,
        parseMappingAnnotationPath(annotationValues),
        parseMappingAnnotationConsumes(annotationValues)
      )
    }

    if (mappingDetails == null)
      return null

    val absoluteRequestPath = joinPaths(basePath, mappingDetails.path)
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
    val argumentAnnotationLists = methodNode.visibleParameterAnnotations ?: emptyArray()

    val argumentNames: List<String>?

    if (methodNode.localVariables == null)
      argumentNames = null
    else {
      var collectedArgumentNames: MutableList<String>? = null

      // GOSH, this is a hack! methodNode.parameters would always yield null...
      // It looks like everything after this are the parameters, if it works, it works, :^)
      for (localVariable in methodNode.localVariables) {
        val localVariableName = localVariable.name

        if (localVariableName == "this") {
          collectedArgumentNames = mutableListOf()
          continue
        }

        collectedArgumentNames?.add(localVariableName)
      }

      argumentNames = collectedArgumentNames
    }

    for (argumentTypeIndex in methodType.argumentTypes.indices) {
      val argumentType = methodType.argumentTypes[argumentTypeIndex]
      val builtInArgumentType = BuiltInType.getByDescriptor(argumentType.descriptor)

      val argumentAnnotations = argumentAnnotationLists.getOrNull(argumentTypeIndex)
      val (inputSource, customArgumentName) = resolveInputSource(argumentAnnotations)

      val argumentName = customArgumentName
        ?: (argumentNames?.getOrNull(argumentTypeIndex)
        ?: throw IllegalStateException("Could not resolve argument name for index $argumentTypeIndex"))

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

    val exceptionsThrown = recursivelyCollectThrownExceptions(jar, owner, methodNode)

    // TODO: Continue working on this
    if (exceptionsThrown.size > 0) {
      println("\nMethod ${methodNode.name} throws:")
      exceptionsThrown.forEach {
        print("${it.classFile.simpleName}: ")
        println(it.methodStack.joinToString { classMethod ->
          "${classMethod.containingClass.simpleName}#${classMethod.method.name}()"
        })
      }
    }

    return EndpointMethod(
      returnType,
      argumentTypes,
      mappingDetails.method,
      mappingDetails.contentType,
      absoluteRequestPath,
      parseSuccessResponseCode(methodNode)
    )
  }

  private fun recursivelyCollectThrownExceptions(
    jar: JarContainer,
    owner: JavaClassFile,
    methodNode: MethodNode
  ): HashSet<ThrownException> {
    val result = HashSet<ThrownException>()
    val visitedMethods = HashSet<String>()
    val methodStack = Stack<ClassMethod>()
    collectThrownExceptions(jar, owner, methodNode, methodStack, visitedMethods, result)
    return result
  }

  private fun handleMethodInvocationInstruction(
    ownerClass: JavaClassFile,
    desc: String,
    name: String,
    jar: JarContainer,
    methodStack: Stack<ClassMethod>,
    visitedMethods: MutableSet<String>,
    list: HashSet<ThrownException>
  ) {
    if (!jar.methodInvocationOwnerPaths.contains(ownerClass.classNode.name)) {
      logger.finest("ignore $ownerClass")
      return
    }

    val methodId = "$ownerClass:$name:$desc"

    if (!visitedMethods.add(methodId)) {
      logger.finest("seen $ownerClass")
      return
    }

    // Automatically visit the invoke of a lambda. It could be called at runtime, very likely even, and
    // so it's throw statements have to be collected as well.
    if (jar.doesExtend(ownerClass, kotlinLambdaName)) {
      ownerClass.tryFindMethod(jar, "invoke", null)?.let {
        handleMethodInvocationInstruction(ownerClass, it.desc, it.name, jar, methodStack, visitedMethods, list)
      }
    }

    if (ownerClass.classNode.access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) > 0) {
      // For now, at least, just skip functional interfaces
      if (ownerClass.classNode.visibleAnnotations?.any {
        it.desc == functionalInterfaceDescriptor
      } == true)
        return

      val ownerClassImplementations = jar.findTypesThatExtendMethodInvocationOwnerType(ownerClass)

      if (ownerClassImplementations.isEmpty())
        throw IllegalStateException("Found unimplemented but called interface/abstract class: $ownerClass")

      for (ownerClassImplementation in ownerClassImplementations)
        handleMethodInvocationInstruction(ownerClassImplementation, desc, name, jar, methodStack, visitedMethods, list)

      return
    }

    val targetMethod = ownerClass.tryFindMethod(jar, name, desc) ?: return

    collectThrownExceptions(jar, ownerClass, targetMethod, methodStack, visitedMethods, list)
  }

  private fun isValidOpcode(opcode: Int): Boolean {
    if (opcode > 255)
      throw IllegalStateException("Encountered opcode > 255")

    // Label node
    if (opcode == -1)
      return false

    if (opcode < 0)
      throw IllegalStateException("Encountered opcode < 0")

    return true
  }

  private fun addExceptionByNameIfInPaths(
    jar: JarContainer,
    name: String,
    methodStack: Stack<ClassMethod>,
    list: HashSet<ThrownException>,
  ) {
    if (!jar.methodInvocationOwnerPaths.contains(name))
      return

    val methodStackCopy = Stack<ClassMethod>()
    methodStackCopy.addAll(methodStack)

    list.add(ThrownException(
      jar.locateClassByPath(name),
      methodStackCopy
    ))
  }

  private fun collectThrownExceptions(
    jar: JarContainer,
    owner: JavaClassFile,
    methodNode: MethodNode,
    methodStack: Stack<ClassMethod>,
    visitedMethods: MutableSet<String>,
    list: HashSet<ThrownException>
  ) {
    methodStack.push(ClassMethod(owner, methodNode))
    logger.fine("enter $owner :: ${methodNode.name}: ${methodNode.desc}")

    for (instructionIndex in 0 until methodNode.instructions.size()) {
      val instruction = methodNode.instructions.get(instructionIndex)
      val opcode = instruction.opcode

      if (!isValidOpcode(opcode)) {
        logger.finest("invalid $opcode")
        continue
      }

      val opcodeName = opcodeNameByOpcode[instruction.opcode]
      logger.finest("op $opcodeName")

      // Zero operands instruction
      if (instruction is InsnNode) {
        if (opcode == Opcodes.ATHROW) {
          var previousInstruction: AbstractInsnNode
          var indexOffset = 1

          do {
            val previousIndex = instructionIndex - indexOffset

            if (previousIndex < 0)
              throw IllegalStateException("Could not locate valid instruction before throw")

            previousInstruction = methodNode.instructions[previousIndex]
            ++indexOffset
          } while (!isValidOpcode(previousInstruction.opcode))

          val previousInstructionOpcodeName = opcodeNameByOpcode[instruction.opcode]

          if (previousInstruction is MethodInsnNode) {
            if (previousInstruction.opcode == Opcodes.INVOKESPECIAL) {
              if (previousInstruction.name != "<init>")
                throw IllegalStateException("Expected constructor call previous to throw, but got ${previousInstruction.name}")
            }

            else if (previousInstruction.opcode == Opcodes.INVOKEVIRTUAL)
              addExceptionByNameIfInPaths(jar, Type.getMethodType(previousInstruction.desc).returnType.internalName, methodStack, list)

            else
              throw IllegalStateException("Unaccounted-for invocation opcode: $previousInstructionOpcodeName")

            addExceptionByNameIfInPaths(jar, previousInstruction.owner, methodStack, list)
            continue
          }

          if (previousInstruction is VarInsnNode) {
            val loadedVariable = methodNode.localVariables[previousInstruction.`var`]

            addExceptionByNameIfInPaths(jar, Type.getType(loadedVariable.desc).internalName, methodStack, list)
            continue
          }

          throw IllegalStateException("Unimplemented instruction previous to throw: $previousInstructionOpcodeName (${previousInstruction.opcode})")
        }

        continue
      }

      if (instruction is MethodInsnNode) {
        val ownerClass = jar.tryLocateClassByPath(instruction.owner) ?: continue
        handleMethodInvocationInstruction(ownerClass, instruction.desc, instruction.name, jar, methodStack, visitedMethods, list)
        continue
      }

      if (instruction is InvokeDynamicInsnNode) {
        val ownerClass = jar.tryLocateClassByPath(instruction.bsm.owner) ?: continue
        handleMethodInvocationInstruction(ownerClass, instruction.bsm.desc, instruction.bsm.name, jar, methodStack, visitedMethods, list)
        continue
      }
    }
    methodStack.pop()
    logger.fine("exit $owner :: ${methodNode.name}: ${methodNode.desc}")
  }

  private fun processController(javaClass: JavaClassFile, jar: JarContainer, endpoints: MutableList<EndpointMethod>) {
    var basePath: String? = null

    if (javaClass.classNode.visibleAnnotations != null) {
      for (methodAnnotation in javaClass.classNode.visibleAnnotations) {
        if (methodAnnotation.desc == requestMappingDescriptor) {
          val annotationValues = Util.parseAnnotationValues(methodAnnotation) ?: continue
          basePath = parseMappingAnnotationPath(annotationValues)
          break
        }
      }
    }

    if (basePath == null)
      return

    for (method in javaClass.classNode.methods) {
      if (method.access and Opcodes.ACC_PUBLIC != 0)
        endpoints.add(parseEndpoint(basePath, javaClass, method, jar) ?: continue)
    }
  }

  fun parseEndpoints(jar: JarContainer): List<EndpointMethod> {
    val endpoints = mutableListOf<EndpointMethod>()

    for (controllerPackagePath in jar.controllerPackagePaths) {
      for (entry in jar.classes) {
        if (!entry.key.startsWith(controllerPackagePath))
          continue

        processController(entry.value, jar, endpoints)
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
}