package org.jetbrains.sbt
package codeInspection

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.project.ScalaFeatures

class SbtReplaceProjectWithProjectInInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case defn: ScPatternDefinition if defn.getContainingFile.getFileType.getName == Sbt.Name =>
      (defn.expr, defn.bindings) match {
        case (Some(call: ScMethodCall), Seq(projectNamePattern: ScReferencePattern)) =>
          findPlaceToFix(call, projectNamePattern.getText).foreach { place =>
            holder.registerProblem(place, SbtBundle.message("sbt.inspection.projectIn.name"),
                                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                          new SbtReplaceProjectWithProjectInQuickFix(place))
          }
        case _ => // do nothing
      }
    case _ =>
  }

  private def findPlaceToFix(call: ScMethodCall, projectName: String): Option[ScMethodCall] = {
    var placeToFix: Option[ScMethodCall] = None
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitMethodCallExpression(call: ScMethodCall): Unit = call match {
        case ScMethodCall(expr, Seq(ScStringLiteral(name), _))
          if expr.textMatches("Project") && name == projectName =>
            placeToFix = Some(call)
        case _ =>
          super.visitMethodCallExpression(call)
      }
    }
    call.accept(visitor)
    placeToFix
  }
}

class SbtReplaceProjectWithProjectInQuickFix(call: ScMethodCall)
        extends AbstractFixOnPsiElement(SbtBundle.message("sbt.inspection.projectIn.name"), call) {

  override protected def doApplyFix(element: ScMethodCall)
                                   (implicit project: Project): Unit = {
    element match {
      case ScMethodCall(_, Seq(_, pathElt)) =>
        element.replace(createExpressionFromText("project.in(" + pathElt.getText + ")", ScalaFeatures.default))
      case _ => // do nothing
    }
  }
}