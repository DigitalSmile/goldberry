package io.github.digitalsmile.goldberry.html.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The model, without a parser in front of it.
///
/// These are the methods an **application** uses — `text`, `find`, `classes`,
/// `attribute` — and they are what makes the tree worth exporting rather than hiding
/// behind the widget: a summary, an outline and a link check are walks of one parse
/// (ADR-0295's argument, ADR-0298's other half). Built by hand here so that a failure
/// names the walk rather than the parse.
@DisplayName("the HTML model")
class HtmlModelTest {

    private static Element element(String tag, HtmlNode... children) {
        return new Element(tag, HtmlAttributes.NONE, List.of(children));
    }

    @Nested
    @DisplayName("an element")
    class Elements {

        @Test
        @DisplayName("needs a tag name, because a nameless element is not a thing")
        void needsATag() {
            assertThrows(IllegalArgumentException.class, () -> new Element(" "));
            assertThrows(NullPointerException.class, () -> new Element(null));
        }

        @Test
        @DisplayName("holds an unmodifiable copy of its children")
        void copiesChildren() {
            var children = new java.util.ArrayList<HtmlNode>(List.of(new HtmlText("a")));
            var element = new Element("p", HtmlAttributes.NONE, children);

            children.add(new HtmlText("b"));

            assertEquals(1, element.children().size(), "a record made of a caller's list is not a value");
        }

        @Test
        @DisplayName("with no attributes shares the empty one")
        void noAttributes() {
            assertSame(HtmlAttributes.NONE, new Element("hr").attributes());
            assertSame(HtmlAttributes.NONE, new Element("p", HtmlAttributes.NONE, List.of()).attributes());
        }

        @Test
        @DisplayName("reads its id and classes off its attributes")
        void idAndClasses() {
            var map = new LinkedHashMap<String, String>();
            map.put("id", "intro");
            map.put("class", "lead  wide");
            var p = new Element("p", HtmlAttributes.of(map), List.of());

            assertEquals("intro", p.id());
            assertEquals(List.of("lead", "wide"), p.classes());
        }
    }

    @Nested
    @DisplayName("attributes")
    class Attributes {

        @Test
        @DisplayName("keep the order they were written in, so a tag can be written back out")
        void ordered() {
            var map = new LinkedHashMap<String, String>();
            map.put("href", "/a");
            map.put("title", "A");
            map.put("rel", "next");

            assertEquals(
                    List.of("href", "title", "rel"),
                    List.copyOf(HtmlAttributes.of(map).byName().keySet()));
        }

        @Test
        @DisplayName("lower-case their names, because HTML does not care and a fold does")
        void loweredNames() {
            assertEquals("/a", HtmlAttributes.of("HREF", "/a").value("href"));
            assertTrue(HtmlAttributes.of("HREF", "/a").has("hReF"));
        }

        @Test
        @DisplayName("tell absent from empty")
        void absentIsNotEmpty() {
            var attributes = HtmlAttributes.of("href", "");

            assertEquals("", attributes.value("href"));
            assertTrue(attributes.has("href"));
            assertNull(attributes.value("title"));
            assertFalse(attributes.has("title"));
        }

        @Test
        @DisplayName("refuse a null value, which would be a third state nobody can read")
        void nullValueRefused() {
            var map = new java.util.HashMap<String, String>();
            map.put("href", null);

            assertThrows(NullPointerException.class, () -> HtmlAttributes.of(map));
        }

        @Test
        @DisplayName("read no classes off a missing or blank class attribute")
        void noClasses() {
            assertEquals(List.of(), HtmlAttributes.NONE.classes());
            assertEquals(List.of(), HtmlAttributes.of("class", "   ").classes());
        }

        @Test
        @DisplayName("are immutable, however the map they were made from changes")
        void immutable() {
            var map = new LinkedHashMap<String, String>();
            map.put("a", "1");
            var attributes = HtmlAttributes.of(map);
            map.put("b", "2");

            assertEquals(1, attributes.byName().size());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> attributes.byName().put("c", "3"));
        }

