package me.blvckbytes.openapigenerator.util

import org.codehaus.jettison.json.JSONObject

class JsonObjectBuilder private constructor(jsonObject: JSONObject? = null) {

  companion object {
    fun fromKeyOrCreate(jsonObject: JSONObject, key: String, handler: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
      if (jsonObject.has(key)) {
        val existingValue = jsonObject.get(key)

        if (existingValue !is JSONObject)
          throw IllegalStateException("Cannot extend the non-object $key of type ${existingValue.javaClass}")

        return from(existingValue, handler)
      }

      return from(null, handler).also {
        jsonObject.put(key, it.jsonObject)
      }
    }

    fun from(jsonObject: JSONObject?, handler: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
      val builder = JsonObjectBuilder(jsonObject)
      handler(builder)
      return builder
    }

    fun empty(handler: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
      return from(null, handler)
    }
  }

  val jsonObject: JSONObject

  init {
    this.jsonObject = jsonObject ?: JSONObject()
  }

  fun addString(key: String, value: String?): JsonObjectBuilder {
    jsonObject.put(key, value)
    return this
  }

  fun addInt(key: String, value: Int?): JsonObjectBuilder {
    jsonObject.put(key, value)
    return this
  }

  fun addDouble(key: String, value: Double?): JsonObjectBuilder {
    jsonObject.put(key, value)
    return this
  }

  fun addLong(key: String, value: Long?): JsonObjectBuilder {
    jsonObject.put(key, value)
    return this
  }

  fun addBoolean(key: String, value: Boolean?): JsonObjectBuilder {
    jsonObject.put(key, value)
    return this
  }

  fun addArray(key: String, valueBuilder: JsonArrayBuilder.() -> Unit): JsonObjectBuilder {
    jsonObject.put(key, JsonArrayBuilder.empty(valueBuilder).jsonArray)
    return this
  }

  fun addObject(key: String, extend: Boolean = false, valueBuilder: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
    if (extend && jsonObject.has(key)) {
      val existing = jsonObject.get(key)

      if (existing !is JSONObject)
        throw IllegalStateException("Cannot extend $key of type ${existing.javaClass} with an object")

      jsonObject.put(key, from(existing, valueBuilder).jsonObject)
      return this
    }

    jsonObject.put(key, empty(valueBuilder).jsonObject)
    return this
  }
}