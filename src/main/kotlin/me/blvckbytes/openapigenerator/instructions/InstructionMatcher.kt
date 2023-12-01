package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import java.util.logging.Logger

interface InstructionMatcher {
  val optional: Boolean

  fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): Boolean
}