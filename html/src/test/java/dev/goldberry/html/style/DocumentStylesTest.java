package dev.goldberry.html.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.select.Selector;
import dev.goldberry.html.Html;
import dev.goldberry.html.model.Element;
import dev.goldberry.html.model.HtmlDocument;
import dev.goldberry.html.model.HtmlNode;

/// A document's own styles, worked out into one class per element.
@DisplayName("DocumentStyles")
class DocumentStylesTest {

    /// The element with `id`, anywhere in `document`.
    private static Element byId(HtmlDocument document, String id) {
        var found = new ArrayList<Element>();
        collect(document.children(), id, found);
        return found.getFirst();
    }

    private static void collect(List<HtmlNode> nodes, String id, List<Element> found) {
        for (var node : nodes) {
            if (node instanceof Element element) {
                if (id.equals(element.id())) {
                    found.add(element);
                }
                collect(element.children(), id, found);
            }
        }
    }

    /// What the class minted for `id` declares, normally (`state` null) or in a
    /// state, as `property -> value`.
    private static Map<String, String> said(
            DocumentStyles styles, HtmlDocument document, String id, Selector.@Nullable PseudoClass state) {
        var name = styles.classOf(byId(document, id));
        var said = new LinkedHashMap<String, String>();
        if (name == null) {
            return said;
        }
        for (var rule : styles.rules().rules()) {
            var key = rule.selectors().getFirst().key();
            var states = key.pseudoClasses();
            if (key.classes().contains(name) && (state == null ? states.isEmpty() : states.contains(state))) {
                for (var declaration : rule.declarations()) {
                    var value = new StringBuilder();
                    for (var token : declaration.value()) {
                        value.append(token.is(TokenType.WHITESPACE) ? " " : token.cssText());
                    }
                    said.put(declaration.property(), value.toString());
                }
            }
        }
        return said;
    }

    private static Map<String, String> said(String html, String id, Colours colours) {
        var document = Html.parse(html);
        return said(DocumentStyles.of(document, colours), document, id, null);
    }

    private static Map<String, String> said(String html, String id) {
        return said(html, id, Colours.AUTHOR);
    }

    @Nested
    @DisplayName("cascades")
    class Cascades {

        @Test
        @DisplayName("an attribute under a rule under the element's own style=")
        void order() {
            var html = """
                    <style>td { background-color: #eeeeee; color: #111111 }</style>
                    <table><tr><td id="c" bgcolor="#ff0000" style="color: #222222">x</td></tr></table>
                    """;

            assertEquals(Map.of("background-color", "#eeeeee", "color", "#222222"), said(html, "c"));
        }

        @Test
        @DisplayName("by specificity, then by order")
        void specificity() {
            var html = """
                    <style>#p { color: #111111 } p.a { color: #222222 } p { color: #333333 } .a { padding: 2px }
                    .a { padding: 4px }</style>
                    <p id="p" class="a">x</p><p id="q" class="a">y</p>
                    """;

            assertEquals("#111111", said(html, "p").get("color"));
            assertEquals("#222222", said(html, "q").get("color"));
            assertEquals("4px", said(html, "q").get("padding"));
        }

        @Test
        @DisplayName("matching selectors against the document's tree, not the widgets'")
        void documentTree() {
            var html = """
                    <style>ul > li { color: #111111 } td a { color: #222222 } p:first-child { color: #333333 }
                    h1 + p { padding: 1px }</style>
                    <ul><li id="li">x</li></ul>
                    <table><tbody><tr><td><a id="a" href="/">y</a></td></tr></tbody></table>
                    <div><p id="first">one</p><p id="second">two</p></div>
                    <h1>T</h1><p id="after">z</p>
                    """;

            assertEquals("#111111", said(html, "li").get("color"));
            assertEquals("#222222", said(html, "a").get("color"));
            assertEquals("#333333", said(html, "first").get("color"));
            assertNull(said(html, "second").get("color"));
            assertEquals("1px", said(html, "after").get("padding"));
        }

        @Test
        @DisplayName("keeping a hover rule as a state, dropping :visited and reading :link as an anchor")
        void states() {
            var html = """
                    <style>a:link { color: #111111 } a:visited { color: #999999 } a:hover { color: #222222 }</style>
                    <a id="a" href="/">x</a>
                    """;
            var document = Html.parse(html);
            var styles = DocumentStyles.of(document, Colours.AUTHOR);

            assertEquals(Map.of("color", "#111111"), said(styles, document, "a", null));
            assertEquals(Map.of("color", "#222222"), said(styles, document, "a", Selector.PseudoClass.HOVER));
        }

        @Test
        @DisplayName("dropping a selector it cannot read without dropping the rule's others")
        void selectorsAlone() {
            var html = """
                    <style>p::first-line, p[data-x], #p, p:unknown-thing { color: #111111 }</style>
                    <p id="p">x</p><p id="q">y</p>
                    """;

            assertEquals("#111111", said(html, "p").get("color"));
            assertNull(said(html, "q").get("color"));
        }

        @Test
        @DisplayName("ignoring a print sheet and anything inside @media")
        void media() {
            var html = """
                    <style media="print">p { color: #111111 }</style>
                    <style>@media (max-width: 600px) { p { color: #222222 } }</style>
                    <p id="p">x</p>
                    """;

            assertTrue(said(html, "p").isEmpty());
        }
    }

