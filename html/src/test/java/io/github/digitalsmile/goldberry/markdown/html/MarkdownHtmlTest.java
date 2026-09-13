package io.github.digitalsmile.goldberry.markdown.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.markdown.Markdown;
import io.github.digitalsmile.goldberry.markdown.MarkdownExtension;
import io.github.digitalsmile.goldberry.markdown.MarkdownSyntax;
import io.github.digitalsmile.goldberry.markdown.model.CellAlignment;
import io.github.digitalsmile.goldberry.markdown.model.Code;
import io.github.digitalsmile.goldberry.markdown.model.CodeBlock;
import io.github.digitalsmile.goldberry.markdown.model.Document;
import io.github.digitalsmile.goldberry.markdown.model.Emphasis;
import io.github.digitalsmile.goldberry.markdown.model.Heading;
import io.github.digitalsmile.goldberry.markdown.model.Image;
import io.github.digitalsmile.goldberry.markdown.model.Item;
import io.github.digitalsmile.goldberry.markdown.model.LineBreak;
import io.github.digitalsmile.goldberry.markdown.model.Link;
import io.github.digitalsmile.goldberry.markdown.model.NumberedList;
import io.github.digitalsmile.goldberry.markdown.model.Paragraph;
import io.github.digitalsmile.goldberry.markdown.model.Quote;
import io.github.digitalsmile.goldberry.markdown.model.RawHtml;
import io.github.digitalsmile.goldberry.markdown.model.Strong;
import io.github.digitalsmile.goldberry.markdown.model.Struck;
import io.github.digitalsmile.goldberry.markdown.model.Table;
import io.github.digitalsmile.goldberry.markdown.model.TableCell;
import io.github.digitalsmile.goldberry.markdown.model.TableRow;
import io.github.digitalsmile.goldberry.markdown.model.Text;
import io.github.digitalsmile.goldberry.markdown.model.ThematicBreak;
import io.github.digitalsmile.goldberry.markdown.model.Underlined;
import io.github.digitalsmile.goldberry.markdown.model.WikiLink;

/// The model, written out as HTML.
///
/// Half of these build the model by hand rather than parsing, on purpose: the writer
/// is a fold over records and a record is cheap to write down, so a test that says
/// `new Link("x", null, false, …)` is testing the writer rather than the parser. The
/// ones that do parse are the round trips — where what matters is that the two halves
/// of the module agree.
@DisplayName("writing HTML")
class MarkdownHtmlTest {

    @BeforeAll
    static void requireLibrary() {
        // Only the parsing tests below need it, and they are the majority.
        io.github.digitalsmile.goldberry.markdown.model.Document.EMPTY.blocks();
    }

    private static String html(String markdown) {
        return MarkdownHtml.of(markdown);
    }

    private static Document document(io.github.digitalsmile.goldberry.markdown.model.Block... blocks) {
        return new Document(List.of(blocks));
    }

    @Test
    @DisplayName("writes nothing for an empty document")
    void empty() {
        assertEquals("", MarkdownHtml.of(Document.EMPTY));
    }

    @Test
    @DisplayName("refuses null")
    void nullIsRefused() {
        assertThrows(NullPointerException.class, () -> MarkdownHtml.of((Document) null));
    }

    @Nested
    @DisplayName("blocks")
    class Blocks {

        @Test
        @DisplayName("write a heading at its own level")
        void heading() {
            assertEquals("<h3>Hi</h3>\n", MarkdownHtml.of(document(new Heading(3, List.of(new Text("Hi"))))));
        }

        @Test
        @DisplayName("write a paragraph")
        void paragraph() {
            assertEquals("<p>Hi</p>\n", MarkdownHtml.of(document(new Paragraph(List.of(new Text("Hi"))))));
        }

        @Test
        @DisplayName("nest a quote")
        void quote() {
            assertEquals(
                    "<blockquote>\n<p>Hi</p>\n</blockquote>\n",
                    MarkdownHtml.of(document(new Quote(List.of(new Paragraph(List.of(new Text("Hi"))))))));
        }

        @Test
        @DisplayName("write a rule")
        void rule() {
            assertEquals("<hr>\n", MarkdownHtml.of(document(new ThematicBreak())));
        }

