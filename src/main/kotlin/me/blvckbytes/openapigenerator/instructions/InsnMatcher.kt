package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.logging.Logger

class InsnMatcher(
  private val opcode: Int? = null,
  override val optional: Boolean = false
) : InstructionMatcher<InsnNode> {

  override var instruction: InsnNode? = null

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): InstructionMatcher<InsnNode>? {
    if (instruction !is InsnNode) {
      logger?.fine("${instruction.javaClass.simpleName} is not a VarInsnNode")
      return null
    }

    if (opcode != null && opcode != instruction.opcode) {
      logger?.fine("opcode ${instruction.opcode} != $opcode")
      return null
    }

    logger?.finest("matched VarInsnNode")
    this.instruction = instruction
    return this
  }
}