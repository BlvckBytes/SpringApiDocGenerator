package me.blvckbytes.openapigenerator.endpoint.type

import me.blvckbytes.openapigenerator.util.Util
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.*

class BuiltInType private constructor(
  val name: String,
  private vararg val descriptors: String
) {

  companion object {
    private val constantByDescriptor = mutableMapOf<String, BuiltInType>()

    val TYPE_UUID            = BuiltInType("UUID",            Util.makeDescriptor(UUID::class))
    val TYPE_MULTIPART_FILE  = BuiltInType("MULTIPART_FILE",  Util.makeDescriptor(MultipartFile::class))
    val TYPE_LOCAL_DATE_TIME = BuiltInType("LOCAL_DATE_TIME", Util.makeDescriptor(LocalDateTime::class))
    val TYPE_STRING          = BuiltInType("STRING",          "Ljava/lang/String;")
    val TYPE_INTEGER         = BuiltInType("INTEGER",         "I", "Ljava/lang/Integer;")
    val TYPE_LONG            = BuiltInType("LONG",            "J", "Ljava/lang/Long;")
    val TYPE_DOUBLE          = BuiltInType("DOUBLE",          "D", "Ljava/lang/Double;")
    val TYPE_FLOAT           = BuiltInType("FLOAT",           "F", "Ljava/lang/Float;")
    val TYPE_BYTE            = BuiltInType("BYTE",            "B", "Ljava/lang/Byte;")
    val TYPE_CHAR            = BuiltInType("CHAR",            "C", "Ljava/lang/Character;")
    val TYPE_SHORT           = BuiltInType("SHORT",           "S", "Ljava/lang/Short;")
    val TYPE_BOOLEAN         = BuiltInType("BOOLEAN",         "Z", "Ljava/lang/Boolean;")

    fun getByDescriptor(descriptor: String): BuiltInType? {
      return constantByDescriptor[descriptor]
    }
  }

  init {
    for (descriptor in descriptors) {
      if (constantByDescriptor.put(descriptor, this) != null)
        throw IllegalStateException("Duplicate descriptor $descriptor")
    }
  }

  override fun toString(): String {
    return name
  }

  fun descriptor(): String {
    return descriptors[0]
  }
}