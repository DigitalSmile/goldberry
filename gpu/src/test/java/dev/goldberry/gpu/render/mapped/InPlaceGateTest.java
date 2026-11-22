package dev.goldberry.gpu.render.mapped;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/// The check at device creation: medians, the limit, and what a run may ask
/// for instead.
@DisplayName("the in-place gate")
class InPlaceGateTest {

    private static final InPlaceGate GATE = InPlaceGate.DEFAULT;

    @Test
    @DisplayName("is one and a half times heap, over nine pairs")
    void defaults() {
        assertEquals(1.5, GATE.limit());
        assertEquals(9, GATE.pairs());
    }

    @Test
    @DisplayName("lets through memory that paints like heap memory, noise and all")
    void cachedMemory() {
        // A shared runner's spread: mapped from 13% faster to 8% slower.
        long[] heap = {50, 52, 49, 55, 51, 70, 50, 48, 53};
        long[] mapped = {44, 56, 53, 51, 58, 49, 120, 52, 50};
        var verdict = GATE.decide(heap, mapped);
        assertTrue(verdict.on(), verdict::describe);
        assertEquals(52.0 / 51.0, verdict.ratio(), 1e-9);
    }

    @Test
    @DisplayName("stops write-combined memory, which reads several times slower")
    void writeCombinedMemory() {
        long[] heap = {50, 52, 49, 55, 51, 50, 50, 48, 53};
        long[] mapped = {400, 380, 420, 390, 410, 405, 395, 400, 415};
        var verdict = GATE.decide(heap, mapped);
        assertFalse(verdict.on(), verdict::describe);
        assertEquals("8.00× heap", verdict.describe());
    }

    @Test
    @DisplayName("is decided by medians, so one slow sample on either side changes nothing")
    void medians() {
        long[] heap = {10, 10, 10, 10, 10_000};
        long[] mapped = {14, 14, 14, 14_000, 14};
        assertEquals(1.4, GATE.decide(heap, mapped).ratio(), 1e-9);
        assertTrue(GATE.decide(heap, mapped).on());
        assertFalse(new InPlaceGate(1.3, 5).decide(heap, mapped).on(), "a tighter limit stops it");
    }

    @Test
    @DisplayName("refuses a limit that is not above 1, no pairs, and samples that do not pair")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> new InPlaceGate(1.0, 9));
        assertThrows(IllegalArgumentException.class, () -> new InPlaceGate(Double.NaN, 9));
        assertThrows(IllegalArgumentException.class, () -> new InPlaceGate(1.5, 0));
        assertThrows(IllegalArgumentException.class, () -> GATE.decide(new long[2], new long[3]));
        assertThrows(IllegalArgumentException.class, () -> GATE.decide(new long[0], new long[0]));
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource(
            value = {
                "on, ON",
                "ON, ON",
                "true, ON",
                "off, OFF",
                "false, OFF",
                "auto, AUTO",
                "'', AUTO",
                "sometimes, AUTO"
            })
    @DisplayName("a run asks for on, off or auto, and a typo is auto")
    void modes(String value, InPlaceProbe.Mode mode) {
        assertEquals(mode, InPlaceProbe.Mode.parse(value));
    }

    @Test
    @DisplayName("an unset mode is auto")
    void unsetMode() {
        assertEquals(InPlaceProbe.Mode.AUTO, InPlaceProbe.Mode.parse(null));
    }

    @Test
    @DisplayName("says why in the log: measured, asked, or a check that failed")
    void describes() {
        var measured = new InPlaceProbe.Decision(
                true, InPlaceProbe.Mode.AUTO, Optional.of(new InPlaceGate.Verdict(1.02, true)));
        assertEquals("on (1.02× heap)", measured.describe());
        assertEquals(
                "off (asked)", new InPlaceProbe.Decision(false, InPlaceProbe.Mode.OFF, Optional.empty()).describe());
        assertEquals(
                "off (the check failed)",
                new InPlaceProbe.Decision(false, InPlaceProbe.Mode.AUTO, Optional.empty()).describe());
    }
}
