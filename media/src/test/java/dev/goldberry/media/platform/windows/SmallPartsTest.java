package dev.goldberry.media.platform.windows;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.codec.Frame;

/// The small pure-Java pieces the Windows decoders rely on.
@DisplayName("The Windows decoders' small parts")
class SmallPartsTest {

    @Nested
    @DisplayName("HResult")
    class HResultTest {

        @Test
        @DisplayName("names a code as the SDK headers do, with its value in hexadecimal")
        void describe() {
            assertEquals(
                    "MF_E_TRANSFORM_NEED_MORE_INPUT (0xC00D6D72)",
                    HResult.describe(HResult.MF_E_TRANSFORM_NEED_MORE_INPUT));
            assertEquals(
                    "MF_E_TRANSFORM_STREAM_CHANGE (0xC00D6D61)",
                    HResult.describe(HResult.MF_E_TRANSFORM_STREAM_CHANGE));
            assertEquals("MF_E_NOTACCEPTING (0xC00D36B5)", HResult.describe(HResult.MF_E_NOTACCEPTING));
            assertEquals("MF_E_NO_MORE_TYPES (0xC00D36B9)", HResult.describe(HResult.MF_E_NO_MORE_TYPES));
            assertEquals("MF_E_TRANSFORM_TYPE_NOT_SET (0xC00D6D60)", HResult.describe(0xC00D_6D60));
            assertEquals("RPC_E_CHANGED_MODE (0x80010106)", HResult.describe(HResult.RPC_E_CHANGED_MODE));
            assertEquals("S_FALSE (0x00000001)", HResult.describe(1));
            assertEquals("0x80001234", HResult.describe(0x8000_1234));
            assertNull(HResult.name(0x8000_1234));
        }

        @Test
        @DisplayName("a failure is a negative code; S_FALSE is a success")
        void check() {
            assertTrue(HResult.failed(HResult.E_FAIL));
            assertFalse(HResult.failed(HResult.S_FALSE));
            HResult.check("Something", HResult.S_OK);
            HResult.check("Something", HResult.S_FALSE);
            var failure = assertThrows(HResult.Failure.class, () -> HResult.check("MFStartup", HResult.E_NOTIMPL));
            assertEquals(HResult.E_NOTIMPL, failure.hresult());
            assertEquals("MFStartup failed: E_NOTIMPL (0x80004001)", failure.getMessage());
        }
    }

    @Nested
    @DisplayName("AudioFormats")
    class AudioFormatsTest {

        @Test
        @DisplayName("the AAC user data is HEAACWAVEINFO's tail, then the AudioSpecificConfig")
        void userData() {
            var expected = HexFormat.of()
                    .parseHex(
                            "0000" // wPayloadType: raw
                                    + "fe00" // wAudioProfileLevelIndication: 0xFE, little-endian
                                    + "0000" // wStructType
                                    + "0000" // wReserved1
                                    + "00000000" // dwReserved2
                                    + "1190"); // AAC LC, 48 kHz, stereo
            assertArrayEquals(expected, AudioFormats.aacUserData(new byte[] {0x11, (byte) 0x90}));
            assertEquals(12, AudioFormats.HEAACWAVEINFO_TAIL);
            assertThrows(IllegalArgumentException.class, () -> AudioFormats.aacUserData(new byte[] {0x11}));
        }

        @ParameterizedTest(name = "{0}: {1} Hz, {2} channels")
        @CsvSource({
            "1190, 48000, 2", // AAC LC
            "1210, 44100, 2",
            "2b920800, 22050, 2", // HE-AAC, explicit: the core's rate
            "11b8, 48000, 8", // channel configuration 7 is 7.1
            "1180, 48000, 0", // configuration 0: a program config element
            "11, 0, 0", // too short
        })
        @DisplayName("reads the core rate and channels from an AudioSpecificConfig")
        void config(String hex, int rate, int channels) {
            assertEquals(
                    new AudioFormats.AacConfig(rate, channels),
                    AudioFormats.aacConfig(HexFormat.of().parseHex(hex)));
        }

        @Test
        @DisplayName("reads an escaped object type and an explicit rate")
        void escaped() {
            // Object type 31 + 0, frequency index 15, 24-bit rate, mono: 43 bits.
            var bits = (31L << 38) | (0L << 32) | (15L << 28) | (48_000L << 4) | 1L;
            var packed = bits << 5;
            var bytes = new byte[6];
            for (var i = 0; i < 6; i++) {
                bytes[i] = (byte) (packed >>> (40 - 8 * i));
            }
            assertEquals(new AudioFormats.AacConfig(48_000, 1), AudioFormats.aacConfig(bytes));
        }

