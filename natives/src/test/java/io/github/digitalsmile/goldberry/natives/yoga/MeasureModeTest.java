package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class MeasureModeTest {

    /// The literals are Yoga's, from the `YG_ENUM_DECL(YGMeasureMode, ...)` in
    /// `yoga/YGEnums.h`. Asserting them here means reordering the Java enum shows
    /// up as a failure rather than as a layout that is subtly wrong in one mode.
    @ParameterizedTest
    @CsvSource({"UNDEFINED, 0", "EXACTLY, 1", "AT_MOST, 2"})
    @DisplayName("values match YGMeasureMode")
    void valuesMatchYoga(MeasureMode mode, int expected) {
        assertEquals(expected, mode.nativeValue());
    }

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

    @Test
    @DisplayName("the three modes are the only ones Yoga defines")
    void yogaDefinesThree() {
        assertEquals(3, MeasureMode.values().length);
    }
}
