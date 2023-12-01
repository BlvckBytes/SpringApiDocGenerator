package me.blvckbytes.openapigenerator.endpoint

import me.blvckbytes.openapigenerator.JavaClassFile

data class FieldReference(
  val containingClass: JavaClassFile,
  val fieldName: String
)