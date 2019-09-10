package org.jetbrains.plugins.scala.lang.parser.stress;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.convertLineSeparators;

/**
 * @author ilyas
 */
@RunWith(AllTests.class)
public class DragSearchTest extends BaseScalaFileSetTestCase {
  private static final int MAX_ROLLBACKS = 30;

  @NonNls
  private static final String DATA_PATH = "/parser/stress/data/";

  public DragSearchTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
        TestUtils.getTestDataPath() + DATA_PATH
    );
  }

  @Override
  public void runTest(@NotNull File testFile,
                      @NotNull Project project) throws IOException {
    String content = convertLineSeparators(new String(FileUtil.loadFileText(testFile)));
    transform(testFile.getName(), content, project);
  }

  @NotNull
  protected String transform(@NotNull String testName,
                             @NotNull String fileText,
                             @NotNull Project project) {
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaLanguage.INSTANCE);
    PsiBuilder psiBuilder = PsiBuilderFactory.getInstance().createBuilder(
            parserDefinition,
            parserDefinition.createLexer(project),
            fileText
    );
    DragBuilderWrapper dragBuilder = new DragBuilderWrapper(project, psiBuilder);
    parserDefinition.createParser(project).parse(parserDefinition.getFileNodeType(), dragBuilder);

    Pair<TextRange, Integer>[] dragInfo = dragBuilder.getDragInfo();
    exploreForDrags(dragInfo);

    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "temp.scala",
            ScalaFileType.INSTANCE,
            fileText
    );
    return DebugUtil.psiToString(psiFile, false);
  }

  private static void exploreForDrags(Pair<TextRange, Integer>[] dragInfo) {
    int ourMaximum = max(dragInfo);
    List<Pair<TextRange, Integer>> penals = ContainerUtil.findAll(dragInfo, pair -> pair.getSecond() >= MAX_ROLLBACKS);

    if (penals.size() > 0) {
      Assert.assertTrue("Too much rollbacks: " + ourMaximum, ourMaximum < MAX_ROLLBACKS);
    }

  }

  private static int max(Pair<TextRange, Integer>[] dragInfo) {
    int max = 0;
    for (Pair<TextRange, Integer> pair : dragInfo) {
      if (pair.getSecond() > max) {
        max = pair.getSecond();
      }
    }
    return max;
  }

  public static Test suite() {
    return new DragSearchTest();
  }
}
