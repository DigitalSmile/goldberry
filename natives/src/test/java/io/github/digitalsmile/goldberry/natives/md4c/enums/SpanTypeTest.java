package io.github.digitalsmile.goldberry.natives.md4c.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// [SpanType]'s table on its own, with no library loaded.
///
/// `LayoutVerificationTest` is what holds these values to md4c's header, and it
/// needs libgoldberry. This is the part that does not: that the table maps each
/// value back to one constant, and that the slot md4c 0.6.0 gave `MD_SPAN_INS`
/// is refused rather than quietly read as a neighbour.
@DisplayName("SpanType")
class SpanTypeTest {

    @ParameterizedTest
    @EnumSource(SpanType.class)
    @DisplayName("maps its own value back to itself")
    void roundTrips(SpanType type) {
        assertEquals(type, SpanType.of(type.nativeValue()));
    }

    @Test
    @DisplayName("gives every constant its own value")
    void valuesAreDistinct() {
        var values =
                Arrays.stream(SpanType.values()).mapToInt(SpanType::nativeValue).toArray();
        assertEquals(values.length, Arrays.stream(values).distinct().count());
    }

    @Test
    @DisplayName("refuses MD_SPAN_INS, which it does not bind")
    void insIsUnbound() {
        var e = assertThrows(IllegalArgumentException.class, () -> SpanType.of(5));
        assertTrue(e.getMessage().contains("MD_SPANTYPE 5"), e.getMessage());
    }

    @Test
    @DisplayName("sits DEL after the MD_SPAN_INS md4c 0.6.0 inserted")
    void delFollowsIns() {
        assertEquals(SpanType.CODE.nativeValue() + 2, SpanType.DEL.nativeValue());
    }
}
