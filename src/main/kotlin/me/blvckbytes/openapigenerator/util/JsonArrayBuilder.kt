package me.blvckbytes.openapigenerator.util

import org.codehaus.jettison.json.JSONArray

class JsonArrayBuilder {

  private val jsonArray = JSONArray()

  fun addString(value: String?): JsonArrayBuilder {
    jsonArray.put(value)
    return this
  }

  fun addInt(value: Int?): JsonArrayBuilder {
    jsonArray.put(value)
    return this
  }

  fun addDouble(value: Double?): JsonArrayBuilder {
    jsonArray.put(value)
    return this
  }

  fun addLong(value: Long?): JsonArrayBuilder {
    jsonArray.put(value)
    return this
  }

  fun addBoolean(value: Boolean?): JsonArrayBuilder {
    jsonArray.put(value)
    return this
  }

  fun addArray(valueBuilder: JsonArrayBuilder.() -> JsonArrayBuilder): JsonArrayBuilder {
    jsonArray.put(valueBuilder(JsonArrayBuilder()).build())
    return this
  }

  fun addObject(valueBuilder: JsonObjectBuilder.() -> JsonObjectBuilder): JsonArrayBuilder {
    jsonArray.put(valueBuilder(JsonObjectBuilder()).build())
    return this
  }

  fun build(): JSONArray {
    return jsonArray
  }
}