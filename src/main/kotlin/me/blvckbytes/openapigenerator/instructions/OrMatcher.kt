package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.logging.Logger

class OrMatcher(
  private vararg val matchers: InstructionMatcher,
  override val optional: Boolean = false
) : InstructionMatcher {

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): Boolean {
    if (!matchers.any { it.match(instruction, jar, logger) }) {
      logger?.finest("non of the ORed matchers matched")
      return false
    }

    return true
  }
}