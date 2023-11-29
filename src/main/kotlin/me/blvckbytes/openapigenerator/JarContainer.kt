package me.blvckbytes.openapigenerator

class JarContainer(
  private val classFileByPath: Map<String, JavaClassFile>,
  methodInvocationOwnerPredicate: (classFilePath: String) -> Boolean,
  controllerPredicate: (classFilePath: String) -> Boolean,
  returnTypePredicate: (classFilePath: String) -> Boolean,
) {

  private val returnTypeClassFiles: List<JavaClassFile>
  private val methodInvocationOwnerClassFiles: List<JavaClassFile>

  val controllerPackagePaths: HashSet<String>
  val methodInvocationOwnerPaths: HashSet<String>
  private val returnTypePackagePaths: HashSet<String>

  init {
    controllerPackagePaths = classFileByPath.keys.filter(controllerPredicate).toHashSet()
    returnTypePackagePaths = classFileByPath.keys.filter(returnTypePredicate).toHashSet()
    methodInvocationOwnerPaths = classFileByPath.keys.filter(methodInvocationOwnerPredicate).toHashSet()

    returnTypeClassFiles = filterClassFilesByPaths(returnTypePackagePaths)
    methodInvocationOwnerClassFiles = filterClassFilesByPaths(methodInvocationOwnerPaths)
  }

  private fun filterClassFilesByPaths(paths: HashSet<String>): List<JavaClassFile> {
    val result = mutableListOf<JavaClassFile>()

    for (classEntry in classFileByPath) {
      for (path in paths) {
        if (classEntry.key.startsWith(path))
          result.add(classEntry.value)
      }
    }

    return result
  }

  val classes: Collection<Map.Entry<String, JavaClassFile>>
  get() = classFileByPath.entries

  fun locateClassByPath(path: String): JavaClassFile {
    return classFileByPath[path]
      ?: throw IllegalStateException("Could not locate class by path $path")
  }

  fun tryLocateClassByPath(path: String): JavaClassFile? {
    return classFileByPath[path]
  }

  fun locateClassByDescriptor(descriptor: String): JavaClassFile {
    return tryLocateClassByDescriptor(descriptor)
      ?: throw IllegalStateException("Could not locate class by descriptor $descriptor")
  }

  fun tryLocateClassByDescriptor(descriptor: String): JavaClassFile? {
    val descriptorLength = descriptor.length

    if (!(descriptor.startsWith('L') && descriptor[descriptorLength - 1] == ';'))
      return null

    return classFileByPath[descriptor.substring(1, descriptorLength - 1)]
  }

  fun findTypesThatExtendReturnType(returnType: JavaClassFile): List<JavaClassFile> {
    return findTypesThatExtendFromItems(returnType, returnTypeClassFiles)
  }

  fun findTypesThatExtendMethodInvocationOwnerType(ownerType: JavaClassFile): List<JavaClassFile> {
    return findTypesThatExtendFromItems(ownerType, methodInvocationOwnerClassFiles)
  }

  private fun findTypesThatExtendFromItems(type: JavaClassFile, items: Collection<JavaClassFile>): List<JavaClassFile> {
    val result = mutableListOf<JavaClassFile>()

    for (item in items) {
      if (item == type)
        continue

      if (doesExtend(item, type.classNode.name))
        result.add(item)
    }

    return result
  }

  fun doesExtend(type: JavaClassFile, superName: String): Boolean {
    if (type.classNode.name == superName)
      return true

    if (type.classNode.superName != null) {
      val typeSuperClass = classFileByPath[type.classNode.superName]
      if (typeSuperClass != null && doesExtend(typeSuperClass, superName))
        return true
    }

    if (type.classNode.interfaces != null) {
      for (typeInterface in type.classNode.interfaces) {
        val typeInterfaceClass = classFileByPath[typeInterface]
        if (typeInterfaceClass != null && doesExtend(typeInterfaceClass, superName))
          return true
      }
    }

    return false
  }
}