package me.blvckbytes.openapigenerator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.io.File

class JavaClassFile(
  private val name: String,
  private val bytes: ByteArray,
) {
  private var _classNode: ClassNode? = null

  // Most classes will not be analyzed any further, thus parsing should be lazy
  val classNode: ClassNode
  get() {
    if (_classNode != null)
      return _classNode!!

    _classNode = ClassNode()
    ClassReader(bytes).accept(_classNode, 0)

    return _classNode!!
  }

  val simpleName: String
  get() {
    val name = classNode.name
    val lastDotIndex = name.lastIndexOf('/')
    return name.substring(lastDotIndex + 1)
  }

  fun tryFindField(name: String, descriptor: String): FieldNode? {
    for (field in classNode.fields) {
      if (field.name == name && field.desc == descriptor)
        return field
    }
    return null
  }

  fun tryFindMethod(jar: JarContainer, name: String, descriptor: String?): MethodNode? {
    for (method in classNode.methods) {
      if ((descriptor == null || method.desc == descriptor) && method.name == name)
        return method
    }

    if (classNode.superName == null)
      return null

    if (!jar.methodInvocationOwnerPaths.contains(classNode.superName))
      return null

    return jar.tryLocateClassByPath(classNode.superName)?.tryFindMethod(jar, name, descriptor)
  }

  // A quite convenient helper while debugging.
  fun dumpTo(absolutePath: String) {
    val file = File(absolutePath)

    if (!file.exists())
      file.createNewFile()

    file.writeBytes(this.bytes)
  }

  override fun toString(): String {
    return name
  }

  override fun hashCode(): Int {
    return classNode.name.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as JavaClassFile
    return classNode.name == other.classNode.name
  }
}