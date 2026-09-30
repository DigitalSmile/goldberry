package io.github.digitalsmile.goldberry.media.platform.macos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// The small pure-Java pieces the decoders rely on.
@DisplayName("The decoders' small parts")
class SmallPartsTest {

    @Nested
    @DisplayName("AacCookie")
    class AacCookieTest {

        @Test
        @DisplayName("wraps an AudioSpecificConfig in the ES_Descriptor FFmpeg's audiotoolboxdec writes")
        void descriptor() {
            // AAC LC, 48 kHz, stereo.
            var cookie = AacCookie.of(new byte[] {0x11, (byte) 0x90});
            var expected = HexFormat.of()
                    .parseHex("03808080" + "1c" + "000000" // ES_Descriptor, 28 bytes: ES_ID and flags
                            + "04808080" + "14" + "40" + "15" + "0000000000000000000000" // DecoderConfig
                            + "05808080" + "02" + "1190"); // DecoderSpecificInfo: the config
            assertArrayEquals(expected, cookie);
        }

        @Test
        @DisplayName("refuses a configuration shorter than two bytes")
        void tooShort() {
            assertThrows(IllegalArgumentException.class, () -> AacCookie.of(new byte[] {0x11}));
        }
    }

    @Nested
    @DisplayName("ChannelLabels")
    class ChannelLabelsTest {

        @Test
        @DisplayName("mono and stereo need no remapping")
        void monoAndStereo() {
            assertEquals(0, ChannelLabels.forChannels(1).length);
            assertEquals(0, ChannelLabels.forChannels(2).length);
        }

        @Test
        @DisplayName("5.1 is FL FR FC LFE and the surround pair")
        void fivePointOne() {
            assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6}, ChannelLabels.forChannels(6));
        }

        @ParameterizedTest(name = "{0} channels")
        @ValueSource(ints = {3, 4, 5, 6, 7, 8})
        @DisplayName("every count up to eight has one label per channel, each once")
        void oneLabelEach(int channels) {
            var labels = ChannelLabels.forChannels(channels);
            assertEquals(channels, labels.length);
            assertEquals(channels, Arrays.stream(labels).distinct().count());
        }

        @ParameterizedTest(name = "{0} channels")
        @ValueSource(ints = {0, 9, 24})
        @DisplayName("other counts are refused")
        void refused(int channels) {
            assertThrows(IllegalArgumentException.class, () -> ChannelLabels.forChannels(channels));
        }
    }

    @Nested
    @DisplayName("OsStatus")
    class OsStatusTest {

        @Test
        @DisplayName("four-character codes go both ways")
        void fourCharacterCodes() {
            assertEquals(0x6161_6320, OsStatus.code("aac "));
            assertEquals("aac ", OsStatus.fourCc(OsStatus.code("aac ")));
            assertNull(OsStatus.fourCc(-12_903));
            assertThrows(IllegalArgumentException.class, () -> OsStatus.code("abc"));
            assertThrows(IllegalArgumentException.class, () -> OsStatus.code("abéc"));
        }

        @Test
        @DisplayName("a status is described by its name, its code, or its number")
        void describe() {
            assertEquals("kVTInvalidSessionErr (-12903)", OsStatus.describe(OsStatus.VT_INVALID_SESSION));
            assertEquals("'fmt?' (" + OsStatus.code("fmt?") + ")", OsStatus.describe(OsStatus.code("fmt?")));
            assertEquals("-1", OsStatus.describe(-1));
        }

        @Test
        @DisplayName("check passes noErr and raises the rest with the status")
        void check() {
            OsStatus.check("f", OsStatus.OK);
            var failure = assertThrows(OsStatus.Failure.class, () -> OsStatus.check("AudioConverterNew", -50));
            assertEquals(-50, failure.status());
            assertTrue(failure.getMessage().startsWith("AudioConverterNew failed: kAudio_ParamError"));
        }
    }

    @Nested
    @DisplayName("Frameworks")
    class FrameworksTest {

        @Test
        @DisplayName("only macOS is macOS")
        void isMac() {
            assertTrue(Frameworks.isMac("Mac OS X"));
            assertTrue(!Frameworks.isMac("Linux"));
            assertTrue(!Frameworks.isMac("Windows 11"));
        }

        @Test
        @DisplayName("elsewhere the frameworks are unavailable, and say why")
        void elsewhere() {
            var state = assertInstanceOf(Frameworks.State.Unavailable.class, Frameworks.load("Linux"));
            assertTrue(state.reason().contains("Linux"), state.reason());
        }

        @Test
        @DisplayName("on a Mac every binding resolves")
        void onMac() {
            PlatformRequirement.enforce();
            assertInstanceOf(Frameworks.State.Loaded.class, Frameworks.load(System.getProperty("os.name")));
        }
    }
}
