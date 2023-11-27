package me.blvckbytes.openapigenerator.util

import org.codehaus.jettison.json.JSONArray

class JsonArrayBuilder private constructor(jsonArray: JSONArray? = null){

  companion object {
    fun from(jsonArray: JSONArray?, handler: JsonArrayBuilder.() -> Unit): JsonArrayBuilder {
      val builder = JsonArrayBuilder(jsonArray)
      handler(builder)
      return builder
    }

    fun empty(handler: JsonArrayBuilder.() -> Unit): JsonArrayBuilder {
      return from(null, handler)
    }
  }

  private val jsonArray: JSONArray

  init {
    this.jsonArray = jsonArray ?: JSONArray()
  }

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

  fun addArray(valueBuilder: JsonArrayBuilder.() -> Unit): JsonArrayBuilder {
    jsonArray.put(empty(valueBuilder).build())
    return this
  }

  fun addObject(valueBuilder: JsonObjectBuilder.() -> Unit): JsonArrayBuilder {
    jsonArray.put(JsonObjectBuilder.empty(valueBuilder).build())
    return this
  }

  fun build(): JSONArray {
    return jsonArray
  }
}