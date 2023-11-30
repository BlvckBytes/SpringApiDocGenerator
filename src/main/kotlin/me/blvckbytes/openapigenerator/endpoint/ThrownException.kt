package me.blvckbytes.openapigenerator.endpoint

import me.blvckbytes.openapigenerator.JavaClassFile
import org.objectweb.asm.tree.MethodNode
import java.util.Stack

class ThrownException(
  val classFile: JavaClassFile,
  val methodStack: Stack<ClassMethod>
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ThrownException
    return (classFile == other.classFile)
  }

  override fun hashCode(): Int {
    return classFile.hashCode()
  }
}