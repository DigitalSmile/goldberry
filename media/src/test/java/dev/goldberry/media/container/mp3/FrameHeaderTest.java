package dev.goldberry.media.container.mp3;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// The frame header against the tables of the standard, and against the
/// fixtures' own first frame.
@DisplayName("FrameHeader")
class FrameHeaderTest {

    @Test
    @DisplayName("the fixtures' frame: MPEG-1 layer III, 128 kbit/s, 48 kHz, stereo, 384 bytes, 1152 samples")
    void fixtureFrame() {
        var header = FrameHeader.parse(0xFFFB_9400L);
        assertNotNull(header);
        assertAll(
                () -> assertEquals(FrameHeader.Version.MPEG_1, header.version()),
                () -> assertEquals(3, header.layer()),
                () -> assertEquals(128_000, header.bitRate()),
                () -> assertEquals(48_000, header.sampleRate()),
                () -> assertEquals(2, header.channels()),
                () -> assertFalse(header.padding()),
                () -> assertFalse(header.crc()),
                () -> assertEquals(384, header.bytes()),
                () -> assertEquals(1152, header.samples()),
                () -> assertEquals(32, header.sideInfoBytes()),
                () -> assertEquals("mp3", header.codecName()));
    }

    @Test
    @DisplayName("padding adds a slot: one byte in layers II and III, four in layer I")
    void padding() {
        var padded = FrameHeader.parse(0xFFFB_9600L);
        assertNotNull(padded);
        assertTrue(padded.padding());
        assertEquals(385, padded.bytes());

        // MPEG-1 layer I, 288 kbit/s, 44.1 kHz: 12 × 288000 / 44100 = 78 slots.
        var layerOne = FrameHeader.parse(0xFFFF_9000L);
        assertNotNull(layerOne);
        assertEquals(1, layerOne.layer());
        assertEquals(384, layerOne.samples());
        assertEquals(312, layerOne.bytes());
        var paddedOne = FrameHeader.parse(0xFFFF_9200L);
        assertNotNull(paddedOne);
        assertEquals(316, paddedOne.bytes());
    }

    @Test
    @DisplayName("MPEG-2 and 2.5 layer III frames are 576 samples, mono side info is shorter, a CRC is noted")
    void lowerVersions() {
        // MPEG-2, layer III, 64 kbit/s, 24 kHz, mono, with a CRC.
        var mpeg2 = FrameHeader.parse(0xFFF2_84C0L);
        assertNotNull(mpeg2);
        assertAll(
                () -> assertEquals(FrameHeader.Version.MPEG_2, mpeg2.version()),
                () -> assertEquals(64_000, mpeg2.bitRate()),
                () -> assertEquals(24_000, mpeg2.sampleRate()),
                () -> assertEquals(1, mpeg2.channels()),
                () -> assertTrue(mpeg2.crc()),
                () -> assertEquals(576, mpeg2.samples()),
                () -> assertEquals(192, mpeg2.bytes()),
                () -> assertEquals(9, mpeg2.sideInfoBytes()));
        // MPEG-2.5, layer III, 32 kbit/s, 11025 Hz.
        var mpeg25 = FrameHeader.parse(0xFFE3_4000L);
        assertNotNull(mpeg25);
        assertEquals(FrameHeader.Version.MPEG_2_5, mpeg25.version());
        assertEquals(11_025, mpeg25.sampleRate());
        assertEquals(32_000, mpeg25.bitRate());
        assertEquals(576, mpeg25.samples());
        // MPEG-2 layer II keeps 1152 samples.
        var layerTwo = FrameHeader.parse(0xFFF5_8400L);
        assertNotNull(layerTwo);
        assertEquals(2, layerTwo.layer());
        assertEquals(1152, layerTwo.samples());
        assertEquals("mp2", layerTwo.codecName());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            longs = {
                0x4944_3304L, // "ID3": no sync
                0xFFEB_9400L, // reserved version
                0xFFF9_9400L, // reserved layer
                0xFFFB_F400L, // bad bit rate
                0xFFFB_0400L, // free format
                0xFFFB_9C00L, // reserved sample rate
                0xFFF1_5000L, // ADTS AAC: layer bits 00
            })
    @DisplayName("refuses words that are not a header")
    void refuses(long word) {
        assertNull(FrameHeader.parse(word));
    }

    @Test
    @DisplayName("frames of one stream share version, layer and rate; the bit rate and mode may change")
    void sameStream() {
        var a = FrameHeader.parse(0xFFFB_9400L);
        var b = FrameHeader.parse(0xFFFB_A4C0L);
        var other = FrameHeader.parse(0xFFFB_9000L);
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(other);
        assertTrue(a.sameStream(b));
        assertFalse(a.sameStream(other), "44.1 kHz is another stream");
    }
}
