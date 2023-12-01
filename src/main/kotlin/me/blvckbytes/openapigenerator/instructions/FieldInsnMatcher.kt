package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import java.util.logging.Logger

class FieldInsnMatcher(
  private val owner: String? = null,
  private val name: String? = null,
  private val desc: String? = null,
  private val isStatic: Boolean? = null,
  override val optional: Boolean = false
) : InstructionMatcher {

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): Boolean {
    if (instruction !is FieldInsnNode) {
      logger?.fine("${instruction.javaClass.simpleName} is not a FieldInsnNode")
      return false
    }

    if (owner != null && instruction.owner != owner) {
      logger?.fine("field owner ${instruction.owner} != $owner")
      return false
    }

    if (name != null && instruction.name != name) {
      logger?.fine("field name ${instruction.name} != $name")
      return false
    }

    if (desc != null && instruction.desc != desc) {
      logger?.fine("field desc ${instruction.desc} != $desc")
      return false
    }

    if (isStatic != null && isStaticField(jar, instruction) != isStatic) {
      logger?.fine("field static $isStatic mismatch")
      return false
    }

    logger?.finest("matched FieldInsnNode")
    return true
  }

  private fun isStaticField(jar: JarContainer, instruction: FieldInsnNode): Boolean {
    return jar.tryLocateClassByPath(instruction.owner)
      ?.tryFindField(instruction.name, instruction.desc)
      ?.let { it.access and Opcodes.ACC_STATIC != 0 }
      ?: false
  }
}