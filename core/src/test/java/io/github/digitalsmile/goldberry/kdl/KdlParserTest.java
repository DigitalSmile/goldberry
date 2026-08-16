package io.github.digitalsmile.goldberry.kdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class KdlParserTest {

    private static KdlNode one(String markup) {
        var nodes = KdlParser.parse(markup);
        assertEquals(1, nodes.size(), () -> "expected one node, got " + nodes);
        return nodes.getFirst();
    }

    private static double number(KdlValue value) {
        return assertInstanceOf(KdlValue.Num.class, value).value();
    }

    @Nested
    @DisplayName("nodes")
    class Nodes {

        @Test
        @DisplayName("a bare node")
        void bareNode() {
            var node = one("separator");
            assertEquals("separator", node.name());
            assertTrue(node.arguments().isEmpty());
            assertTrue(node.children().isEmpty());
        }

        @Test
        @DisplayName("arguments, properties and children in one node")
        void everything() {
            var node = one("""
                    button id="apply" icon="check" "Apply" {
                      tooltip "Applies the change"
                    }
                    """);

            assertEquals("button", node.name());
            // §9: string arguments are the primary content.
            assertEquals("Apply", node.argument().orElseThrow().asString());
            assertEquals("apply", node.stringProperty("id"));
            assertEquals("check", node.stringProperty("icon"));
            assertEquals(1, node.children().size());
            assertEquals("tooltip", node.children().getFirst().name());
        }

        @Test
        @DisplayName("a newline ends a node")
        void newlineEndsANode() {
            var nodes = KdlParser.parse("a\nb\nc");
            assertEquals(List.of("a", "b", "c"), nodes.stream().map(KdlNode::name).toList());
        }

        @Test
        @DisplayName("a semicolon ends a node too")
        void semicolonEndsANode() {
            assertEquals(3, KdlParser.parse("a; b; c").size());
        }

        @Test
        @DisplayName("a backslash continues a node across a newline")
        void escline() {
            var node = one("""
                    button \\
                      id="apply" \\
                      "Apply"
                    """);
            assertEquals("apply", node.stringProperty("id"));
            assertEquals("Apply", node.argument().orElseThrow().asString());
        }

        @Test
        @DisplayName("children nest to depth")
        void nesting() {
            var root = one("""
                    window {
                      row {
                        column {
                          button "Deep"
                        }
                      }
                    }
                    """);
            var button = root.children().getFirst().children().getFirst().children().getFirst();
            assertEquals("button", button.name());
            assertEquals("Deep", button.argument().orElseThrow().asString());
        }

        @Test
        @DisplayName("a repeated property keeps the last, as KDL specifies")
        void repeatedProperty() {
            assertEquals("second", one("a k=\"first\" k=\"second\"").stringProperty("k"));
        }

        @Test
        @DisplayName("nodes remember where they came from")
        void positions() {
            var nodes = KdlParser.parse("a\n\n  b");
            assertEquals(1, nodes.get(0).line());
            assertEquals(3, nodes.get(1).line());
            assertEquals(3, nodes.get(1).column());
        }
    }

    @Nested
    @DisplayName("values")
    class Values {

        @Test
        @DisplayName("quoted strings, with escapes")
        void strings() {
            assertEquals("a\nb", one("a x=\"a\\nb\"").stringProperty("x"));
            assertEquals("a\"b", one("a x=\"a\\\"b\"").stringProperty("x"));
            assertEquals("→", one("a x=\"\\u{2192}\"").stringProperty("x"));
        }

        @Test
        @DisplayName("a raw string takes no escapes")
        void rawStrings() {
            // The point of a raw string: a Windows path or a regex without
            // doubling every backslash.
            assertEquals("C:\\Users\\n", one("a x=#\"C:\\Users\\n\"#").stringProperty("x"));
        }

        @Test
        @DisplayName("a raw string can fence around a quote")
        void rawStringFencing() {
            assertEquals("say \"#\" here", one("a x=##\"say \"#\" here\"##").stringProperty("x"));
        }

        @ParameterizedTest
        @CsvSource({
                "'a x=1', 1",
                "'a x=-2.5', -2.5",
                "'a x=1.5e3', 1500",
                "'a x=1_000', 1000",
                "'a x=0xff', 255",
                "'a x=0o17', 15",
                "'a x=0b1010', 10",
        })
        @DisplayName("numbers, in every radix KDL allows")
        void numbers(String markup, double expected) {
            assertEquals(expected, number(one(markup).property("x").orElseThrow()));
        }

        @Test
        @DisplayName("keywords are #-prefixed in KDL 2.0")
        void keywords() {
            // The change from 1.0 that a parser written from memory gets wrong:
            // bare `true` is not a boolean, it is not even a legal argument.
            assertEquals(new KdlValue.Bool(true), one("a x=#true").property("x").orElseThrow());
            assertEquals(new KdlValue.Bool(false), one("a x=#false").property("x").orElseThrow());
            assertSame(KdlValue.Null.NULL, one("a x=#null").property("x").orElseThrow());
            assertTrue(Double.isInfinite(number(one("a x=#inf").property("x").orElseThrow())));
            assertTrue(Double.isNaN(number(one("a x=#nan").property("x").orElseThrow())));
        }

        @Test
        @DisplayName("a bare word is not a value")
        void bareWordsAreNotValues() {
            // KDL 2.0 removed bare-word arguments; `enabled true` is an error
            // rather than a string "true", which is what makes #true necessary.
            assertThrows(KdlSyntaxException.class, () -> KdlParser.parse("a true"));
        }

        @Test
        @DisplayName("a quoted node name is allowed")
        void quotedIdentifiers() {
            assertEquals("has space", one("\"has space\" x=1").name());
        }

        @Test
        @DisplayName("asInt refuses a number that is not whole")
        void asInt() {
            var whole = (KdlValue.Num) one("a x=720").property("x").orElseThrow();
            assertEquals(720, whole.asInt());

            var fractional = (KdlValue.Num) one("a x=7.5").property("x").orElseThrow();
            // Truncating silently is how a window ends up a pixel narrow with no
            // explanation.
            assertThrows(KdlSyntaxException.class, fractional::asInt);
        }
    }

    @Nested
    @DisplayName("comments")
    class Comments {

        @Test
        @DisplayName("line and block comments")
        void basic() {
            assertEquals(1, KdlParser.parse("// gone\na\n/* also gone */").size());
        }

        @Test
        @DisplayName("block comments nest")
        void nestingBlockComments() {
            // The one place KDL differs from C, and where a naive scanner ends
            // the comment at the first "*/" and then chokes on the rest.
            assertEquals(1, KdlParser.parse("/* outer /* inner */ still comment */ a").size());
        }

        @Test
        @DisplayName("/- comments out a whole node, children and all")
        void slashdashNode() {
            var nodes = KdlParser.parse("""
                    a
                    /-b {
                      child "kept out too"
                    }
                    c
                    """);
            assertEquals(List.of("a", "c"), nodes.stream().map(KdlNode::name).toList());
        }

        @Test
        @DisplayName("/- comments out a single argument or property")
        void slashdashEntry() {
            var node = one("a /-\"skipped\" \"kept\" /-x=1 y=2");

            assertEquals(1, node.arguments().size());
            assertEquals("kept", node.argument().orElseThrow().asString());
            assertTrue(node.property("x").isEmpty());
            assertEquals(2, number(node.property("y").orElseThrow()));
        }

        @Test
        @DisplayName("/- comments out a child block")
        void slashdashChildren() {
            var node = one("a /-{ b; c }");
            assertTrue(node.children().isEmpty());
        }

        @Test
        @DisplayName("an unterminated block comment is refused")
        void unterminated() {
            assertThrows(KdlSyntaxException.class, () -> KdlParser.parse("a /* oops"));
        }
    }

    @Nested
    @DisplayName("what this subset refuses")
    class Refused {

        @Test
        @DisplayName("type annotations are named rather than dropped")
        void typeAnnotations() {
            var thrown = assertThrows(KdlSyntaxException.class, () -> KdlParser.parse("(u8)a x=1"));
            assertTrue(thrown.getMessage().contains("type annotation"));
        }

        @Test
        @DisplayName("multi-line strings are named rather than mis-reported")
        void multiLineStrings() {
            var thrown = assertThrows(KdlSyntaxException.class,
                    () -> KdlParser.parse("a x=\"\"\"\nhello\n\"\"\""));
            assertTrue(thrown.getMessage().contains("multi-line"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "a {",                 // unclosed child block
                "}",                   // stray close
                "a x=",                // property with no value
                "a \"unterminated",
                "1abc",                // an identifier may not look like a number
        })
        @DisplayName("malformed markup is refused with a position")
        void malformed(String markup) {
            var thrown = assertThrows(KdlSyntaxException.class, () -> KdlParser.parse(markup));
            assertTrue(thrown.line() >= 1, () -> "no position on: " + thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("the document from ARCHITECTURE.md §9")
    class ArchitectureExample {

        @Test
        @DisplayName("parses whole, with the structure §9 describes")
        void settingsWindow() {
            // Lifted from the architecture document, which makes this the test
            // that the documented example is actually valid markup.
            var nodes = KdlParser.parse("""
                    window title="Settings" width=720 height=480 {
                      menubar {
                        menu "File" {
                          item "Save" icon="save" accel="Ctrl+S" action="save"
                          separator
                          item "Quit" accel="Ctrl+Q" action="quit"
                        }
                      }
                      row class="root" {
                        column class="sidebar" {
                          button id="apply" icon="check" "Apply"
                          checkbox id="frost" bind="prefs.frost" "Enable frost"
                          progress id="scan" max=100
                        }
                        scroll {
                          form {
                            field label="Name"  { text-input id="name" placeholder="…" }
                            field label="Theme" { select id="theme" options="light;dark;system" }
                          }
                        }
                      }
                    }
                    """);

            assertEquals(1, nodes.size());
            var window = nodes.getFirst();
            assertEquals("window", window.name());
            assertEquals("Settings", window.stringProperty("title"));
            assertEquals(720, ((KdlValue.Num) window.property("width").orElseThrow()).asInt());

            var menu = window.childrenNamed("menubar").getFirst().childrenNamed("menu").getFirst();
            assertEquals("File", menu.argument().orElseThrow().asString());
            assertEquals(3, menu.children().size());
            assertEquals("separator", menu.children().get(1).name());

            var sidebar = window.childrenNamed("row").getFirst().childrenNamed("column").getFirst();
            assertEquals("sidebar", sidebar.stringProperty("class"));
            var button = sidebar.childrenNamed("button").getFirst();
            assertEquals("Apply", button.argument().orElseThrow().asString());
            assertEquals("apply", button.stringProperty("id"));

            // A hyphenated node name has to survive the bare-identifier rules.
            var input = window.childrenNamed("row").getFirst()
                    .childrenNamed("scroll").getFirst()
                    .childrenNamed("form").getFirst()
                    .childrenNamed("field").getFirst()
                    .childrenNamed("text-input").getFirst();
            assertEquals("name", input.stringProperty("id"));
        }
    }
}
