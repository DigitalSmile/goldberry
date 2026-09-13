package io.github.digitalsmile.goldberry.html;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.html.model.Comment;
import io.github.digitalsmile.goldberry.html.model.Element;
import io.github.digitalsmile.goldberry.html.model.HtmlDocument;
import io.github.digitalsmile.goldberry.html.model.HtmlNode;
import io.github.digitalsmile.goldberry.html.model.HtmlText;

/// The parser, one construct at a time.
///
/// Two kinds of test, and the second kind is the point of the file: what a
/// *well-formed* document parses to, and what a **malformed** one parses to. HTML has
/// no syntax errors, so every recovery is a decision somebody made and every one of
/// them is asserted here rather than left to a golden image to notice sideways
/// (ADR-0298).
@DisplayName("Html.parse")
class HtmlTest {

    /// The document's children, for a test that wants to read them positionally.
    private static List<HtmlNode> nodesOf(String html) {
        return Html.parse(html).children();
    }

    /// The one element `html` is expected to parse to.
    private static Element onlyElement(String html) {
        var elements = nodesOf(html).stream()
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .toList();
        assertEquals(1, elements.size(), () -> "expected one element from " + html + ", got " + elements);
        return elements.getFirst();
    }

    @Test
    @DisplayName("an empty document is the shared empty one, not a new object")
    void empty() {
        assertSame(HtmlDocument.EMPTY, Html.parse(""));
        assertTrue(Html.parse("").children().isEmpty());
    }

    @Test
    @DisplayName("null is a programming error rather than an empty page")
    void nullRefused() {
        assertThrows(NullPointerException.class, () -> Html.parse(null));
    }

    @Nested
    @DisplayName("elements")
    class Elements {

        @Test
        @DisplayName("a paragraph is one element holding its text")
        void paragraph() {
            var p = onlyElement("<p>Hello</p>");

            assertEquals("p", p.tag());
            assertEquals(List.of(new HtmlText("Hello")), p.children());
        }

        @Test
        @DisplayName("a tag name is lower-cased, so a fold matches one spelling")
        void lowerCased() {
            assertEquals("div", onlyElement("<DIV>x</DIV>").tag());
        }

        @Test
        @DisplayName("nesting is nesting")
        void nested() {
            var p = onlyElement("<p>a <em>b</em> c</p>");

            assertEquals(3, p.children().size());
            assertEquals(
                    "em", assertInstanceOf(Element.class, p.children().get(1)).tag());
        }

        @Test
        @DisplayName("a void element has no children and takes no close tag")
        void voidElement() {
            var nodes = nodesOf("<p>a<br>b</p>");
            var p = assertInstanceOf(Element.class, nodes.getFirst());

            var br = assertInstanceOf(Element.class, p.children().get(1));
            assertEquals("br", br.tag());
            assertTrue(br.children().isEmpty());
            // And the text after it is still inside the paragraph, which is what a
            // `br` that had swallowed the rest of the document would have lost.
            assertEquals(new HtmlText("b"), p.children().get(2));
        }

        @Test
        @DisplayName("a self-closed tag is empty, which HTML5 does not say and an author means")
        void selfClosing() {
            var nodes = nodesOf("<div/><p>after</p>");

            assertEquals(2, nodes.size());
            assertTrue(
                    assertInstanceOf(Element.class, nodes.getFirst()).children().isEmpty());
            assertEquals("p", assertInstanceOf(Element.class, nodes.get(1)).tag());
        }

        @Test
        @DisplayName("a fragment stays a fragment: no html, head or body is invented")
        void noImpliedElements() {
            var nodes = nodesOf("<p>Hello</p>");

            assertEquals(1, nodes.size());
            assertEquals("p", assertInstanceOf(Element.class, nodes.getFirst()).tag());
        }
    }

    @Nested
    @DisplayName("attributes")
    class Attributes {

        @Test
        @DisplayName("quoted, in either quote, and in the order they were written")
        void quoted() {
            var a = onlyElement("<a href=\"/help\" title='Help'>x</a>");

            assertEquals("/help", a.attribute("href"));
            assertEquals("Help", a.attribute("title"));
            assertEquals(
                    List.of("href", "title"),
                    List.copyOf(a.attributes().byName().keySet()));
        }

        @Test
        @DisplayName("unquoted, which is what half of hand-written HTML does")
        void unquoted() {
            assertEquals("3", onlyElement("<td colspan=3>x</td>").attribute("colspan"));
        }

