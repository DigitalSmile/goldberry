package io.github.digitalsmile.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.KeyframeAnimations;
import io.github.digitalsmile.goldberry.css.cascade.KeyframeAnimations.Direction;
import io.github.digitalsmile.goldberry.css.cascade.KeyframeAnimations.FillMode;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.motion.Easing;

/// `animation` and its seven longhands, resolved ([ADR-0353]).
class AnimationPropertyTest {

    private static KeyframeAnimations resolve(String declarations) {
        var resolver =
                new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "tile { " + declarations + " }")));
        return ComputedStyle.of(resolver.resolve(TestElement.element("tile")), CssLength.Context.DEFAULT)
                .animations();
    }

    @Test
    @DisplayName("nothing animates by default")
    void noneByDefault() {
        assertTrue(ComputedStyle.INITIAL.animations().isEmpty());
        assertTrue(resolve("color: red").isEmpty());
    }

    @Test
    @DisplayName("the shorthand reads every part in any order, the first time a duration and the second a delay")
    void shorthand() {
        var entry = resolve("animation: alternate tile-drop infinite 850ms ease-exit 120ms both")
                .entries()
                .getFirst();

        assertEquals(
                new KeyframeAnimations.Entry(
                        "tile-drop",
                        850,
                        Easing.EASE_EXIT,
                        120,
                        Double.POSITIVE_INFINITY,
                        Direction.ALTERNATE,
                        FillMode.BOTH),
                entry);
    }

    @Test
    @DisplayName("the shorthand's defaults are CSS's, with §1.7's enter curve")
    void shorthandDefaults() {
        assertEquals(
                new KeyframeAnimations.Entry("pulse", 1000, Easing.EASE_ENTER, 0, 1, Direction.NORMAL, FillMode.NONE),
                resolve("animation: pulse 1s").entries().getFirst());
    }

    /// The one place a unitless number and a `<time>` collide.
    ///
    /// `milliseconds` accepts a unitless zero, which is right for `transition` —
    /// `0` has no duration to be wrong about, and `transition` has no count for it
    /// to be instead. It was asked first here, so the `0` below became the delay
    /// and the count stayed at its default of one: an animation asked to run no
    /// times ran once.
    @Test
    @DisplayName("a bare 0 is an iteration count, not a time — `animation: spin 1s 0` runs it no times")
    void aBareZeroIsACount() {
        var entry = resolve("animation: spin 1s 0").entries().getFirst();

        assertEquals(0, entry.iterations(), "a bare number in this shorthand is the count");
        assertEquals(1000, entry.durationMillis());
        assertEquals(0, entry.delayMillis());
    }

    @Test
    @DisplayName("and a zero delay still has to say so, which is what a unit is for")
    void aZeroDelayNeedsItsUnit() {
        var entry = resolve("animation: spin 1s 0s 3").entries().getFirst();

        assertEquals(1000, entry.durationMillis());
        assertEquals(0, entry.delayMillis());
        assertEquals(3, entry.iterations());
    }

    @Test
    @DisplayName("a later longhand changes one part of an animation an earlier rule named — a stagger")
    void longhandsOverlay() {
        var resolver = new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                tile { animation: tile-drop 850ms both }
                tile.second { animation-delay: 60ms }
                """)));
        var style = ComputedStyle.of(resolver.resolve(TestElement.element("tile.second")), CssLength.Context.DEFAULT);

        var entry = style.animations().entries().getFirst();
        assertEquals("tile-drop", entry.name());
        assertEquals(60, entry.delayMillis());
        assertEquals(FillMode.BOTH, entry.fillMode());
    }

    @Test
    @DisplayName("names decide how many run, and shorter lists repeat")
    void listsRepeat() {
        var entries =
                resolve("animation-name: a, b, c; animation-duration: 1s, 2s").entries();

        assertEquals(
                List.of("a", "b", "c"),
                entries.stream().map(KeyframeAnimations.Entry::name).toList());
        assertEquals(
                List.of(1000.0, 2000.0, 1000.0),
                entries.stream().map(e -> e.durationMillis()).toList());
    }

    @Test
    @DisplayName("`none` turns every animation off")
    void noneTurnsOff() {
        assertSame(KeyframeAnimations.NONE, resolve("animation: pulse 1s; animation: none"));
        assertTrue(resolve("animation: pulse 1s; animation-name: none").isEmpty());
    }

    /// Values are checked when a style is computed rather than when a sheet is
    /// parsed, so a bad declaration still wins the cascade and is then dropped:
    /// the property is left at what it would have been with nothing declared.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "animation: 1s",
                "animation: a b 1s",
                "animation: a 1s 2s 3s",
                "animation: a -1s",
                "animation: a 1s ease-in-out",
            })
    @DisplayName("a shorthand that is not in the subset is dropped whole, and nothing runs")
    void droppedShorthand(String declaration) {
        assertTrue(resolve(declaration).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "animation-duration: -5ms",
                "animation-iteration-count: -1",
                "animation-direction: sideways",
                "animation-fill-mode: always",
                "animation-name: 4px",
                "animation-delay: 1s 2s",
            })
    @DisplayName("a longhand that is not in the subset is dropped, and the shorthand it would have changed stands")
    void droppedLonghand(String declaration) {
        var survived = resolve("animation: kept 300ms; " + declaration);

        assertEquals("kept", survived.entries().getFirst().name());
        assertEquals(300, survived.entries().getFirst().durationMillis());
    }

    @Test
    @DisplayName("reduced motion turns every animation off, where it makes transitions instant")
    void reduced() {
        assertTrue(resolve("animation: pulse 1s infinite").reduced().isEmpty());
    }
}
