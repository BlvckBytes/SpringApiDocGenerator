package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import java.util.logging.Logger

open class OrMatcher(
  private vararg val matchers: InstructionMatcher<out AbstractInsnNode>,
  override val optional: Boolean = false
) : InstructionMatcher<AbstractInsnNode> {

  override var instruction: AbstractInsnNode? = null

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): InstructionMatcher<out AbstractInsnNode>? {
    for (matcher in matchers) {
      val match = matcher.match(instruction, jar, logger)

      if (match != null) {
        this.instruction = instruction
        return match
      }

      continue
    }

    return null
  }
}