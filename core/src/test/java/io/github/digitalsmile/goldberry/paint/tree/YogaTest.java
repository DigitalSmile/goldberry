package io.github.digitalsmile.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.layout.Align;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Justify;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.layout.Wrap;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;

/// The one file where the toolkit's flexbox vocabulary meets the engine's.
///
/// ## What can go wrong here, and what cannot
///
/// The compiler already guarantees the `switch`es are **exhaustive** — both
/// enumerations are sealed or enum, so a constant added on either side stops this
/// building. What it cannot guarantee is that each arm names the *right*
/// counterpart: `case CENTER -> Align.FLEX_END` compiles perfectly and moves
/// every centred row in the toolkit to one end.
///
/// So every constant is checked by **name**, generically, from `values()` rather
/// than from a list written here. A constant added to either vocabulary is
/// therefore checked the day it appears, and a translation that transposed two
/// arms fails on both of them (ADR-0279).
///
/// The names agreeing is not a coincidence to be relied on elsewhere — the
/// *numbers* deliberately do not travel, which is the whole reason these are
/// `switch`es and not ordinal casts. The names agreeing is simply what makes an
/// automatic check possible, and if a future constant has to be renamed on one
/// side this test is where that decision gets written down.
class YogaTest {

    @Test
    @DisplayName("every direction translates to the one with its own name")
    void directions() {
        assertNamesMatch(FlexDirection.values(), Yoga::direction, Enum::name);
    }

    @Test
    @DisplayName("every justification translates to the one with its own name")
    void justifications() {
        assertNamesMatch(Justify.values(), Yoga::justify, Enum::name);
    }

    @Test
    @DisplayName("every alignment translates to the one with its own name")
    void alignments() {
        // The nine-value one, and the one most likely to be transposed: `Align`
        // and `Justify` share five constant names and agree on none of their
        // numbers.
        assertNamesMatch(Align.values(), Yoga::align, Enum::name);
    }

    @Test
    @DisplayName("every wrap translates to the one with its own name")
    void wraps() {
        assertNamesMatch(Wrap.values(), Yoga::wrap, Enum::name);
    }

    @Test
    @DisplayName("every overflow translates to the one with its own name")
    void overflows() {
        assertNamesMatch(Overflow.values(), Yoga::overflow, Enum::name);
    }

    @Test
    @DisplayName("every position translates to the one with its own name")
    void positions() {
        // Renamed on the toolkit's side -- CSS calls this `position` and the
        // binding calls it `PositionType` -- so only the constants line up.
        assertNamesMatch(Position.values(), Yoga::position, Enum::name);
    }

    @Test
    @DisplayName("a length keeps its unit and its number")
    void lengths() {
        assertEquals(StyleLength.points(8), Yoga.length(Length.points(8)));
        assertEquals(StyleLength.percent(50), Yoga.length(Length.percent(50)));
        // Identity, not equality: `Limits.isNone` compares the engine's keywords
        // with `==` too.
        assertSame(StyleLength.AUTO, Yoga.length(Length.AUTO));
        assertSame(StyleLength.UNDEFINED, Yoga.length(Length.UNDEFINED));
    }

    @Test
    @DisplayName("insets keep CSS's order across the boundary")
    void insetsKeepTheirOrder() {
        // Four values of one type, which is exactly the shape a transposition
        // hides in: swapping right and left here mirrors every asymmetric
        // padding in the toolkit and nothing else changes.
        var translated =
                Yoga.insets(new Insets(Length.points(1), Length.points(2), Length.points(3), Length.points(4)));

        assertEquals(StyleLength.points(1), translated.top());
        assertEquals(StyleLength.points(2), translated.right());
        assertEquals(StyleLength.points(3), translated.bottom());
        assertEquals(StyleLength.points(4), translated.left());
    }

    @Test
    @DisplayName("a laid-out rectangle keeps its corner and its extent")
    void rectangles() {
        var rect = Yoga.rect(new io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout(10, 20, 30, 40));

        assertEquals(10, rect.left());
        assertEquals(20, rect.top());
        assertEquals(30, rect.width());
        assertEquals(40, rect.height());
    }

    /// Asserts that each of `values` translates to something with the same name.
    ///
    /// Generic and driven from `values()`, so a constant added to either
    /// vocabulary is checked without this file being touched — which is the same
    /// rule `ExportedSurfaceTest` follows for the module descriptor.
    private static <T extends Enum<T>, R> void assertNamesMatch(
            T[] values, Function<T, R> translate, Function<R, String> nameOf) {

        var mapped = Arrays.stream(values)
                .collect(Collectors.toMap(Enum::name, value -> nameOf.apply(translate.apply(value))));
        for (var entry : mapped.entrySet()) {
            assertEquals(
                    entry.getKey(),
                    entry.getValue(),
                    () -> "the translation of " + entry.getKey() + " names " + entry.getValue());
        }
        // And injective: two arms pointing at one constant would pass the check
        // above for whichever of them was named correctly.
        assertEquals(
                values.length, mapped.values().stream().distinct().count(), "two constants translated to the same one");
    }
}
