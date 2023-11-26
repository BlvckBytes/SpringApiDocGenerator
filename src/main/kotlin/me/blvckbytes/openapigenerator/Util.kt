package me.blvckbytes.openapigenerator

import kotlin.reflect.KClass

object Util {

  fun makeDescriptor(type: KClass<*>): String {
    return 'L' + type.qualifiedName!!.replace('.', '/') + ';'
  }
}