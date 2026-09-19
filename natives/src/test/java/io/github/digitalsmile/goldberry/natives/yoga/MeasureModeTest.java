package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureMode;

/// `YGMeasureMode`.
///
/// The native values are compared with the compiled Yoga by the layout probe —
/// `MeasureMode` is a `YogaEnum`, so every one of its constants is a row of
/// `NativeConstants.registry()`. What is left for this is the Java side: the round
/// trip, and the refusal that keeps a wrong callback signature from looking like a
/// layout that is merely odd.
class MeasureModeTest {

    @ParameterizedTest
    @EnumSource(MeasureMode.class)
    @DisplayName("every mode survives a round trip through its native value")
    void roundTrips(MeasureMode mode) {
        assertSame(mode, MeasureMode.of(mode.nativeValue()));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3, 4, Integer.MAX_VALUE, Integer.MIN_VALUE})
    @DisplayName("an undefined value is rejected rather than defaulted")
    void unknownValueIsRejected(int value) {
        // Defaulting to UNDEFINED would turn a wrong callback signature into a
        // layout that merely looks odd.
        var thrown = assertThrows(IllegalArgumentException.class, () -> MeasureMode.of(value));

        assertEquals(true, thrown.getMessage().contains(String.valueOf(value)));
    }
}
