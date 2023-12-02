package me.blvckbytes.openapigenerator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.io.File

class JavaClassFile(
  private val name: String,
  val bytes: ByteArray,
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

  fun findField(name: String, descriptor: String?): FieldNode {
    return tryFindField(name, descriptor)
      ?: throw IllegalStateException("Could not find field $name of class ${classNode.name}")
  }

  fun tryFindField(name: String, descriptor: String?): FieldNode? {
    for (field in classNode.fields) {
      if (field.name == name && (descriptor == null || field.desc == descriptor))
        return field
    }
    return null
  }

  fun isAbstractOrInterface(): Boolean {
    return classNode.access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) != 0
  }

  fun stripCompanion(jar: JarContainer): JavaClassFile {
    var realContainingClassName = classNode.name
    val companionSuffix = "\$Companion"

    var companionSuffixIndex: Int

    while (true) {
      companionSuffixIndex = realContainingClassName.indexOf(companionSuffix)

      if (companionSuffixIndex < 0)
        break

      realContainingClassName = realContainingClassName.substring(0, companionSuffixIndex)
    }

    return jar.locateClassByPath(realContainingClassName)
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