package io.github.digitalsmile.goldberry.css.cascade;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.parse.Token;

/// What the cascade does with `@starting-style` ([ADR-0352]) and `@keyframes`
/// ([ADR-0353]).
class MotionCascadeTest {

    private static StyleResolver resolver(String css) {
        return new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)));
    }

    private static String value(Map<String, List<Token>> resolved, String property) {
        var tokens = resolved.get(property);
        if (tokens == null) {
            return null;
        }
        var text = new StringBuilder();
        tokens.forEach(token -> text.append(token.cssText()));
        return text.toString();
    }

    @Nested
    @DisplayName("starting styles")
    class Starting {

        private static final String CSS = """
                button { opacity: 1; color: red }
                @starting-style {
                  button.float { opacity: 0 }
                }
                """;

        @Test
        @DisplayName("a starting rule is never part of the style an element has")
        void notInTheOrdinaryCascade() {
            assertEquals("1", value(resolver(CSS).resolve(element("button.float")), "opacity"));
        }

        @Test
        @DisplayName("the starting style is the ordinary cascade with the starting rules added")
        void addedToTheOrdinaryCascade() {
            var starting = resolver(CSS).resolveStarting(element("button.float"));

            assertEquals("0", value(starting, "opacity"), "the starting rule wins where it speaks");
            assertEquals("red", value(starting, "color"), "and everything else is what the element has");
        }

        @Test
        @DisplayName("an element no starting rule matches has no starting style at all")
        void noneWhenNothingMatches() {
            assertNull(resolver(CSS).resolveStarting(element("button")));
        }

        @Test
        @DisplayName("a sheet with no starting rules says so without matching anything")
        void cheapWhenAbsent() {
            var resolver = resolver("button { opacity: 1 }");

            assertFalse(resolver.hasStartingStyles());
            assertNull(resolver.resolveStarting(element("button")));
            assertTrue(resolver(CSS).hasStartingStyles());
        }

        @Test
        @DisplayName("source order decides between a starting rule and a later ordinary one of equal weight")
        void sourceOrder() {
            var resolver = resolver("""
                    @starting-style { a { opacity: 0 } }
                    a { opacity: 0.5 }
                    """);

            assertEquals("0.5", value(resolver.resolveStarting(element("a")), "opacity"));
        }

        @Test
        @DisplayName("a var() in a starting rule means the element's token")
        void substitutes() {
            var resolver = resolver("""
                    a { --lift: 8px }
                    @starting-style { a { transform: translateY(var(--lift)) } }
                    """);

            assertEquals("translateY(8px)", value(resolver.resolveStarting(element("a")), "transform"));
        }
    }

    @Nested
    @DisplayName("keyframes")
    class KeyframeBlocks {

        @Test
        @DisplayName("a later block with the same name replaces an earlier one whole")
        void laterWins() {
            var resolver = new StyleResolver(List.of(
                    Stylesheet.parse(CascadeLayer.TOOLKIT_BASE, "@keyframes pulse { from { opacity: 0 } }"),
                    Stylesheet.parse(CascadeLayer.APPLICATION, "@keyframes pulse { to { color: red } }")));

            var block = resolver.keyframes("pulse");
            assertEquals(1, block.frames().size());
            assertEquals(1.0, block.frames().getFirst().offset());
        }

        @Test
        @DisplayName("an unknown name is no block")
        void unknown() {
            assertNull(resolver("a { color: red }").keyframes("pulse"));
        }

        @Test
        @DisplayName("a keyframe's var() is substituted for the element it runs on")
        void substitutedPerElement() {
            var resolver = resolver("""
                    .a { --glaze: #88c0d0 }
                    .b { --glaze: #a3be8c }
                    @keyframes glaze { to { background-color: var(--glaze) } }
                    """);
            var frame = resolver.keyframes("glaze").frames().getFirst();

            assertEquals("#88c0d0", value(resolver.resolveKeyframe(element("tile.a"), frame), "background-color"));
            assertEquals("#a3be8c", value(resolver.resolveKeyframe(element("tile.b"), frame), "background-color"));
        }
    }
}
