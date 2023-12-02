package me.blvckbytes.openapigenerator.instructions

import me.blvckbytes.openapigenerator.JarContainer
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.StringJoiner
import java.util.logging.Logger
import kotlin.reflect.KClass

class InstructionsParser(
  private val instructions: InsnList,
  private val targetIndices: IntProgression,
  private val jar: JarContainer,
  private val logger: Logger? = null
) {

  companion object {
    private val opcodeNameByOpcode = arrayOfNulls<String>(0xFF)

    init {
      val ignorePrefixes = listOf(
        "ASM", "H_", "T_", "F_", "V_", "ACC_",
        "V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9",
      )

      val ignoreEquals = listOf(
        "TOP", "INTEGER", "FLOAT", "DOUBLE", "LONG", "NULL", "UNINITIALIZED_THIS",
        "SOURCE_MASK", "SOURCE_DEPRECATED"
      )

      for (field in Opcodes::class.java.declaredFields) {
        if (field.type.simpleName != "int")
          continue

        val name = field.name

        if (ignorePrefixes.any{ name.startsWith(it) })
          continue

        if (ignoreEquals.any{ name == it })
          continue

        opcodeNameByOpcode[field.get(null) as Int] = field.name
      }
    }

    fun resolveOpcode(opcode: Int): String? {
      if (opcode < 0 || opcode >= 255)
        return null

      return opcodeNameByOpcode[opcode]
    }
  }

  private val ignoredInstructions = mutableSetOf<KClass<out AbstractInsnNode>>()

  fun ignoreInstructions(vararg instructions: KClass<out AbstractInsnNode>): InstructionsParser {
    ignoredInstructions.addAll(instructions)
    return this
  }

  fun matchSequence(vararg matchers: InstructionMatcher<*>): List<InstructionMatcher<*>>? {
    val matches = mutableListOf<InstructionMatcher<*>>()
    var instructionIndex = targetIndices.first

    for (matcher in matchers) {
      var instruction: AbstractInsnNode

      while (true) {
        if (targetIndices.step > 0) {
          if (instructionIndex < targetIndices.first || instructionIndex > targetIndices.last)
            return null
        } else {
          if (instructionIndex > targetIndices.first || instructionIndex < targetIndices.last)
            return null
        }

        logger?.finest("index=$instructionIndex")

        instruction = instructions.get(instructionIndex)
        val isIgnored = ignoredInstructions.any { it.isInstance(instruction) }

        if (!isIgnored)
          break

        logger?.finest("ignore")
        instructionIndex += targetIndices.step
      }

      val match = matcher.match(instruction, jar, logger)

      if (match != null) {
        matches.add(match)
        instructionIndex += targetIndices.step
        continue
      }

      if (matcher.optional) {
        logger?.finest("previous matcher was optional, continuing without index increment")
        continue
      }

      throw IllegalStateException("A matcher could not find a match")
    }

    if (matches.size != matchers.size)
      throw IllegalStateException("Expected result to be of same size as number of matchers are")

    return matches
  }

  fun stringifyInstructions(): String {
    val builder = StringJoiner("\n", "[\n", "\n]")

    for (instructionIndex in 0 until instructions.size()) {
      val instruction = instructions[instructionIndex]

      if (ignoredInstructions.any { it.isInstance(instruction) })
        continue

      builder.add(when(instruction) {
        is LdcInsnNode ->    "$instructionIndex\tLDC\t${instruction.cst}"
        is VarInsnNode ->    "$instructionIndex\tLDV\t${instruction.`var`}"
        is FieldInsnNode ->  "$instructionIndex\tLDF\t${instruction.owner}#${instruction.name}"
        is TypeInsnNode ->   "$instructionIndex\tCAST\t${instruction.desc}"
        is LabelNode ->      "$instructionIndex\tLBL"
        is MethodInsnNode -> "$instructionIndex\tCALL\t${instruction.owner}#${instruction.name} desc ${instruction.desc}"
        is LineNumberNode -> "$instructionIndex\tLINE\t${instruction.line}"
        is InsnNode ->       "$instructionIndex\tOP\t${resolveOpcode(instruction.opcode)}"
        else -> "Unimplemented: ${instruction.javaClass.simpleName}"
      })
    }

    return builder.toString()
  }
}