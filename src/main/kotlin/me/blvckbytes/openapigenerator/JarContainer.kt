package me.blvckbytes.openapigenerator

class JarContainer(
  private val classFileByPath: Map<String, JavaClassFile>,
  controllerPredicate: (classFilePath: String) -> Boolean,
  returnTypePredicate: (classFilePath: String) -> Boolean,
) {

  private val returnTypeClassFileByPath: Map<String, JavaClassFile>

  val controllerPackagePaths: List<String>
  private val returnTypePackagePaths: List<String>

  init {
    controllerPackagePaths = classFileByPath.keys.filter(controllerPredicate).toList()
    returnTypePackagePaths = classFileByPath.keys.filter(returnTypePredicate).toList()

    returnTypeClassFileByPath = classFileByPath.filter {
      for (returnTypePackage in returnTypePackagePaths) {
        if (it.key.startsWith(returnTypePackage))
          return@filter true
      }
      false
    }
  }

  val classes: Collection<Map.Entry<String, JavaClassFile>>
  get() = classFileByPath.entries

  fun locateClassByPath(path: String): JavaClassFile {
    return classFileByPath[path]
      ?: throw IllegalStateException("Could not locate class by path $path")
  }

  fun locateClassByDescriptor(descriptor: String): JavaClassFile {
    val descriptorLength = descriptor.length

    if (!(descriptor.startsWith('L') && descriptor[descriptorLength - 1] == ';'))
      throw IllegalStateException("Invalid class descriptor: $descriptor")

    return locateClassByPath(descriptor.substring(1, descriptorLength - 1))
  }

  fun findTypesThatExtendReturnType(returnType: JavaClassFile): List<JavaClassFile> {
    val result = mutableListOf<JavaClassFile>()

    for (returnTypeItem in returnTypeClassFileByPath.values) {
      if (doesExtend(returnTypeItem, returnType))
        result.add(returnTypeItem)
    }

    return result
  }

  private fun doesExtend(type: JavaClassFile, superClass: JavaClassFile): Boolean {
    if (type == superClass)
      return true

    if (type.classNode.superName != null) {
      val typeSuperClass = classFileByPath[type.classNode.superName]
      if (typeSuperClass != null && doesExtend(typeSuperClass, superClass))
        return true
    }

    if (type.classNode.interfaces != null) {
      for (typeInterface in type.classNode.interfaces) {
        val typeInterfaceClass = classFileByPath[typeInterface]
        if (typeInterfaceClass != null && doesExtend(typeInterfaceClass, superClass))
          return true
      }
    }

    return false
  }
}