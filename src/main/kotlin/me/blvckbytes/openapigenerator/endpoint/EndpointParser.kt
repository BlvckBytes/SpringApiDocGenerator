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
import me.blvckbytes.propertyvalidation.validatior.ApplicableValidator
import me.blvckbytes.propertyvalidation.validatior.NotNull
import me.blvckbytes.propertyvalidation.validatior.NotNullAndNotBlank
import me.blvckbytes.propertyvalidation.validatior.NullOrNotBlank
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.StringConcatFactory
import java.util.Stack
import java.util.logging.*
import kotlin.collections.ArrayList
import kotlin.jvm.internal.PropertyReference

class EndpointParser(
  private val jar: JarContainer
) {
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

  private val kotlinLambdaName = "kotlin/jvm/internal/Lambda"

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
          Util.makeDescriptor(PathVariable::class) -> {
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
          Util.makeDescriptor(RequestParam::class) -> {
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
          Util.makeDescriptor(RequestBody::class) -> {
            return Pair(InputSource.BODY, null)
          }
          Util.makeDescriptor(RequestPart::class) -> {
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
    val httpStatusDescriptor = Util.makeDescriptor(HttpStatus::class)

    if (methodNode.visibleAnnotations != null) {
      for (annotation in methodNode.visibleAnnotations) {
        if (annotation.desc != Util.makeDescriptor(ResponseStatus::class))
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

  private fun parseReturnGenerics(methodNode: MethodNode): Array<JavaClassFile>? {
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

  private fun parseEndpoint(basePath: String, owner: JavaClassFile, methodNode: MethodNode): EndpointMethod? {
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
          parseReturnGenerics(methodNode)
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

    val exceptionsThrown = recursivelyCollectThrownExceptions(owner, methodNode)

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
    owner: JavaClassFile,
    methodNode: MethodNode
  ): HashSet<ThrownException> {
    val result = HashSet<ThrownException>()
    val visitedMethods = HashSet<String>()
    val methodStack = Stack<ClassMethod>()
    collectThrownExceptions(owner, methodNode, methodStack, visitedMethods, result)
    return result
  }

  private fun handleMethodInvocationInstruction(
    ownerClass: JavaClassFile,
    desc: String,
    name: String,
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
        handleMethodInvocationInstruction(ownerClass, it.desc, it.name, methodStack, visitedMethods, list)
      }
    }

    if (ownerClass.classNode.access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) > 0) {
      // For now, at least, just skip functional interfaces
      if (ownerClass.classNode.visibleAnnotations?.any {
        it.desc == Util.makeDescriptor(FunctionalInterface::class)
      } == true)
        return

      val ownerClassImplementations = jar.findTypesThatExtendMethodInvocationOwnerType(ownerClass)

      if (ownerClassImplementations.isEmpty())
        throw IllegalStateException("Found unimplemented but called interface/abstract class: $ownerClass")

      for (ownerClassImplementation in ownerClassImplementations)
        handleMethodInvocationInstruction(ownerClassImplementation, desc, name, methodStack, visitedMethods, list)

      return
    }

    val targetMethod = ownerClass.tryFindMethod(jar, name, desc) ?: return

    collectThrownExceptions(ownerClass, targetMethod, methodStack, visitedMethods, list)
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

  private fun parseKotlinPropertyReference(javaClass: JavaClassFile): FieldReference {
    val propertyReferenceName = Util.makeName(PropertyReference::class)

    if (!jar.doesExtend(javaClass, propertyReferenceName))
      throw IllegalStateException("$javaClass is not a property reference")

    val constructor = javaClass.classNode.methods.firstOrNull { it.name == "<init>" }
      ?: throw IllegalStateException("Could not locate constructor of $javaClass")

    for (constructorInstructionIndex in 0 until constructor.instructions.size()) {
      val constructorInstruction = constructor.instructions[constructorInstructionIndex]

      if (constructorInstruction !is MethodInsnNode)
        continue

      if (constructorInstruction.name != "<init>")
        continue

      val methodOwner = jar.locateClassByPath(constructorInstruction.owner)

      if (!jar.doesExtend(methodOwner, propertyReferenceName))
        continue

      val methodType = Type.getMethodType(constructorInstruction.desc)
      val args = methodType.argumentTypes

      if (args.size != 4)
        throw IllegalStateException("Case of argument count being ${args.size} is not yet implemented")

      // Member-containing class
      if (args[0].internalName != Util.makeName(Class::class))
        throw IllegalStateException("Expected first argument to be of type Class")

      // Property name
      if (args[1].descriptor != BuiltInType.TYPE_STRING.descriptor())
        throw IllegalStateException("Expected second argument to be of type String")

      // Property getter "descriptor"
      if (args[2].descriptor != BuiltInType.TYPE_STRING.descriptor())
        throw IllegalStateException("Expected third argument to be of type String")

      // Flag bits
      if (args[3].descriptor != "I")
        throw IllegalStateException("Expected fourth argument to be of type int")

      val propertyNameInstruction = constructor.instructions[constructorInstructionIndex - 3]
      val containingClassInstruction = constructor.instructions[constructorInstructionIndex - 4]

      if (propertyNameInstruction !is LdcInsnNode)
        throw IllegalStateException("Expected property-name to be a constant")

      if (propertyNameInstruction.cst !is String)
        throw IllegalStateException("Expected property-name to be a constant String")

      if (containingClassInstruction !is LdcInsnNode)
        throw IllegalStateException("Expected containing-class to be a constant")

      if (containingClassInstruction.cst !is Type)
        throw IllegalStateException("Expected containing-class to be a constant Type")

      return FieldReference(
        jar.locateClassByPath((containingClassInstruction.cst as Type).internalName),
        propertyNameInstruction.cst as String
      )
    }

    throw IllegalStateException("Could not parse a kotlin property reference: $javaClass")
  }

  private fun parseValidatorConstructorInstruction(
    methodNode: MethodNode,
    ownerClass: JavaClassFile,
    currentInstructionIndex: Int
  ) {
    // TODO: CompareToConstant, CompareToMinMax, CompareToOther
    // constructor(field: KProperty, fieldValue: Any?)
    if (
      ownerClass.classNode.name == Util.makeName(NotNull::class) ||
      ownerClass.classNode.name == Util.makeName(NotNullAndNotBlank::class) ||
      ownerClass.classNode.name == Util.makeName(NullOrNotBlank::class)
    ) {
      for (instructionIndex in currentInstructionIndex - 1 downTo 0) {
        val instruction = methodNode.instructions.get(instructionIndex)
        val opcode = instruction.opcode

        if (!isValidOpcode(opcode))
          continue

        if (opcode == Opcodes.CHECKCAST)
          continue

        if (instruction is FieldInsnNode) {
          if (instruction.name != "INSTANCE")
            continue

          val fieldContainer = jar.locateClassByPath(instruction.owner)

          val targetField = fieldContainer.classNode.fields.firstOrNull {
            it.name == instruction.name && it.desc == instruction.desc
          } ?: throw IllegalStateException("Could not locate field ${instruction.name} of $fieldContainer")

          if (targetField.access and Opcodes.ACC_STATIC == 0)
            throw IllegalStateException("Expected $fieldContainer#INSTANCE to be a static field")

          val (containingClass, propertyName) = parseKotlinPropertyReference(fieldContainer)
          println("${ownerClass.simpleName}: ${containingClass.simpleName}#$propertyName")
        }
      }

      return
    }
  }

  private fun collectThrownExceptions(
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
              addExceptionByNameIfInPaths(Type.getMethodType(previousInstruction.desc).returnType.internalName, methodStack, list)

            else
              throw IllegalStateException("Unaccounted-for invocation opcode: $previousInstructionOpcodeName")

            addExceptionByNameIfInPaths(previousInstruction.owner, methodStack, list)
            continue
          }

          if (previousInstruction is VarInsnNode) {
            val loadedVariable = methodNode.localVariables[previousInstruction.`var`]

            addExceptionByNameIfInPaths(Type.getType(loadedVariable.desc).internalName, methodStack, list)
            continue
          }

          throw IllegalStateException("Unimplemented instruction previous to throw: $previousInstructionOpcodeName (${previousInstruction.opcode})")
        }

        continue
      }

      if (instruction is MethodInsnNode) {
        val ownerClass = jar.tryLocateClassByPath(instruction.owner) ?: continue

        if (instruction.name == "<init>" && jar.doesExtend(ownerClass, Util.makeName(ApplicableValidator::class)))
          parseValidatorConstructorInstruction(methodNode, ownerClass, instructionIndex)

        handleMethodInvocationInstruction(ownerClass, instruction.desc, instruction.name, methodStack, visitedMethods, list)
        continue
      }

      if (instruction is InvokeDynamicInsnNode) {
        val lambdaMetafactoryName = Util.makeName(LambdaMetafactory::class)
        val lambdaMetafactoryFunctionName = LambdaMetafactory::metafactory.name

        if (instruction.bsm.owner == lambdaMetafactoryName) {
          if (instruction.bsm.name != lambdaMetafactoryFunctionName)
            throw IllegalStateException("Expected $lambdaMetafactoryName#$lambdaMetafactoryFunctionName to be called, but found ${instruction.bsm.name}")

          if (instruction.bsmArgs == null)
            throw IllegalStateException("BSM received no arguments")

          var targetHandle: Handle? = null

          for (bsmArg in instruction.bsmArgs) {
            if (bsmArg is Handle) {
              if (targetHandle != null)
                throw IllegalStateException("Found multiple BSM arguments of type Handle")

              targetHandle = bsmArg
            }
          }

          if (targetHandle == null)
            throw IllegalStateException("Found no BSM argument of type Handle")

          val ownerClass = jar.tryLocateClassByPath(targetHandle.owner) ?: continue
          handleMethodInvocationInstruction(ownerClass, targetHandle.desc, targetHandle.name, methodStack, visitedMethods, list)
          continue
        }

        // Ignored, for now at least
        if (instruction.name == StringConcatFactory::makeConcatWithConstants.name)
          continue

        throw IllegalStateException("Did not account for dynamic invocation: ${instruction.name} ${instruction.desc}")
      }
    }
    methodStack.pop()
    logger.fine("exit $owner :: ${methodNode.name}: ${methodNode.desc}")
  }

  private fun processController(javaClass: JavaClassFile, endpoints: MutableList<EndpointMethod>) {
    var basePath: String? = null

    if (javaClass.classNode.visibleAnnotations != null) {
      for (methodAnnotation in javaClass.classNode.visibleAnnotations) {
        if (methodAnnotation.desc == Util.makeDescriptor(RequestMapping::class)) {
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
        endpoints.add(parseEndpoint(basePath, javaClass, method) ?: continue)
    }
  }

  fun parseEndpoints(): List<EndpointMethod> {
    val endpoints = mutableListOf<EndpointMethod>()

    for (controllerPackagePath in jar.controllerPackagePaths) {
      for (entry in jar.classes) {
        if (!entry.key.startsWith(controllerPackagePath))
          continue

        processController(entry.value, endpoints)
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