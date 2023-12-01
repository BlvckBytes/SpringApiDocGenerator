package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.logging.Logger

class VarInsnMatcher(
  override val optional: Boolean = false
) : InstructionMatcher {

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): Boolean {
    if (instruction !is VarInsnNode) {
      logger?.fine("${instruction.javaClass.simpleName} is not a VarInsnNode")
      return false
    }

    logger?.finest("matched VarInsnNode")
    return true
  }
}