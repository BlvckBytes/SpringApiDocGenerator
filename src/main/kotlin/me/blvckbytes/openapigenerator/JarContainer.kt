package me.blvckbytes.openapigenerator

class JarContainer(
  private val classFileByPath: Map<String, JavaClassFile>
) {
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
}