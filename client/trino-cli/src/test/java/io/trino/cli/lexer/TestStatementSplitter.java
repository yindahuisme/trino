/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.cli.lexer;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.StatementSplitter.Statement;
import org.junit.jupiter.api.Test;

import static io.trino.cli.lexer.StatementSplitter.isEmptyStatement;
import static io.trino.cli.lexer.StatementSplitter.squeezeStatement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStatementSplitter
{
    @Test
    public void testSplitterIncomplete()
    {
        StatementSplitter splitter = new StatementSplitter(" select * FROM foo  ");
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEqualTo("select * FROM foo");
    }

    @Test
    public void testSplitterEmptyInput()
    {
        StatementSplitter splitter = new StatementSplitter("");
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterEmptyStatements()
    {
        StatementSplitter splitter = new StatementSplitter(";;;");
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterSingle()
    {
        StatementSplitter splitter = new StatementSplitter("select * from foo;");
        assertThat(splitter.getCompleteStatements()).containsExactly(statement("select * from foo"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterMultiple()
    {
        StatementSplitter splitter = new StatementSplitter(" select * from  foo ; select * from t; select * from ");
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select * from  foo"),
                statement("select * from t"));
        assertThat(splitter.getPartialStatement()).isEqualTo("select * from");
    }

    @Test
    public void testSplitterMultipleWithEmpty()
    {
        StatementSplitter splitter = new StatementSplitter("; select * from  foo ; select * from t;;;select * from ");
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select * from  foo"),
                statement("select * from t"));
        assertThat(splitter.getPartialStatement()).isEqualTo("select * from");
    }

    @Test
    public void testSplitterCustomDelimiters()
    {
        String sql = "// select * from  foo // select * from t;//select * from ";
        StatementSplitter splitter = new StatementSplitter(sql, ImmutableSet.of(";", "//"));
        assertThat(splitter.getCompleteStatements()).containsExactly(
                new Statement("select * from  foo", "//"),
                new Statement("select * from t", ";"));
        assertEquals("select * from", splitter.getPartialStatement());
    }

    @Test
    public void testSplitterErrorBeforeComplete()
    {
        StatementSplitter splitter = new StatementSplitter(" select * from z# oops ; select ");
        assertThat(splitter.getCompleteStatements()).containsExactly(statement("select * from z# oops"));
        assertThat(splitter.getPartialStatement()).isEqualTo("select");
    }

    @Test
    public void testSplitterErrorAfterComplete()
    {
        StatementSplitter splitter = new StatementSplitter("select * from foo; select z# oops ");
        assertThat(splitter.getCompleteStatements()).containsExactly(statement("select * from foo"));
        assertThat(splitter.getPartialStatement()).isEqualTo("select z# oops");
    }

    @Test
    public void testSplitterWithQuotedString()
    {
        String sql = "select 'foo bar' x from dual";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEqualTo(sql);
    }

    @Test
    public void testSplitterWithIncompleteQuotedString()
    {
        String sql = "select 'foo', 'bar";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEqualTo(sql);
    }

    @Test
    public void testSplitterWithEscapedSingleQuote()
    {
        String sql = "select 'hello''world' from dual";
        StatementSplitter splitter = new StatementSplitter(sql + ";");
        assertThat(splitter.getCompleteStatements()).containsExactly(statement(sql));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterWithQuotedIdentifier()
    {
        String sql = "select \"0\"\"bar\" from dual";
        StatementSplitter splitter = new StatementSplitter(sql + ";");
        assertThat(splitter.getCompleteStatements()).containsExactly(statement(sql));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterWithBackquote()
    {
        String sql = "select  ` f``o o ` from dual";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEqualTo(sql);
    }

    @Test
    public void testSplitterWithDigitIdentifier()
    {
        String sql = "select   1x  from dual";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEqualTo(sql);
    }

    @Test
    public void testSplitterWithSingleLineComment()
    {
        StatementSplitter splitter = new StatementSplitter("--empty\n;-- start\nselect * -- junk\n-- hi\nfrom foo; -- done");
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("--empty"),
                statement("-- start\nselect * -- junk\n-- hi\nfrom foo"));
        assertThat(splitter.getPartialStatement()).isEqualTo("-- done");
    }

    @Test
    public void testSplitterWithMultiLineComment()
    {
        StatementSplitter splitter = new StatementSplitter("/* empty */;/* start */ select * /* middle */ from foo; /* end */");
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("/* empty */"),
                statement("/* start */ select * /* middle */ from foo"));
        assertThat(splitter.getPartialStatement()).isEqualTo("/* end */");
    }

    @Test
    public void testSplitterWithSingleLineCommentPartial()
    {
        String sql = "-- start\nselect * -- junk\n-- hi\nfrom foo -- done";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEqualTo(sql);
    }

    @Test
    public void testSplitterWithMultiLineCommentPartial()
    {
        String sql = "/* start */ select * /* middle */ from foo /* end */";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).isEmpty();
        assertThat(splitter.getPartialStatement()).isEqualTo(sql);
    }

    @Test
    public void testSplitterIncompleteSelect()
    {
        String sql = "select abc, ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc,"),
                statement("select 456"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterIncompleteSelectAndFrom()
    {
        String sql = "select abc, from ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc, from"),
                statement("select 456"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterIncompleteSelectWithFrom()
    {
        String sql = "select abc, from xxx ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc, from xxx"),
                statement("select 456"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterIncompleteSelectAndWhere()
    {
        String sql = "select abc, from xxx where ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc, from xxx where"),
                statement("select 456"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterIncompleteSelectWithWhere()
    {
        String sql = "select abc, from xxx where false ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc, from xxx where false"),
                statement("select 456"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterIncompleteSelectWithInvalidWhere()
    {
        String sql = "select abc, from xxx where and false ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc, from xxx where and false"),
                statement("select 456"));
    }

    @Test
    public void testSplitterIncompleteSelectAndFromAndWhere()
    {
        String sql = "select abc, from where ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc, from where"),
                statement("select 456"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testSplitterSelectItemsWithoutComma()
    {
        String sql = "select abc xyz foo ; select 456;";
        StatementSplitter splitter = new StatementSplitter(sql);
        assertThat(splitter.getCompleteStatements()).containsExactly(
                statement("select abc xyz foo"),
                statement("select 456"));
        assertThat(splitter.getPartialStatement()).isEmpty();
    }

    @Test
    public void testIsEmptyStatement()
    {
        assertTrue(isEmptyStatement(""));
        assertTrue(isEmptyStatement(" "));
        assertTrue(isEmptyStatement("\t\n "));
        assertTrue(isEmptyStatement("--foo\n  --what"));
        assertTrue(isEmptyStatement("/* oops */"));
        assertFalse(isEmptyStatement("x"));
        assertFalse(isEmptyStatement("select"));
        assertFalse(isEmptyStatement("123"));
        assertFalse(isEmptyStatement("z#oops"));
    }

    @Test
    public void testSqueezeStatement()
    {
        String sql = "select   *  from\n foo\n  order by x ; ";
        assertEquals("select * from foo order by x ;", squeezeStatement(sql));
    }

    @Test
    public void testSqueezeStatementWithIncompleteQuotedString()
    {
        String sql = "select   *  from\n foo\n  where x = 'oops";
        assertEquals("select * from foo where x = 'oops", squeezeStatement(sql));
    }

    @Test
    public void testSqueezeStatementWithBackquote()
    {
        String sql = "select  `  f``o  o`` `   from dual";
        assertEquals("select `  f``o  o`` ` from dual", squeezeStatement(sql));
    }

    @Test
    public void testSqueezeStatementAlternateDelimiter()
    {
        String sql = "select   *  from\n foo\n  order by x // ";
        assertEquals("select * from foo order by x //", squeezeStatement(sql));
    }

    @Test
    public void testSqueezeStatementError()
    {
        String sql = "select   *  from z#oops";
        assertEquals("select * from z#oops", squeezeStatement(sql));
    }

    private static Statement statement(String value)
    {
        return new Statement(value, ";");
    }
}