        @Test
        @DisplayName("mark a fence's language the way every highlighter reads it")
        void codeLanguage() {
            assertEquals(
                    "<pre><code class=\"language-java\">int a;\n</code></pre>\n",
                    MarkdownHtml.of(document(new CodeBlock("java", "java", "int a;\n"))));
            assertEquals(
                    "<pre><code>int a;\n</code></pre>\n",
                    MarkdownHtml.of(document(new CodeBlock("int a;\n"))),
                    "no language, no class");
        }

        @Test
        @DisplayName("escape code rather than trusting it")
        void codeIsEscaped() {
            assertEquals(
                    "<pre><code>&lt;script&gt;&amp;\n</code></pre>\n",
                    MarkdownHtml.of(document(new CodeBlock("<script>&\n"))));
        }

        @Test
        @DisplayName("pass a raw HTML block through, because that is what it is for")
        void rawBlock() {
            assertEquals("<div>x</div>\n", html("<div>x</div>\n"));
        }

        @Test
        @DisplayName("number a list from where it starts")
        void numberedStart() {
            var list = new NumberedList(7, true, List.of(new Item(List.of(new Paragraph(List.of(new Text("x")))))));
            assertEquals("<ol start=\"7\">\n<li>x</li>\n</ol>\n", MarkdownHtml.of(document(list)));
            var fromOne = new NumberedList(1, true, List.of(new Item(List.of(new Paragraph(List.of(new Text("x")))))));
            assertEquals("<ol>\n<li>x</li>\n</ol>\n", MarkdownHtml.of(document(fromOne)), "1 is the default");
        }

        @Test
        @DisplayName("unwrap a tight item's paragraph, as CommonMark's own output does")
        void tightItem() {
            assertEquals("<ul>\n<li>one</li>\n<li>two</li>\n</ul>\n", html("- one\n- two\n"));
        }

        @Test
        @DisplayName("keep a loose item's paragraphs")
        void looseItem() {
            assertTrue(html("- one\n\n- two\n").contains("<li>\n<p>one</p>\n</li>"), "the blank line meant something");
        }

        @Test
        @DisplayName("write a task item as the check box a reader expects")
        void taskItem() {
            var output = html("- [x] done\n- [ ] not\n");
            assertTrue(
                    output.contains("<li class=\"task\"><input type=\"checkbox\" disabled checked>done</li>"), output);
            assertTrue(output.contains("<li class=\"task\"><input type=\"checkbox\" disabled>not</li>"), output);
        }
    }

    @Nested
    @DisplayName("inline content")
    class Inlines {

        private String inline(io.github.digitalsmile.goldberry.markdown.model.Inline... content) {
            var out = MarkdownHtml.of(document(new Paragraph(List.of(content))));
            return out.substring("<p>".length(), out.length() - "</p>\n".length());
        }

        @Test
        @DisplayName("escapes text, and does not escape an apostrophe")
        void escaping() {
            assertEquals("a &amp; b &lt;c&gt; d's", inline(new Text("a & b <c> d's")));
        }

        @Test
        @DisplayName("wraps every mark in the tag CommonMark uses")
        void marks() {
            assertEquals("<em>a</em>", inline(new Emphasis(List.of(new Text("a")))));
            assertEquals("<strong>a</strong>", inline(new Strong(List.of(new Text("a")))));
            assertEquals("<del>a</del>", inline(new Struck(List.of(new Text("a")))));
            assertEquals("<u>a</u>", inline(new Underlined(List.of(new Text("a")))));
            assertEquals("<code>a &amp; b</code>", inline(new Code("a & b")));
        }

        @Test
        @DisplayName("quotes a link's href and title, escaping the quote itself")
        void link() {
            assertEquals(
                    "<a href=\"x?a=1&amp;b=2\" title=\"say &quot;hi&quot;\">go</a>",
                    inline(new Link("x?a=1&b=2", "say \"hi\"", false, List.of(new Text("go")))),
                    "a title with a quotation mark in it would otherwise end the attribute");
            assertEquals("<a href=\"x\">go</a>", inline(new Link("x", List.of(new Text("go")))), "no title, no title");
        }

