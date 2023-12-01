package me.blvckbytes.openapigenerator.util

import org.objectweb.asm.tree.AnnotationNode
import kotlin.reflect.KClass

object Util {

  private val descriptorByClass = mutableMapOf<KClass<*>, String>()
  private val internalNameByClass = mutableMapOf<KClass<*>, String>()

  fun makeDescriptor(type: KClass<*>): String {
    return internalNameByClass.computeIfAbsent(type) { 'L' + makeName(type) + ';' }
  }

  fun makeName(type: KClass<*>): String {
    return descriptorByClass.computeIfAbsent(type) { type.qualifiedName!!.replace('.', '/') }
  }

  fun <T> extractAnnotationValue(
    values: Map<String, Any>,
    mapper: (value: Any) -> T?,
    vararg nameAndFallbackNames: String,
  ): T? {
    for (targetName in nameAndFallbackNames) {
      val targetValue = values[targetName] ?: continue
      return mapper(targetValue) ?: continue
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