package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.Rational;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;
import io.github.digitalsmile.goldberry.media.platform.bitstream.ParameterSetsTest;

/// What the Media Foundation providers claim, from requests made up here: no
/// FFmpeg, and for the refusals no Windows either.
@DisplayName("The Media Foundation providers' claims")
class ProvidersTest {

    private static final TrackParams VIDEO = new TrackParams.Video(
            160, 90, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty());
    private static final TrackParams STEREO =
            new TrackParams.Audio(48_000, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());

    private static DecoderRequest request(CodecId codec, TrackParams params, byte[] extradata, Arena arena) {
        // What the demuxer hands over for no extradata: an empty segment.
        var segment = extradata.length == 0 ? MemorySegment.NULL : arena.allocateFrom(JAVA_BYTE, extradata);
        return new DecoderRequest(codec, codec.name().toLowerCase(), params, segment, new Rational(1, 1000));
    }

    @Test
    @DisplayName("the video provider reads an avcC or hvcC it can decode, and passes over everything else")
    void video() {
        try (var arena = Arena.ofConfined()) {
            assertTrue(MediaFoundationVideoProvider.configuration(
                            request(CodecId.H264, VIDEO, ParameterSetsTest.AVCC_HIGH, arena))
                    .isPresent());
            assertTrue(MediaFoundationVideoProvider.configuration(
                            request(CodecId.HEVC, VIDEO, ParameterSetsTest.HVCC_MAIN10, arena))
                    .isPresent());
            // An avcC offered as HEVC is not an hvcC.
            assertFalse(MediaFoundationVideoProvider.configuration(
                            request(CodecId.HEVC, VIDEO, ParameterSetsTest.AVCC_HIGH, arena))
                    .isPresent());
            // No record, or Annex B start codes in its place.
            assertFalse(MediaFoundationVideoProvider.configuration(request(CodecId.H264, VIDEO, new byte[0], arena))
                    .isPresent());
            assertFalse(MediaFoundationVideoProvider.configuration(
                            request(CodecId.H264, VIDEO, HexFormat.of().parseHex("00000001674d0028"), arena))
                    .isPresent());
            // 4:2:2, which the frame contract has no layout for.
            var fourTwoTwo = ParameterSetsTest.avcC(ParameterSetsTest.h264HighSps(122, 2, 8));
            assertFalse(MediaFoundationVideoProvider.configuration(request(CodecId.H264, VIDEO, fourTwoTwo, arena))
                    .isPresent());
            // Codecs and tracks that are not its own.
            assertFalse(MediaFoundationVideoProvider.configuration(
                            request(CodecId.VP9, VIDEO, ParameterSetsTest.AVCC_HIGH, arena))
                    .isPresent());
            assertFalse(MediaFoundationVideoProvider.configuration(
                            request(CodecId.H264, STEREO, ParameterSetsTest.AVCC_HIGH, arena))
                    .isPresent());
        }
        assertEquals(MfGuids.MFVideoFormat_H264, MediaFoundationVideoProvider.subtype(CodecId.H264));
        assertEquals(MfGuids.MFVideoFormat_HEVC, MediaFoundationVideoProvider.subtype(CodecId.HEVC));
    }

    @Test
    @DisplayName("the audio provider claims AAC with a configuration, AC-3 and E-AC-3 with up to eight channels")
    void audio() {
        try (var arena = Arena.ofConfined()) {
            assertTrue(MediaFoundationAudioProvider.claims(
                    request(CodecId.AAC, STEREO, new byte[] {0x11, (byte) 0x90}, arena)));
            assertFalse(MediaFoundationAudioProvider.claims(request(CodecId.AAC, STEREO, new byte[0], arena)), "ADTS");
            assertTrue(MediaFoundationAudioProvider.claims(request(CodecId.AC3, STEREO, new byte[0], arena)));
            assertTrue(MediaFoundationAudioProvider.claims(request(CodecId.EAC3, STEREO, new byte[0], arena)));
            var nine = new TrackParams.Audio(48_000, 9, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            assertFalse(MediaFoundationAudioProvider.claims(request(CodecId.AC3, nine, new byte[0], arena)));
            var unknown = new TrackParams.Audio(0, 0, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            assertFalse(MediaFoundationAudioProvider.claims(request(CodecId.EAC3, unknown, new byte[0], arena)));
            assertFalse(MediaFoundationAudioProvider.claims(request(CodecId.OPUS, STEREO, new byte[0], arena)));
            assertFalse(
                    MediaFoundationAudioProvider.claims(request(CodecId.AAC, VIDEO, new byte[] {0x11, 0x10}, arena)));
        }
        assertEquals(Optional.of(MfGuids.MFAudioFormat_AAC), MediaFoundationAudioProvider.subtype(CodecId.AAC));
        assertEquals(Optional.of(MfGuids.MFAudioFormat_Dolby_AC3), MediaFoundationAudioProvider.subtype(CodecId.AC3));
        assertEquals(
                Optional.of(MfGuids.MFAudioFormat_Dolby_DDPlus), MediaFoundationAudioProvider.subtype(CodecId.EAC3));
        assertEquals(Optional.empty(), MediaFoundationAudioProvider.subtype(CodecId.OPUS));
    }

    @Test
    @DisplayName("opening what is not claimed is refused before anything native is touched")
    void openRefuses() {
        try (var arena = Arena.ofConfined()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new MediaFoundationAudioProvider().open(request(CodecId.OPUS, STEREO, new byte[0], arena)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new MediaFoundationVideoProvider().open(request(CodecId.VP9, VIDEO, new byte[0], arena)));
        }
    }

    @Test
    @DisplayName("both providers have their names")
    void names() {
        assertEquals("mediafoundation-video", new MediaFoundationVideoProvider().name());
        assertEquals("mediafoundation-audio", new MediaFoundationAudioProvider().name());
    }

    @Test
    @DisplayName("Media Foundation is Windows's: any other system is unavailable without opening a library")
    void otherSystems() {
        assertTrue(MediaFoundation.isWindows("Windows 11"));
        assertTrue(MediaFoundation.isWindows("windows server 2022"));
        assertFalse(MediaFoundation.isWindows("Linux"));
        assertFalse(MediaFoundation.isWindows("Mac OS X"));
        var state = assertInstanceOf(MediaFoundation.State.Unavailable.class, MediaFoundation.load("Linux"));
        assertTrue(state.reason().contains("Linux"), state.reason());
    }

    @Test
    @DisplayName("off Windows, the providers support nothing and opening a claimed track fails before native code")
    @DisabledOnOs(OS.WINDOWS)
    void offWindows() {
        assertTrue(WindowsDecoders.unavailableReason().isPresent());
        try (var arena = Arena.ofConfined()) {
            var h264 = request(CodecId.H264, VIDEO, ParameterSetsTest.AVCC_HIGH, arena);
            var aac = request(CodecId.AAC, STEREO, new byte[] {0x11, (byte) 0x90}, arena);
            assertFalse(new MediaFoundationVideoProvider().supports(h264));
            assertFalse(new MediaFoundationAudioProvider().supports(aac));
            assertThrows(IllegalStateException.class, () -> new MediaFoundationVideoProvider().open(h264));
            assertThrows(IllegalStateException.class, () -> new MediaFoundationAudioProvider().open(aac));
        }
    }
}
