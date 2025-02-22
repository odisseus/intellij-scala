package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember}

trait ScExtension extends ScParameterOwner.WithContextBounds
  with ScDocCommentOwner
  with ScCommentOwner
  with ScMember
  with ScDeclaredElementsHolder {

  def extensionBody: Option[ScExtensionBody]
  def targetParameter: Option[ScParameter]
  def targetTypeElement: Option[ScTypeElement]
  def extensionMethods: Seq[ScFunction]
}