        @Test
        @DisplayName("a name is lower-cased and a value is not")
        void caseRules() {
            var a = onlyElement("<a HREF=\"/Help\">x</a>");

            assertEquals("/Help", a.attribute("href"));
        }

        @Test
        @DisplayName("present with no value is the empty string, and absent is null")
        void presentWithNoValue() {
            var input = onlyElement("<p disabled>x</p>");

            assertEquals("", input.attribute("disabled"));
            assertTrue(input.attributes().has("disabled"));
            assertEquals(null, input.attribute("title"), "an absent attribute is not an empty one");
        }

        @Test
        @DisplayName("spaces round the equals sign are allowed")
        void spacedEquals() {
            assertEquals("/x", onlyElement("<a href = \"/x\" >y</a>").attribute("href"));
        }

        @Test
        @DisplayName("the first of two with one name wins, which is HTML's rule")
        void repeated() {
            assertEquals(
                    "first",
                    onlyElement("<a href=\"first\" href=\"second\">x</a>").attribute("href"));
        }

        @Test
        @DisplayName("entities in a value are resolved, so an href is usable as written")
        void entitiesInValues() {
            HtmlRequirement.enforce();

            assertEquals(
                    "/s?a=1&b=2",
                    onlyElement("<a href=\"/s?a=1&amp;b=2\">x</a>").attribute("href"));
        }

        @Test
        @DisplayName("a close tag's attributes are read and dropped")
        void onACloseTag() {
            var p = onlyElement("<p>x</p class=\"nonsense\">");

            assertEquals(List.of(new HtmlText("x")), p.children());
        }
    }

    @Nested
    @DisplayName("text and entities")
    class Text {

        @Test
        @DisplayName("a named entity becomes the character it names")
        void named() {
            HtmlRequirement.enforce();

            assertEquals("AT&T", Html.parse("AT&amp;T").text());
        }

        @Test
        @DisplayName("a numeric reference, in either base")
        void numeric() {
            HtmlRequirement.enforce();

            assertEquals("&&", Html.parse("&#38;&#x26;").text());
        }

        @Test
        @DisplayName("a bare ampersand is an ampersand, which is most of them")
        void bareAmpersand() {
            assertEquals("Q&A", Html.parse("Q&A").text());
        }

        @Test
        @DisplayName("and a reference that names nothing is left exactly as typed")
        void unknownEntity() {
            HtmlRequirement.enforce();

            assertEquals("&nope; and &amp;", Html.parse("&nope; and &amp;amp;").text());
        }

        @Test
        @DisplayName("a less-than that begins no tag is a less-than")
        void lessThanIsText() {
            // The changelog case: `if (a < b)` in a sentence. A tokenizer that opened
            // an element here would swallow the rest of the page.
            assertEquals("if a < b then", Html.parse("if a < b then").text());
        }

        @Test
        @DisplayName("whitespace is kept in the model, because a pre needs it")
        void whitespaceKept() {
            var nodes = nodesOf("<p>a  b</p>");
            var p = assertInstanceOf(Element.class, nodes.getFirst());

            assertEquals(new HtmlText("a  b"), p.children().getFirst());
        }
    }

    @Nested
    @DisplayName("comments, doctypes and raw text")
    class Other {

        @Test
        @DisplayName("a comment is kept, because tools read them")
        void comment() {
            var nodes = nodesOf("<!-- a note -->");

            assertEquals(List.of(new Comment(" a note ")), nodes);
        }

        @Test
        @DisplayName("an unterminated comment takes the rest of the file rather than showing it")
        void unterminatedComment() {
            var nodes = nodesOf("<p>x</p><!-- and then nothing");

            assertEquals(2, nodes.size());
            assertEquals(new Comment(" and then nothing"), nodes.get(1));
        }

        @Test
        @DisplayName("a doctype is read and dropped, because the model has no node for one")
        void doctype() {
            var nodes = nodesOf("<!DOCTYPE html><p>x</p>");

            assertEquals(1, nodes.size());
            assertEquals("p", assertInstanceOf(Element.class, nodes.getFirst()).tag());
        }

        @Test
        @DisplayName("a script's content is text, tags and ampersands included")
        void script() {
            var script = onlyElement("<script>if (a < b && c) x()</script>");

            assertEquals("script", script.tag());
            assertEquals(new HtmlText("if (a < b && c) x()"), script.children().getFirst());
        }

