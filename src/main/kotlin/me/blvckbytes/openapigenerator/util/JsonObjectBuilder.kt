package me.blvckbytes.openapigenerator.util

import org.codehaus.jettison.json.JSONObject

class JsonObjectBuilder(jsonObject: JSONObject? = null) {

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

  fun addArray(key: String, valueBuilder: JsonArrayBuilder.() -> JsonArrayBuilder): JsonObjectBuilder {
    jsonObject.put(key, valueBuilder(JsonArrayBuilder()).build())
    return this
  }

  fun addObject(key: String, valueBuilder: JsonObjectBuilder.() -> JsonObjectBuilder): JsonObjectBuilder {
    jsonObject.put(key, valueBuilder(JsonObjectBuilder()).build())
    return this
  }

  fun build(): JSONObject {
    return jsonObject
  }
}