        @Test
        @DisplayName("a channel mask has one bit per channel, in FFmpeg's order")
        void channelMask() {
            for (var channels = 1; channels <= 8; channels++) {
                assertEquals(channels, Integer.bitCount(AudioFormats.channelMask(channels)), channels + " channels");
            }
            assertEquals(0x3F, AudioFormats.channelMask(6), "FL FR FC LFE BL BR");
            assertEquals(0, AudioFormats.channelMask(9));
        }
    }

    @Nested
    @DisplayName("SampleClock")
    class SampleClockTest {

        @Test
        @DisplayName("times each chunk by the samples before it, from the first packet")
        void counts() {
            var clock = new SampleClock(48_000);
            assertEquals(Frame.NO_PTS, clock.next());
            assertFalse(clock.packet(Frame.NO_PTS, true));
            assertFalse(clock.packet(1_000_000_000L, true));
            assertEquals(1_000_000_000L, clock.advance(1024));
            assertEquals(1_000_000_000L + 1024 * 1_000_000_000L / 48_000, clock.advance(1024));
            // A packet a little off the count does not move it.
            assertFalse(clock.packet(1_050_000_000L, true));
            assertEquals(1_000_000_000L + 2048 * 1_000_000_000L / 48_000, clock.next());
        }

        @Test
        @DisplayName("a gap re-anchors it, but not while decoded samples wait")
        void gap() {
            var clock = new SampleClock(48_000);
            clock.packet(0, true);
            clock.advance(48_000);
            assertFalse(clock.packet(5_000_000_000L, false), "samples pending");
            assertEquals(1_000_000_000L, clock.next());
            assertTrue(clock.packet(5_000_000_000L, true));
            assertEquals(5_000_000_000L, clock.advance(10));
        }

        @Test
        @DisplayName("a reset waits for the next packet; a new rate keeps the time running")
        void resetAndRate() {
            var clock = new SampleClock(24_000);
            clock.packet(0, true);
            clock.advance(24_000);
            clock.rate(48_000);
            assertEquals(1_000_000_000L, clock.advance(48_000));
            assertEquals(2_000_000_000L, clock.next());
            clock.reset();
            assertEquals(Frame.NO_PTS, clock.next());
            clock.packet(600_000_000L, true);
            assertEquals(600_000_000L, clock.next());
            assertThrows(IllegalArgumentException.class, () -> new SampleClock(0));
        }
    }

    @Nested
    @DisplayName("Structs and times")
    class StructsTest {

        @Test
        @DisplayName("the x64 struct layouts are the SDK's sizes and offsets")
        void layouts() {
            var data = MfTransform.OUTPUT_DATA_BUFFER;
            assertEquals(32, data.byteSize());
            assertEquals(0, data.byteOffset(groupElement("dwStreamID")));
            assertEquals(8, MfTransform.OUTPUT_SAMPLE);
            assertEquals(16, MfTransform.OUTPUT_STATUS);
            assertEquals(24, MfTransform.OUTPUT_EVENTS);
            assertEquals(12, MfTransform.OUTPUT_STREAM_INFO.byteSize());
            assertEquals(32, MfPlat.REGISTER_TYPE_INFO.byteSize());
            assertEquals(16, MfPlat.REGISTER_TYPE_INFO.byteOffset(groupElement("guidSubtype")));
            assertEquals(16, PictureLayout.VIDEO_AREA.byteSize());
        }

        @Test
        @DisplayName("the caller allocates output unless the MFT provides it or can")
        void allocation() {
            assertTrue(new MfTransform.StreamInfo(0, 1000, 16).callerAllocates());
            assertTrue(new MfTransform.StreamInfo(0x7, 1000, 16).callerAllocates());
            assertFalse(new MfTransform.StreamInfo(MfTransform.OUTPUT_STREAM_PROVIDES_SAMPLES, 0, 0).callerAllocates());
            assertFalse(
                    new MfTransform.StreamInfo(MfTransform.OUTPUT_STREAM_CAN_PROVIDE_SAMPLES, 0, 0).callerAllocates());
        }

        @Test
        @DisplayName("times cross in 100-nanosecond units")
        void times() {
            assertEquals(10_000_000L, Transform.toHundredNanos(1_000_000_000L));
            assertEquals(333_666L, Transform.toHundredNanos(33_366_667L));
            assertEquals(-1, Transform.toHundredNanos(-1));
            assertEquals(1_000_000_000L, Transform.fromHundredNanos(10_000_000L));
        }
    }
}