        @Test
        @DisplayName("and its close tag is matched whatever case it is written in")
        void scriptCloseTag() {
            var nodes = nodesOf("<script>x</SCRIPT><p>after</p>");

            assertEquals(2, nodes.size());
            assertEquals("p", assertInstanceOf(Element.class, nodes.get(1)).tag());
        }

        @Test
        @DisplayName("a style block is the same, and nothing applies it")
        void style() {
            var style = onlyElement("<style>p { color: red }</style>");

            assertEquals(new HtmlText("p { color: red }"), style.children().getFirst());
        }
    }

    @Nested
    @DisplayName("what an author writes and does not close")
    class ImpliedCloses {

        @Test
        @DisplayName("two items with no close tags are two items, not one inside the other")
        void listItems() {
            var ul = onlyElement("<ul><li>First<li>Second</ul>");

            assertEquals(2, ul.children().size(), () -> "a nested item is a bullet indented under the one above it");
            assertEquals(
                    "First",
                    assertInstanceOf(Element.class, ul.children().getFirst()).text());
            assertEquals(
                    "Second",
                    assertInstanceOf(Element.class, ul.children().get(1)).text());
        }

        @Test
        @DisplayName("a paragraph is ended by a block and not by an inline")
        void paragraphs() {
            var nodes = nodesOf("<p>One<p>Two<h2>Three</h2>");

            assertEquals(List.of("p", "p", "h2"), tags(nodes));
        }

        @Test
        @DisplayName("so an emphasis inside an unclosed paragraph stays in it")
        void inlineDoesNotClose() {
            var nodes = nodesOf("<p>a <em>b</em> c");

            assertEquals(1, nodes.size());
            assertEquals(
                    "a b c", assertInstanceOf(Element.class, nodes.getFirst()).text());
        }

        @Test
        @DisplayName("cells and rows close each other, which is how tables are typed")
        void cellsAndRows() {
            var table = onlyElement("<table><tr><td>a<td>b<tr><td>c</table>");
            var rows = table.find("tr");

            assertEquals(2, rows.size());
            assertEquals(2, rows.getFirst().find("td").size());
            assertEquals(1, rows.get(1).find("td").size());
        }

        @Test
        @DisplayName("a definition list's terms and definitions close each other")
        void definitions() {
            var dl = onlyElement("<dl><dt>Term<dd>Meaning<dt>Other</dl>");

            assertEquals(List.of("dt", "dd", "dt"), tags(dl.children()));
        }

        private List<String> tags(List<HtmlNode> nodes) {
            return nodes.stream()
                    .filter(Element.class::isInstance)
                    .map(node -> ((Element) node).tag())
                    .toList();
        }
    }

    @Nested
    @DisplayName("recovery")
    class Recovery {

        @Test
        @DisplayName("an unclosed element is closed at the end of the file")
        void unclosed() {
            var div = onlyElement("<div><p>text");

            assertEquals("text", div.text());
        }

        @Test
        @DisplayName("a close tag for nothing open is dropped rather than thrown")
        void strayClose() {
            var nodes = nodesOf("</div><p>x</p>");

            assertEquals(1, nodes.size());
            assertEquals("p", assertInstanceOf(Element.class, nodes.getFirst()).tag());
        }

        @Test
        @DisplayName("misnesting closes innermost first and loses nothing")
        void misnested() {
            // `<b>a<i>b</b>c</i>` -- the adoption agency algorithm's example. A browser
            // reopens the `i`; this does not, and what matters is that every word is
            // still in the document.
            assertEquals("a b c", Html.parse("<p><b>a<i>b</b>c</i></p>").text());
        }

        @Test
        @DisplayName("an unterminated tag is closed at the end of the file")
        void unterminatedTag() {
            assertEquals(1, nodesOf("<p class=\"x\"").size());
        }

        @Test
        @DisplayName("an unterminated quote takes the rest of the file rather than the page")
        void unterminatedQuote() {
            var p = onlyElement("<p class=\"x>still text</p>");

            assertEquals("p", p.tag());
        }

        @Test
        @DisplayName("a tag with nothing but rubbish in it terminates")
        void rubbishInATag() {
            // The guard against a hang: a malformed attribute list used to be an
            // endless loop, which is a worse failure than a wrong picture.
            assertEquals(1, nodesOf("<p = / = >x</p>").size());
        }
    }
}