        @Test
        @DisplayName("an empty map is the shared empty one")
        void emptyIsShared() {
            assertSame(HtmlAttributes.NONE, HtmlAttributes.of(Map.of()));
        }
    }

    @Nested
    @DisplayName("the text of a subtree")
    class Text {

        @Test
        @DisplayName("is its words, with a space where a tag was")
        void spacedAtBoundaries() {
            // The failure this exists for: `<b>bold</b>text` read back as "boldtext",
            // which is what inserting a space only in front of a tag produces.
            var p = element("p", element("b", new HtmlText("bold")), new HtmlText("text"));

            assertEquals("bold text", p.text());
        }

        @Test
        @DisplayName("skips a comment, a script and a stylesheet")
        void skipsWhatIsNotRead() {
            var div = element(
                    "div",
                    new Comment(" hidden "),
                    element("script", new HtmlText("var x = 1")),
                    element("style", new HtmlText("p{}")),
                    new HtmlText("visible"));

            assertEquals("visible", div.text());
        }

        @Test
        @DisplayName("and rawText inserts nothing, which is what a pre holds")
        void rawKeepsSpacing() {
            var pre = element("pre", element("code", new HtmlText("  two  spaces\n  and a line")));

            assertEquals("  two  spaces\n  and a line", pre.rawText());
        }

        @Test
        @DisplayName("a document answers the same question from the root")
        void fromTheDocument() {
            var document =
                    new HtmlDocument(List.of(element("h1", new HtmlText("Title")), element("p", new HtmlText("Body"))));

            assertEquals("Title Body", document.text());
        }
    }

    @Nested
    @DisplayName("find")
    class Find {

        @Test
        @DisplayName("answers in document order, at any depth")
        void inOrder() {
            var document = new HtmlDocument(
                    List.of(element("h2", new HtmlText("One")), element("div", element("h2", new HtmlText("Two")))));

            assertEquals(
                    List.of("One", "Two"),
                    document.find("h2").stream().map(Element::text).toList());
        }

        @Test
        @DisplayName("is case-insensitive about the tag asked for")
        void anyCase() {
            assertEquals(1, new HtmlDocument(List.of(element("a"))).find("A").size());
        }

        @Test
        @DisplayName("answers about descendants and not about the element asked")
        void descendantsOnly() {
            var outer = element("div", element("div"));

            assertEquals(1, outer.find("div").size(), "`div.find(\"div\")` means the divs inside this one");
        }

        @Test
        @DisplayName("and is empty rather than null for a tag nothing has")
        void nothingFound() {
            assertEquals(List.of(), new HtmlDocument(List.of(element("p"))).find("table"));
        }
    }

    @Nested
    @DisplayName("what HTML says about a tag")
    class TagFacts {

        @Test
        @DisplayName("void elements, in any case")
        void voidElements() {
            assertTrue(Tags.isVoid("br"));
            assertTrue(Tags.isVoid("IMG"));
            assertFalse(Tags.isVoid("div"));
        }

        @Test
        @DisplayName("inline is a list and block is everything else, including the unknown")
        void inlineAndBlock() {
            assertTrue(Tags.isInline("em"));
            assertTrue(Tags.isBlock("div"));
            // The default that matters: `<my-callout>` full of paragraphs stacks. The
            // other way round would put a page's sections side by side.
            assertTrue(Tags.isBlock("my-callout"));
        }

        @Test
        @DisplayName("raw text is the two elements whose content is not markup")
        void rawText() {
            assertTrue(Tags.isRawText("script"));
            assertTrue(Tags.isRawText("style"));
            assertFalse(Tags.isRawText("pre"), "a pre keeps its spacing and is still markup");
        }

        @Test
        @DisplayName("preformatted is about whitespace, which is a different question")
        void preformatted() {
            assertTrue(Tags.isPreformatted("pre"));
            assertFalse(Tags.isPreformatted("code"));
        }

        @Test
        @DisplayName("metadata is what a content renderer draws nothing for")
        void metadata() {
            assertTrue(Tags.isMetadata("head"));
            assertTrue(Tags.isMetadata("title"));
            assertFalse(Tags.isMetadata("body"));
        }

        @Test
        @DisplayName("a heading answers which one it is, and everything else answers zero")
        void headings() {
            assertEquals(1, Tags.headingLevel("h1"));
            assertEquals(6, Tags.headingLevel("H6"));
            assertEquals(0, Tags.headingLevel("h7"));
            assertEquals(0, Tags.headingLevel("hr"));
            assertEquals(0, Tags.headingLevel("header"));
            assertEquals(0, Tags.headingLevel("h"));
        }
    }

    @Nested
    @DisplayName("the leaves")
    class Leaves {

        @Test
        @DisplayName("text and a comment have no children")
        void noChildren() {
            assertEquals(List.of(), new HtmlText("a").children());
            assertEquals(List.of(), new Comment("a").children());
        }

        @Test
        @DisplayName("text knows whether it is only whitespace, which is a renderer's question")
        void blankText() {
            assertTrue(new HtmlText("\n  ").isBlank());
            assertFalse(new HtmlText("a").isBlank());
        }

        @Test
        @DisplayName("and neither takes a null")
        void nullRefused() {
            assertThrows(NullPointerException.class, () -> new HtmlText(null));
            assertThrows(NullPointerException.class, () -> new Comment(null));
        }

        @Test
        @DisplayName("an empty document is the shared one")
        void emptyDocument() {
            assertTrue(HtmlDocument.EMPTY.children().isEmpty());
            assertEquals("", HtmlDocument.EMPTY.text());
        }
    }
}
