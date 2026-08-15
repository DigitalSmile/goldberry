package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/// The Java side of Yoga's enums.
///
/// Whether the *values* are right is not decided here — that is the layout
/// verifier's job, and it asks the C compiler. What is checked here is
/// everything that would stop the verifier from asking: a constant missing from
/// [YogaEnum#all()] is a constant nothing compares against, and it would pass
/// silently forever.
class YogaEnumTest {

    static List<YogaEnum> everyConstant() {
        return YogaEnum.all();
    }

    @ParameterizedTest
    @MethodSource("everyConstant")
    @DisplayName("every constant names a C enumerator")
    void everyConstantNamesAnEnumerator(YogaEnum constant) {
        assertTrue(
                constant.nativeName().startsWith("YG"),
                () -> constant + " reports the C name " + constant.nativeName());
        assertTrue(constant.nativeValue() >= 0, () -> constant + " has a negative value");
    }

    @Test
    @DisplayName("no constant is left out of the registry")
    void theRegistryCoversEveryPermittedEnum() {
        // The sealed interface's permits clause is the authority. An enum added
        // there but forgotten in all() would never reach the layout verifier, so
        // its values would go unchecked -- which is the one failure mode this
        // whole mechanism exists to prevent.
        var registered = new HashSet<>(YogaEnum.all());
        var missing = new ArrayList<String>();

        for (var permitted : YogaEnum.class.getPermittedSubclasses()) {
            var constants = permitted.getEnumConstants();
            assertTrue(constants != null && constants.length > 0, permitted + " has no constants");
            for (var constant : constants) {
                if (!registered.contains(constant)) {
                    missing.add(permitted.getSimpleName() + "." + constant);
                }
            }
        }

        assertEquals(List.of(), missing, "constants missing from YogaEnum.all()");
    }

    @Test
    @DisplayName("no two constants of one enum share a value")
    void valuesAreDistinctWithinAnEnum() {
        for (var permitted : YogaEnum.class.getPermittedSubclasses()) {
            var seen = new HashSet<Integer>();
            for (var constant : permitted.getEnumConstants()) {
                var value = ((YogaEnum) constant).nativeValue();
                assertTrue(
                        seen.add(value),
                        () -> permitted.getSimpleName() + " has two constants with value " + value);
            }
        }
    }

    @Test
    @DisplayName("no two constants anywhere share a C name")
    void namesAreUnique() {
        var seen = new HashSet<String>();
        for (var constant : YogaEnum.all()) {
            assertTrue(
                    seen.add(constant.nativeName()),
                    () -> "two constants both claim to be " + constant.nativeName());
        }
    }

    @Test
    @DisplayName("align and justify are not interchangeable")
    void alignAndJustifyDisagreeAboutCentre() {
        // CSS spells them the same and Yoga numbers them differently. Sharing
        // one Java enum between the two would put a plausible wrong value on the
        // wire, and the result would be a layout that is merely off-centre.
        assertEquals(2, Align.CENTER.nativeValue());
        assertEquals(1, Justify.CENTER.nativeValue());
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    @DisplayName("a direction survives the round trip back from Yoga")
    void directionRoundTrips(Direction direction) {
        assertSame(direction, Direction.of(direction.nativeValue()));
    }

    @Test
    @DisplayName("a direction Yoga does not define is rejected, not guessed")
    void unknownDirectionIsRejected() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> Direction.of(7));

        assertTrue(thrown.getMessage().contains("YGDirection 7"), thrown.getMessage());
    }

    @Test
    @DisplayName("only the four physical sides answer a computed-layout question")
    void physicalSidesAreTheFourSides() {
        for (var edge : List.of(Edge.LEFT, Edge.TOP, Edge.RIGHT, Edge.BOTTOM)) {
            assertTrue(edge.isPhysicalSide(), edge + " is a side");
        }
        for (var edge : List.of(Edge.START, Edge.END, Edge.HORIZONTAL, Edge.VERTICAL, Edge.ALL)) {
            assertTrue(!edge.isPhysicalSide(), edge + " is not a single side");
        }
    }
}
