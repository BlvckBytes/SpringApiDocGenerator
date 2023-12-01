package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.logging.Logger

class InsnMatcher(
  private val opcode: Int? = null,
  override val optional: Boolean = false
) : InstructionMatcher {

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): Boolean {
    if (instruction !is InsnNode) {
      logger?.fine("${instruction.javaClass.simpleName} is not a VarInsnNode")
      return false
    }

    if (opcode != null && opcode != instruction.opcode) {
      logger?.fine("opcode ${instruction.opcode} != $opcode")
      return false
    }

    logger?.finest("matched VarInsnNode")
    return true
  }
}