    @Nested
    @DisplayName("classes")
    class Classes {

        @Test
        @DisplayName("one per element that says something, shared by two that say the same")
        void shared() {
            var document = Html.parse(
                    "<p id=\"a\" style=\"color:red\">x</p><p id=\"b\" style=\"color:red\">y</p>" + "<p id=\"c\">z</p>");
            var styles = DocumentStyles.of(document, Colours.AUTHOR);

            var a = styles.classOf(byId(document, "a"));
            assertNotNull(a);
            assertTrue(a.startsWith(DocumentStyles.PREFIX));
            assertEquals(a, styles.classOf(byId(document, "b")));
            assertNull(styles.classOf(byId(document, "c")));
        }

        @Test
        @DisplayName("an inline element's carries what the inline elements round it said, its own winning")
        void inlineMerged() {
            var html = "<p><span style=\"color:#111111; padding: 1px\"><b id=\"b\" style=\"color:#222222\">x</b>"
                    + "</span></p>";

            assertEquals(Map.of("padding", "1px", "color", "#222222"), said(html, "b"));
        }

        @Test
        @DisplayName("a block's carries its own alone, which its words inherit through the cascade")
        void blockAlone() {
            var html = "<div style=\"padding:1px\"><p id=\"p\" style=\"color:#222222\">x</p></div>";

            assertEquals(Map.of("color", "#222222"), said(html, "p"));
        }

        @Test
        @DisplayName("none at all for a document that styles nothing, and the same empty rules")
        void none() {
            var styles = DocumentStyles.of(Html.parse("<p>plain</p>"), Colours.ADAPT);

            assertSame(DocumentStyles.NONE, styles);
            assertTrue(styles.rules().isEmpty());
        }

        @Test
        @DisplayName("refuses to forward a document class that reads as a generated one")
        void forwardable() {
            assertFalse(DocumentStyles.forwardable("author-2"));
            assertTrue(DocumentStyles.forwardable("author"));
            assertTrue(DocumentStyles.forwardable("callout"));
        }
    }

    @Nested
    @DisplayName("colours")
    class ColoursTreated {

        private static final String MAIL = """
                <div id="card" style="background-color:#ffffff; border: 1px solid #cccccc; padding: 8px">
                  <p id="dim" style="color:#eeeeee">low contrast on white</p>
                  <p id="fine" style="color:#333333">dark grey on white</p>
                  <a id="link" href="/">a link</a>
                </div>
                <p id="loose" style="color:#cc0000">red on the theme's surface</p>
                """;

        @Test
        @DisplayName("as written: everything kept")
        void author() {
            assertEquals("#eeeeee", said(MAIL, "dim", Colours.AUTHOR).get("color"));
            assertEquals("#cc0000", said(MAIL, "loose", Colours.AUTHOR).get("color"));
        }

        @Test
        @DisplayName("the theme's: every colour dropped, and a border keeps its width and style")
        void theme() {
            var card = said(MAIL, "card", Colours.THEME);

            assertEquals(Map.of("border", "1px solid", "padding", "8px"), card);
            assertTrue(said(MAIL, "dim", Colours.THEME).isEmpty());
            assertTrue(said(MAIL, "loose", Colours.THEME).isEmpty());
        }

        @Test
        @DisplayName("adapted: a painted ground kept, with ink that reads on it")
        void groundGetsInk() {
            var card = said(MAIL, "card", Colours.ADAPT);

            assertEquals("#ffffff", card.get("background-color"));
            assertEquals("1px solid #cccccc", card.get("border"));
            assertEquals(Inks.hex(Inks.DARK), card.get("color"), "text on a painted white has to be dark");
        }

        @Test
        @DisplayName("adapted: a colour that reads on its ground kept, one that does not replaced")
        void inkOnGround() {
            assertEquals("#333333", said(MAIL, "fine", Colours.ADAPT).get("color"));
            assertEquals(Inks.hex(Inks.DARK), said(MAIL, "dim", Colours.ADAPT).get("color"));
        }

        @Test
        @DisplayName("adapted: a colour on no ground of the mail's dropped, since the theme's surface is unknown")
        void inkOnNoGround() {
            assertTrue(said(MAIL, "loose", Colours.ADAPT).isEmpty());
        }

        @Test
        @DisplayName("adapted: a link on a painted ground gets a blue that reads on it")
        void link() {
            var colour = said(MAIL, "link", Colours.ADAPT).get("color");

            assertNotNull(colour);
            assertNotEquals(Inks.hex(Inks.DARK), colour);
            assertTrue(Inks.reads(0xFF000000 | Integer.parseInt(colour.substring(1), 16), 0xFFFFFFFF));
        }

        @Test
        @DisplayName("adapted: a dark ground gets light ink")
        void darkGround() {
            var html = "<div id=\"d\" style=\"background:#202020\"><p id=\"p\" style=\"color:#303030\">x</p></div>";

            assertEquals(Inks.hex(Inks.LIGHT), said(html, "d", Colours.ADAPT).get("color"));
            assertEquals(Inks.hex(Inks.LIGHT), said(html, "p", Colours.ADAPT).get("color"));
        }
    }
}
