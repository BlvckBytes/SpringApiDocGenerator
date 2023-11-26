package me.blvckbytes.openapigenerator

import org.objectweb.asm.tree.AnnotationNode
import kotlin.reflect.KClass

object Util {

  fun makeDescriptor(type: KClass<*>): String {
    return 'L' + type.qualifiedName!!.replace('.', '/') + ';'
  }

  fun <T> extractAnnotationValue(
    values: Map<String, Any>,
    mapper: (value: Any) -> T,
    vararg nameAndFallbackNames: String,
  ): T? {
    for (targetName in nameAndFallbackNames) {
      val targetValue = values[targetName] ?: continue
      return mapper(targetValue)
    }

    return null
  }

  fun parseAnnotationValues(annotation: AnnotationNode): Map<String, Any>? {
    if (annotation.values == null)
      return null

    val result = mutableMapOf<String, Any>()

    for (i in annotation.values.indices step 2) {
      val valueName = annotation.values[i]
      val valueValue = annotation.values[i + 1]

      result[valueName as String] = valueValue
    }

    return result
  }
}