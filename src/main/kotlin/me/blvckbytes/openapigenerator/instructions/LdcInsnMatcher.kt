package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import java.util.logging.Logger

class LdcInsnMatcher(
  override val optional: Boolean = false
) : InstructionMatcher<LdcInsnNode> {

  override var instruction: LdcInsnNode? = null

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): InstructionMatcher<LdcInsnNode>? {
    if (instruction !is LdcInsnNode) {
      logger?.fine("${instruction.javaClass.simpleName} is not a LdcInsnNode")
      return null
    }

    logger?.finest("matched LdcInsnNode")
    this.instruction = instruction
    return this
  }
}