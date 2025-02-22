package org.jetbrains.plugins.scala.lang.implicits

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment

import java.io.File

abstract class ImplicitsTestBase extends ScalaLightCodeInsightFixtureTestCase {
  private val startExprMarker = "/*start*/"
  private val endExprMarker = "/*end*/"

  def folderPath: String = getTestDataPath + "implicits/"

  protected def doTest(): Unit = {
    import _root_.org.junit.Assert._

    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileText(getTestName(false) + ".scala", fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(startExprMarker)
    val startOffset = offset + startExprMarker.length

    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endExprMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val addOne = if(PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset),classOf[ScExpression]) != null) 0 else 1 //for xml tests
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")
    val conversions = expr.implicitConversions()
    val res = conversions.map(_.name).mkString("Seq(", ",\n    ", ")") + ",\n" + (
      expr.implicitElement() match {
        case None => "None"
        case Some(elem: PsiNamedElement) => "Some(" + elem.name + ")"
        case _ => assert(assertion = false, message = "elem is not PsiNamedElement")
      }
      )

    val ExpectedResultFromLastComment(_, output) = TestUtils.extractExpectedResultFromLastComment(scalaFile)

    assertEquals(output, res)
  }
}