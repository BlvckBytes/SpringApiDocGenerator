package me.blvckbytes.openapigenerator

import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class QuickConsoleLogger(
  level: Level
) : Logger(UUID.randomUUID().toString(), null) {

  init {
    this.level = level
    this.addHandler(object : Handler() {

      override fun publish(record: LogRecord?) {
        record?.let { println(it.message) }
      }

      override fun flush() {}

      override fun close() {}
    })
  }
}