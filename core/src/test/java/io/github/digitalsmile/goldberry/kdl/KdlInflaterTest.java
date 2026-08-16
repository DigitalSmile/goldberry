package io.github.digitalsmile.goldberry.kdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KdlInflaterTest {

    /// A stand-in for whatever the widget tree will be: enough to prove the
    /// node's own data and its children both reached the factory.
    private record Built(String type, String label, List<Built> children) {
    }

    private static KdlInflater<Built> inflater() {
        var inflater = new KdlInflater<Built>();
        for (var name : List.of("window", "row", "button", "label")) {
            inflater.register(name, (node, children) ->
                    new Built(name, node.argument().map(KdlValue::asString).orElse(null), children));
        }
        return inflater;
    }

    @Nested
    @DisplayName("inflating")
    class Inflating {

        @Test
        @DisplayName("a tree is built depth first, so factories get built children")
        void depthFirst() {
            var built = inflater().inflate(KdlParser.parse("""
                    window {
                      row {
                        button "Apply"
                        button "Cancel"
                      }
                    }
                    """).getFirst());

            assertEquals("window", built.type());
            var row = built.children().getFirst();
            assertEquals("row", row.type());
            assertEquals(List.of("Apply", "Cancel"),
                    row.children().stream().map(Built::label).toList());
        }

        @Test
        @DisplayName("the first argument is the node's primary content, per §9")
        void primaryContent() {
            var built = inflater().inflate(KdlParser.parse("button \"Apply\"").getFirst());
            assertEquals("Apply", built.label());
        }

        @Test
        @DisplayName("a whole document inflates")
        void wholeDocument() {
            var built = inflater().inflateAll(KdlParser.parse("button \"A\"\nbutton \"B\""));
            assertEquals(2, built.size());
        }
    }

    @Nested
    @DisplayName("the registry")
    class Registry {

        @Test
        @DisplayName("an unknown node is a hard error naming the position and what is known")
        void unknownNode() {
            var thrown = assertThrows(KdlSyntaxException.class,
                    () -> inflater().inflate(KdlParser.parse("window {\n  spinner\n}").getFirst()));

            // §9 asks for exactly this: hard errors with source positions.
            assertEquals(2, thrown.line());
            assertTrue(thrown.getMessage().contains("spinner"));
            // And the message lists what would have worked, because a typo is
            // the overwhelmingly likely cause.
            assertTrue(thrown.getMessage().contains("button"));
        }

        @Test
        @DisplayName("registering the same name twice is refused")
        void duplicateRegistration() {
            var inflater = inflater();
            // Shadowing a built-in silently, at whichever point a registration
            // happened to run, is not a good way to find out it happened.
            assertThrows(IllegalStateException.class,
                    () -> inflater.register("button", (node, children) -> null));
        }

        @Test
        @DisplayName("replace() shadows a built-in deliberately")
        void replace() {
            var inflater = inflater();
            inflater.replace("button", (node, children) -> new Built("custom-button", null, children));

            assertEquals("custom-button", inflater.inflate(KdlParser.parse("button \"A\"").getFirst()).type());
        }

        @Test
        @DisplayName("an application widget registers exactly like a built-in")
        void applicationWidget() {
            var inflater = inflater();
            inflater.register("gauge", (node, children) -> new Built("gauge", null, children));

            assertEquals("gauge", inflater.inflate(KdlParser.parse("gauge").getFirst()).type());
        }
    }

    @Nested
    @DisplayName("id lookup")
    class Ids {

        @Test
        @DisplayName("finds a node anywhere in the document")
        void findsNested() {
            var document = KdlParser.parse("""
                    window {
                      row {
                        button id="apply" "Apply"
                      }
                    }
                    """);

            var found = KdlInflater.byId(document, "apply").orElseThrow();
            assertEquals("Apply", found.argument().orElseThrow().asString());
        }

        @Test
        @DisplayName("an absent id is empty rather than an error")
        void absentId() {
            assertTrue(KdlInflater.byId(KdlParser.parse("window"), "nope").isEmpty());
        }

        @Test
        @DisplayName("a duplicated id is refused, with both positions")
        void duplicateId() {
            var document = KdlParser.parse("""
                    window {
                      button id="apply" "One"
                      button id="apply" "Two"
                    }
                    """);

            // Otherwise this shows up much later as a handler firing on the
            // wrong widget.
            var thrown = assertThrows(KdlSyntaxException.class, () -> KdlInflater.byId(document, "apply"));
            assertEquals(3, thrown.line());
            assertTrue(thrown.getMessage().contains("2:"));
        }
    }
}
