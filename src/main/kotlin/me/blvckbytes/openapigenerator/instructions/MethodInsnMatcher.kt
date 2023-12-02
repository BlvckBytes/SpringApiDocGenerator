package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.logging.Logger

class MethodInsnMatcher(
  private val owner: String? = null,
  private val name: String? = null,
  private val desc: String? = null,
  override val optional: Boolean = false
) : InstructionMatcher<MethodInsnNode> {

  override var instruction: MethodInsnNode? = null

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): InstructionMatcher<MethodInsnNode>? {
    if (instruction !is MethodInsnNode) {
      logger?.fine("${instruction.javaClass.simpleName} is not a MethodInsnNode")
      return null
    }

    if (owner != null && instruction.owner != owner) {
      logger?.fine("method owner ${instruction.owner} != $owner")
      return null
    }

    if (name != null && instruction.name != name) {
      logger?.fine("method name ${instruction.name} != $name")
      return null
    }

    if (desc != null && instruction.desc != desc) {
      logger?.fine("method desc ${instruction.desc} != $desc")
      return null
    }

    logger?.finest("matched MethodInsnNode")
    this.instruction = instruction
    return this
  }
}