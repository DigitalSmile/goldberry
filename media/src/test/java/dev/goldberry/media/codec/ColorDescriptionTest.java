package dev.goldberry.media.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ColorDescription")
class ColorDescriptionTest {

    @ParameterizedTest(name = "ColourPrimaries {0} is {1}")
    @CsvSource({
        "0, UNSPECIFIED",
        "1, BT709",
        "2, UNSPECIFIED",
        "5, BT709",
        "6, BT709",
        "9, BT2020",
        "11, DISPLAY_P3",
        "12, DISPLAY_P3",
        "22, UNSPECIFIED"
    })
    @DisplayName("reads H.273's primaries")
    void primaries(int code, ColorDescription.Primaries expected) {
        assertEquals(expected, ColorDescription.Primaries.fromH273(code));
    }

    @ParameterizedTest(name = "TransferCharacteristics {0} is {1}")
    @CsvSource({"1, SDR", "2, UNSPECIFIED", "6, SDR", "13, SDR", "14, SDR", "16, PQ", "17, UNSPECIFIED", "18, HLG"})
    @DisplayName("reads H.273's transfer functions")
    void transfers(int code, ColorDescription.Transfer expected) {
        assertEquals(expected, ColorDescription.Transfer.fromH273(code));
    }

    @Test
    @DisplayName("fills what a frame leaves unspecified from its track, part by part")
    void orElse() {
        var frame = ColorDescription.fromH273(9, 2);
        var track = ColorDescription.fromH273(1, 16).withLightLevels(1000, 0);
        var completed = frame.orElse(track);
        assertEquals(ColorDescription.Primaries.BT2020, completed.primaries(), "the frame's own");
        assertEquals(ColorDescription.Transfer.PQ, completed.transfer(), "the track's");
        assertEquals(OptionalInt.of(1000), completed.masteringPeak());
        assertEquals(OptionalInt.empty(), completed.contentPeak());
        assertEquals(ColorDescription.UNSPECIFIED, ColorDescription.UNSPECIFIED.orElse(ColorDescription.UNSPECIFIED));
    }

    @Test
    @DisplayName("takes the mastering peak, then MaxCLL, then 1000 nits for PQ; 1000 for HLG; 203 for SDR")
    void peaks() {
        var pq = ColorDescription.fromH273(9, 16);
        assertEquals(ColorDescription.DEFAULT_HDR_PEAK, pq.peakNits());
        assertEquals(600, pq.withLightLevels(0, 600).peakNits());
        assertEquals(4000, pq.withLightLevels(4000, 600).peakNits());
        assertEquals(
                1000, ColorDescription.fromH273(9, 18).withLightLevels(4000, 0).peakNits());
        assertEquals(ColorDescription.REFERENCE_WHITE, ColorDescription.SDR.peakNits());
    }

    @Test
    @DisplayName("is plain SDR when nothing says otherwise, and HDR for PQ and HLG")
    void kinds() {
        assertTrue(ColorDescription.UNSPECIFIED.isPlainSdr());
        assertTrue(ColorDescription.SDR.isPlainSdr());
        assertFalse(ColorDescription.fromH273(9, 1).isPlainSdr(), "BT.2020 SDR is converted");
        assertFalse(ColorDescription.fromH273(9, 1).isHdr());
        assertTrue(ColorDescription.fromH273(9, 16).isHdr());
        assertTrue(ColorDescription.fromH273(2, 18).isHdr());
    }

    @Test
    @DisplayName("refuses a peak that is no brightness, and reads zero as not stated")
    void levels() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ColorDescription(
                        ColorDescription.Primaries.BT2020,
                        ColorDescription.Transfer.PQ,
                        OptionalInt.of(0),
                        OptionalInt.empty()));
        assertEquals(ColorDescription.SDR, ColorDescription.SDR.withLightLevels(0, -1));
    }

    @Test
    @DisplayName("a video track and a frame without a description have none")
    void defaults() {
        var video = new TrackParams.Video(
                16, 16, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty());
        assertEquals(ColorDescription.UNSPECIFIED, video.colour());
        var pq = ColorDescription.fromH273(9, 16);
        assertEquals(pq, video.withColour(pq).colour());
    }
}
