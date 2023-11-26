package me.blvckbytes.openapigenerator

import java.lang.annotation.Inherited
import kotlin.reflect.KClass

@Inherited
@Target(AnnotationTarget.CLASS)
annotation class PolymorphicDiscriminator (
  val fieldName: String,

  // This field is not used and shall only serve as a reminder to the user that
  // the target field should be an enum which has the required structure.
  val mappingEnum: KClass<out DiscriminatorEnum>
)