        @Test
        @DisplayName("marks a wiki link so an application can rewrite it")
        void wikiLink() {
            assertEquals(
                    "<a href=\"Meeting notes\" class=\"wikilink\">notes</a>",
                    inline(new WikiLink("Meeting notes", List.of(new Text("notes")))));
        }

        @Test
        @DisplayName("writes an image with its alt text")
        void image() {
            assertEquals("<img src=\"a.png\" alt=\"a &amp; b\" title=\"T\">", inline(new Image("a.png", "T", "a & b")));
        }

        @Test
        @DisplayName("writes a hard break as a break and a soft one as a newline")
        void breaks() {
            assertEquals("a<br>\nb", inline(new Text("a"), new LineBreak(true), new Text("b")));
            assertEquals(
                    "a\nb",
                    inline(new Text("a"), new LineBreak(false), new Text("b")),
                    "HTML collapses whitespace by specification, so the paragraph reflows");
        }

        @Test
        @DisplayName("passes inline markup through")
        void rawHtml() {
            assertEquals(
                    "press <kbd>K</kbd>",
                    inline(new Text("press "), new RawHtml("<kbd>"), new Text("K"), new RawHtml("</kbd>")));
        }
    }

    @Nested
    @DisplayName("tables")
    class Tables {

        @Test
        @DisplayName("write the head, the body and each column's alignment")
        void table() {
            var head = new TableRow(
                    true,
                    List.of(
                            new TableCell(true, CellAlignment.START, List.of(new Text("a"))),
                            new TableCell(true, CellAlignment.CENTER, List.of(new Text("b")))));
            var body = new TableRow(
                    false,
                    List.of(
                            new TableCell(false, CellAlignment.START, List.of(new Text("1"))),
                            new TableCell(false, CellAlignment.END, List.of(new Text("2")))));
            assertEquals("""
                    <table>
                    <thead>
                    <tr>
                    <th style="text-align:left">a</th>
                    <th style="text-align:center">b</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                    <td style="text-align:left">1</td>
                    <td style="text-align:right">2</td>
                    </tr>
                    </tbody>
                    </table>
                    """, MarkdownHtml.of(new Document(List.of(new Table(List.of(head), List.of(body))))));
        }

        @Test
        @DisplayName("leave an unaligned cell without a style attribute")
        void noAlignment() {
            var row = new TableRow(false, List.of(new TableCell(false, CellAlignment.DEFAULT, List.of(new Text("x")))));
            assertFalse(
                    MarkdownHtml.of(new Document(List.of(new Table(List.of(), List.of(row)))))
                            .contains("style"),
                    "the document said nothing about alignment, so neither does the output");
        }
    }

    @Nested
    @DisplayName("a whole note")
    class RoundTrip {

        @Test
        @DisplayName("goes from Markdown to the HTML a server can hand out")
        void note() {
            var source = """
                    # Shopping

                    Milk, **bread** and `coffee`. See [the list](http://x/?a=1&amp;b=2).

                    - [x] Milk
                    - [ ] Bread

                    > Not the expensive coffee.

                    ```sh
                    echo "done"
                    ```
                    """;
            assertEquals("""
                    <h1>Shopping</h1>
                    <p>Milk, <strong>bread</strong> and <code>coffee</code>. See <a href="http://x/?a=1&amp;b=2">the list</a>.</p>
                    <ul>
                    <li class="task"><input type="checkbox" disabled checked>Milk</li>
                    <li class="task"><input type="checkbox" disabled>Bread</li>
                    </ul>
                    <blockquote>
                    <p>Not the expensive coffee.</p>
                    </blockquote>
                    <pre><code class="language-sh">echo "done"
                    </code></pre>
                    """, html(source));
        }

        @Test
        @DisplayName("escapes what the dialect refused to treat as markup")
        void refusedHtmlIsEscaped() {
            var syntax = MarkdownSyntax.gitHub().with(MarkdownExtension.NO_HTML);
            assertEquals(
                    "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>\n",
                    MarkdownHtml.of(Markdown.parse("<script>alert(1)</script>\n", syntax)),
                    "NO_HTML is not a sanitiser, but what it refuses does come out as text");
        }
    }
}
