package org.jetbrains.plugins.scala.lang.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;

/**
 * User: Dmitry Naidanov
 * Date: 11/21/11
 */
abstract public class LexerTestBase extends BaseScalaFileSetTestCase {

    protected LexerTestBase(@NotNull String dataPath) {
        super(customOrPropertyPath(dataPath));
    }

    @NotNull
    protected Lexer createLexer() {
        return new ScalaLexer();
    }

    protected void onToken(@NotNull Lexer lexer,
                           @NotNull IElementType tokenType,
                           @NotNull StringBuilder builder) {
        builder.append(tokenType.toString());
        printTokenRange(lexer.getTokenStart(), lexer.getTokenEnd(), builder);
        printTokenText(lexer.getTokenText(), builder);
        builder.append('\n');
    }

    protected void onEof(@NotNull Lexer lexer,
                         @NotNull StringBuilder builder) {
    }

    protected void printTokenRange(int tokenStart, int tokenEnd,
                                   @NotNull StringBuilder builder) {
        builder.append(':').append(' ').append('[')
                .append(tokenStart)
                .append(',').append(' ')
                .append(tokenEnd)
                .append(']').append(',');
    }

    @NotNull
    @Override
    protected String transform(@NotNull String testName,
                               @NotNull String fileText,
                               @NotNull Project project) {
        Lexer lexer = createLexer();
        lexer.start(fileText);

        StringBuilder builder = new StringBuilder();

        IElementType tokenType = lexer.getTokenType();
        while (tokenType != null) {
            onToken(lexer, tokenType, builder);

            lexer.advance();
            tokenType = lexer.getTokenType();
        }

        onEof(lexer, builder);
        return builder.toString();
    }

    private static String customOrPropertyPath(@NotNull String dataPath) {
        String pathProperty = System.getProperty("path");
        return pathProperty != null ?
                pathProperty :
                dataPath;
    }

    private static void printTokenText(@NotNull String tokenText,
                                       @NotNull StringBuilder builder) {
        builder.append(' ').append('{')
                .append(tokenText)
                .append('}');
    }
}