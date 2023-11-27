package me.blvckbytes.openapigenerator.util

import org.codehaus.jettison.json.JSONObject

class JsonObjectBuilder private constructor(jsonObject: JSONObject? = null) {

  companion object {
    fun from(jsonObject: JSONObject?, handler: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
      val builder = JsonObjectBuilder(jsonObject)
      handler(builder)
      return builder
    }

    fun empty(handler: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
      return from(null, handler)
    }
  }

  private val jsonObject: JSONObject

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
    jsonObject.put(key, JsonArrayBuilder.empty(valueBuilder).build())
    return this
  }

  fun addObject(key: String, valueBuilder: JsonObjectBuilder.() -> Unit): JsonObjectBuilder {
    jsonObject.put(key, empty(valueBuilder).build())
    return this
  }

  fun build(): JSONObject {
    return jsonObject
  }
}