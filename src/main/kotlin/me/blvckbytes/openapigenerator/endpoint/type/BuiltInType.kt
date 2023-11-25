package me.blvckbytes.openapigenerator.endpoint.type

import org.springframework.web.multipart.MultipartFile
import kotlin.reflect.KClass

enum class BuiltInType(val className: String) {
  UUID(java.util.UUID::class),
  MULTIPART_FILE(MultipartFile::class),
  ;

  constructor(type: KClass<*>) : this(type.qualifiedName!!)

  companion object {
    fun getByClassName(className: String): BuiltInType? {
      for (type in values()) {
        if (type.className == className)
          return type
      }
      return null
    }
  }
}