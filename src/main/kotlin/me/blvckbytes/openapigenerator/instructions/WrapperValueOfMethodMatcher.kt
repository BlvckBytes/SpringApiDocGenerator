package me.blvckbytes.openapigenerator.instructions

class WrapperValueOfMethodMatcher(
  override val optional: Boolean = false
) : OrMatcher(
  BOOLEAN_VALUE_OF,
  INTEGER_VALUE_OF,
  LONG_VALUE_OF,
  FLOAT_VALUE_OF,
  DOUBLE_VALUE_OF,
) {

  companion object {
    private val BOOLEAN_VALUE_OF = MethodInsnMatcher(owner = "java/lang/Boolean", name = "valueOf", optional = true)
    private val INTEGER_VALUE_OF = MethodInsnMatcher(owner = "java/lang/Integer", name = "valueOf", optional = true)
    private val LONG_VALUE_OF = MethodInsnMatcher(owner = "java/lang/Long", name = "valueOf", optional = true)
    private val FLOAT_VALUE_OF = MethodInsnMatcher(owner = "java/lang/Float", name = "valueOf", optional = true)
    private val DOUBLE_VALUE_OF = MethodInsnMatcher(owner = "java/lang/Double", name = "valueOf", optional = true)
  }
}