package me.blvckbytes.openapigenerator

class JarContainerClassLoader(
  val jar: JarContainer,
  parent: ClassLoader
) : ClassLoader(parent) {

  override fun findClass(name: String?): Class<*> {
    try {
      return parent.loadClass(name)
    } catch (_: ClassNotFoundException) {}

    if (name == null)
      throw IllegalStateException("Cannot load a class with no name")

    val file = jar.tryLocateClassByPath(name.replace('.', '/'))
      ?: throw ClassNotFoundException("Could not locate class $name in jar container")

    return defineClass(name, file.bytes, 0, file.bytes.size)
  }
}