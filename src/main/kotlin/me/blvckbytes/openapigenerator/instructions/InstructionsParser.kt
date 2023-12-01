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
import kotlin.math.abs
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

  fun matchSequence(vararg matchers: InstructionMatcher): List<AbstractInsnNode>? {

    val realIndexByFilteredIndex = mutableMapOf<Int, Int>()

    val filteredInstructions = buildList<AbstractInsnNode> {
      for (instructionIndex in targetIndices) {
        val instruction = instructions.get(instructionIndex)

        if (ignoredInstructions.none { it.isInstance(instruction) }) {
          realIndexByFilteredIndex[size] = instructionIndex
          add(instruction)
        }
      }
    }

    val numberOfFilteredInstructions = filteredInstructions.size
    val currentInstructions = mutableListOf<AbstractInsnNode>()


    offsetLoop@ for (instructionIndex in filteredInstructions.indices) {
      currentInstructions.clear()

      var numberOfSkippedOptionals = 0

      matcherLoop@ for (matcherIndex in matchers.indices) {
        val matcher = matchers[matcherIndex]

        val currentInstructionIndex = instructionIndex + matcherIndex - numberOfSkippedOptionals

        if (currentInstructionIndex < 0 || currentInstructionIndex >= numberOfFilteredInstructions)
          return null

        logger?.finest("index=${realIndexByFilteredIndex[currentInstructionIndex]}")

        val currentInstruction = filteredInstructions[currentInstructionIndex]

        if (!matcher.match(currentInstruction, jar, logger)) {
          if (matcher.optional) {
            logger?.finest("previous matcher was optional, continuing")
            ++numberOfSkippedOptionals
            continue@matcherLoop
          }

          else {
            if (logger != null)
              println()

            continue@offsetLoop
          }
        }

        currentInstructions.add(currentInstruction)
      }

      if (currentInstructions.size != matchers.size)
        throw IllegalStateException("Expected result to be of same size as number of matchers are")

      return currentInstructions
    }

    return null
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