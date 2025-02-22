// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.openapi.util.Key
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}

import java.util

/**
 * Created by analogy with [[com.intellij.ide.util.JavaAnonymousClassesHelper]]
 */
private object ScalaAnonymousClassNameHelper {

  private val AnonymousClassName: Key[ParameterizedCachedValue[util.Map[ScNewTemplateDefinition, String], ScTypeDefinition]] =
    Key.create("ScalaAnonymousClassName")

  private val ANON_CLASS_PROVIDER = new AnonimousClassProvider

  /**
   * @return name of an anonimous class in form of `$1, $2...`<br>
   * @note The returned name is not the same name as generated by the compiler for a anonimous class file
   *       Compile implementation varies from version to version.<br>
   *       That said, this implementation is not "strict".
   *       it provides some more or less good names in order to distinguish the classes in the structure view
   */
  def getPresentationName(newDef: ScNewTemplateDefinition): Option[String] = {
    val upper = PsiTreeUtil.getParentOfType(newDef, classOf[ScTypeDefinition])
    if (upper == null && newDef.isAnonimous)
      return None

    var value = upper.getUserData(AnonymousClassName)
    if (value == null) {
      value = CachedValuesManager.getManager(upper.getProject).createParameterizedCachedValue(ANON_CLASS_PROVIDER, false)
      upper.putUserData(AnonymousClassName, value)
    }
    Option(value.getValue(upper).get(newDef))
  }

  private class AnonimousClassProvider extends ParameterizedCachedValueProvider[util.Map[ScNewTemplateDefinition, String], ScTypeDefinition] {

    override def compute(upper: ScTypeDefinition): CachedValueProvider.Result[util.Map[ScNewTemplateDefinition, String]] = {
      val map = new util.HashMap[ScNewTemplateDefinition, String]
      upper.accept(new ScalaRecursiveElementVisitor {
        private var anonimousClassNameIndex = 0

        override def visitNewTemplateDefinition(newDef: ScNewTemplateDefinition): Unit = {
          if ((upper eq newDef) || !newDef.isAnonimous)
            return

          anonimousClassNameIndex += 1
          map.put(newDef, "$" + anonimousClassNameIndex)

          super.visitNewTemplateDefinition(newDef)
        }

        override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
          if (upper eq typedef) {
            super.visitTypeDefinition(typedef)
          } else {
            //stop at any type definition except the initial one
            //anonimous classes inside every new type definition will have different name prefix,
            //so their index can start from zero again
          }
        }
      })

      CachedValueProvider.Result.create(map, upper)
    }
  }
}