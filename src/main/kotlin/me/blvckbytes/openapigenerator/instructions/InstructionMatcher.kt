package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.tree.AbstractInsnNode
import java.util.logging.Logger

interface InstructionMatcher<T : AbstractInsnNode> {
  val optional: Boolean

  var instruction: T?

  fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): InstructionMatcher<out T>?
}