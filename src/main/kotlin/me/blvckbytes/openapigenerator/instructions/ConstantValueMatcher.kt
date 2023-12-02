package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import java.util.logging.Logger

class ConstantValueMatcher : OrMatcher(
  I_CONST_0_MATCHER,
  I_CONST_1_MATCHER,
  I_CONST_2_MATCHER,
  I_CONST_3_MATCHER,
  I_CONST_4_MATCHER,
  I_CONST_5_MATCHER,
  L_CONST_0_MATCHER,
  L_CONST_1_MATCHER,
  F_CONST_0_MATCHER,
  F_CONST_1_MATCHER,
  F_CONST_2_MATCHER,
  D_CONST_0_MATCHER,
  D_CONST_1_MATCHER,
  LDC_MATCHER
) {

  companion object {
    private val LDC_MATCHER = LdcInsnMatcher()
    private val I_CONST_0_MATCHER = InsnMatcher(Opcodes.ICONST_0)
    private val I_CONST_1_MATCHER = InsnMatcher(Opcodes.ICONST_1)
    private val I_CONST_2_MATCHER = InsnMatcher(Opcodes.ICONST_2)
    private val I_CONST_3_MATCHER = InsnMatcher(Opcodes.ICONST_3)
    private val I_CONST_4_MATCHER = InsnMatcher(Opcodes.ICONST_4)
    private val I_CONST_5_MATCHER = InsnMatcher(Opcodes.ICONST_5)
    private val L_CONST_0_MATCHER = InsnMatcher(Opcodes.LCONST_0)
    private val L_CONST_1_MATCHER = InsnMatcher(Opcodes.LCONST_0)
    private val F_CONST_0_MATCHER = InsnMatcher(Opcodes.FCONST_0)
    private val F_CONST_1_MATCHER = InsnMatcher(Opcodes.FCONST_1)
    private val F_CONST_2_MATCHER = InsnMatcher(Opcodes.FCONST_2)
    private val D_CONST_0_MATCHER = InsnMatcher(Opcodes.DCONST_0)
    private val D_CONST_1_MATCHER = InsnMatcher(Opcodes.DCONST_1)
  }

  var value: Any? = null
  override var instruction: AbstractInsnNode? = null

  override fun match(instruction: AbstractInsnNode, jar: JarContainer, logger: Logger?): InstructionMatcher<out AbstractInsnNode>? {
    val match = super.match(instruction, jar, logger) ?: return null

    value = when (match) {
      I_CONST_0_MATCHER -> 0
      I_CONST_1_MATCHER -> 1
      I_CONST_2_MATCHER -> 2
      I_CONST_3_MATCHER -> 3
      I_CONST_4_MATCHER -> 4
      I_CONST_5_MATCHER -> 5
      L_CONST_0_MATCHER -> 0.toLong()
      L_CONST_1_MATCHER -> 1.toLong()
      F_CONST_0_MATCHER -> 0.0.toFloat()
      F_CONST_1_MATCHER -> 1.0.toFloat()
      F_CONST_2_MATCHER -> 2.0.toFloat()
      D_CONST_0_MATCHER -> 0.0
      D_CONST_1_MATCHER -> 1.0
      LDC_MATCHER -> (instruction as LdcInsnNode).cst
      else -> throw IllegalStateException("Unimplemented match: ${match.javaClass.simpleName}")
    }

    return this
  }
}