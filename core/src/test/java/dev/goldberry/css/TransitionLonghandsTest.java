package dev.goldberry.css;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.cascade.Transitions;
import dev.goldberry.css.cascade.Transitions.Animatable;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.motion.Easing;

/// `transition: all`, the four `transition-*` longhands, and which sheets may
/// write which curves.
class TransitionLonghandsTest {

    private static Transitions transitions(String css) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var declarations = new StyleResolver(List.of(sheet)).resolve(element("button"));
        return ComputedStyle.of(declarations, CssLength.Context.DEFAULT).transitions();
    }

    @Nested
    @DisplayName("transition: all")
    class All {

        @Test
        @DisplayName("names every animatable property")
        void everything() {
            var all = transitions("button { transition: all 200ms ease-exit }");
            for (var property : Animatable.values()) {
                assertEquals(200, all.get(property).durationMillis(), property.cssName());
                assertEquals(Easing.EASE_EXIT, all.get(property).easing());
            }
        }

        @Test
        @DisplayName("gives way to a later entry for one of them, and wins over an earlier one")
        void order() {
            var later = transitions("button { transition: all 200ms, opacity 50ms }");
            assertEquals(50, later.get(Animatable.OPACITY).durationMillis());
            assertEquals(200, later.get(Animatable.COLOR).durationMillis());
            var earlier = transitions("button { transition: opacity 50ms, all 200ms }");
            assertEquals(200, earlier.get(Animatable.OPACITY).durationMillis());
        }
    }

    @Nested
    @DisplayName("the longhands")
    class Longhands {

        @Test
        @DisplayName("pair up by position, the shorter lists repeated")
        void paired() {
            var timings = transitions("button { transition-property: opacity, color, transform;"
                    + " transition-duration: 100ms, 200ms; transition-timing-function: linear;"
                    + " transition-delay: 5ms }");
            assertEquals(100, timings.get(Animatable.OPACITY).durationMillis());
            assertEquals(200, timings.get(Animatable.COLOR).durationMillis());
            assertEquals(100, timings.get(Animatable.TRANSFORM).durationMillis(), "the durations start over");
            assertEquals(Easing.LINEAR, timings.get(Animatable.TRANSFORM).easing());
            assertEquals(5, timings.get(Animatable.COLOR).delayMillis());
        }

        @Test
        @DisplayName("a later rule's longhand changes what an earlier shorthand set")
        void overShorthand() {
            var timings = transitions(
                    "button { transition: opacity 100ms ease-exit }" + " button { transition-duration: 300ms }");
            assertEquals(300, timings.get(Animatable.OPACITY).durationMillis());
            assertEquals(Easing.EASE_EXIT, timings.get(Animatable.OPACITY).easing());
        }

        @Test
        @DisplayName("a duration alone animates nothing until a property is named")
        void durationAlone() {
            assertTrue(transitions("button { transition-duration: 300ms }").isEmpty());
        }

        @Test
        @DisplayName("transition-property: none turns the list off")
        void none() {
            assertNull(transitions("button { transition: opacity 1s; transition-property: none }")
                    .get(Animatable.OPACITY));
        }

        @Test
        @DisplayName("a property that cannot transition drops the declaration")
        void layoutRefused() {
            var timings = transitions("button { transition: opacity 1s } button { transition-property: width }");
            assertEquals(1000, timings.get(Animatable.OPACITY).durationMillis());
        }
    }

    @Nested
    @DisplayName("which sheet may write which curve")
    class Sheets {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "a { transition: opacity 1s ease }",
                    "a { transition: opacity 1s cubic-bezier(0.1, 0.7, 1, 0.1) }",
                    "a { animation: spin 1s steps(4) }",
                    "a { animation-timing-function: step-end }",
                    "a { transition-timing-function: linear(0, 1) }",
                })
        @DisplayName("a strict sheet refuses any curve but the three, as outside its subset")
        void strictRefuses(String css) {
            var thrown = assertThrows(
                    CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.STRICT, "controls.css"));
            assertTrue(thrown.isUnsupportedFeature());
            assertTrue(thrown.getMessage().contains("design system's curves"), thrown.getMessage());
        }

        @Test
        @DisplayName("and reads the three, and a name inside var() it cannot see")
        void strictReads() {
            CssParser.parseSheet(
                    "a { transition: opacity 1s ease-enter, color 1s linear; animation: spin 1s ease-exit;"
                            + " transition-duration: var(--gb-motion-fast) }",
                    ParseMode.STRICT,
                    "controls.css");
        }

        @Test
        @DisplayName("a lenient sheet keeps every curve")
        void lenientKeeps() {
            var parsed = CssParser.parseSheet(
                    "a { transition: opacity 1s cubic-bezier(0.1, 0.7, 1, 0.1) }", ParseMode.LENIENT, "app.css");
            assertTrue(parsed.dropped().isEmpty());
            assertEquals(1, parsed.rules().getFirst().declarations().size());
        }
    }
}
