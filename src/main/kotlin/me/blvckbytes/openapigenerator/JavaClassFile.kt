package me.blvckbytes.openapigenerator

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

class JavaClassFile(
  private val name: String,
  private val bytes: ByteArray,
) {
  private var _classNode: ClassNode? = null

  // Most classes will not be analyzed any further, thus parsing should be lazy
  val classNode: ClassNode
  get() {
    val reader = ClassReader(bytes)
    _classNode = ClassNode()
    reader.accept(_classNode, 0)
    return _classNode!!
  }

  val simpleName: String
  get() {
    val name = classNode.name
    val lastDotIndex = name.lastIndexOf('/')
    return name.substring(lastDotIndex + 1)
  }

  override fun toString(): String {
    return name
  }
}