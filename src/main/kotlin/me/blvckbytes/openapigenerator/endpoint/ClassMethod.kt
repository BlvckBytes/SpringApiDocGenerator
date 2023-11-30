package me.blvckbytes.openapigenerator.endpoint

import me.blvckbytes.openapigenerator.JavaClassFile
import org.objectweb.asm.tree.MethodNode

class ClassMethod(
  val containingClass: JavaClassFile,
  val method: MethodNode
)