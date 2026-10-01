package dev.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.platform.PlatformDecoders;
import dev.goldberry.media.platform.bitstream.ParameterSetsTest;

/// What the providers claim, from requests made up here: no FFmpeg, and for the
/// refusals no Mac either.
@DisplayName("The providers' claims")
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
    @DisplayName("VideoToolbox reads an avcC or hvcC it can decode, and passes over everything else")
    void video() {
        try (var arena = Arena.ofConfined()) {
            assertTrue(
                    VideoToolboxProvider.configuration(request(CodecId.H264, VIDEO, ParameterSetsTest.AVCC_HIGH, arena))
                            .isPresent());
            assertTrue(VideoToolboxProvider.configuration(
                            request(CodecId.HEVC, VIDEO, ParameterSetsTest.HVCC_MAIN10, arena))
                    .isPresent());
            // An avcC offered as HEVC is not an hvcC.
            assertFalse(
                    VideoToolboxProvider.configuration(request(CodecId.HEVC, VIDEO, ParameterSetsTest.AVCC_HIGH, arena))
                            .isPresent());
            // No record, or Annex B start codes in its place.
            assertFalse(VideoToolboxProvider.configuration(request(CodecId.H264, VIDEO, new byte[0], arena))
                    .isPresent());
            assertFalse(VideoToolboxProvider.configuration(
                            request(CodecId.H264, VIDEO, HexFormat.of().parseHex("00000001674d0028"), arena))
                    .isPresent());
            // 4:2:2, which the frame contract has no layout for.
            var fourTwoTwo = ParameterSetsTest.avcC(ParameterSetsTest.h264HighSps(122, 2, 8));
            assertFalse(VideoToolboxProvider.configuration(request(CodecId.H264, VIDEO, fourTwoTwo, arena))
                    .isPresent());
            // Codecs and tracks that are not its own.
            assertFalse(
                    VideoToolboxProvider.configuration(request(CodecId.VP9, VIDEO, ParameterSetsTest.AVCC_HIGH, arena))
                            .isPresent());
            assertFalse(VideoToolboxProvider.configuration(
                            request(CodecId.H264, STEREO, ParameterSetsTest.AVCC_HIGH, arena))
                    .isPresent());
        }
    }

    @Test
    @DisplayName("AudioToolbox claims AAC with a configuration, AC-3 and E-AC-3 with a layout it can order")
    void audio() {
        try (var arena = Arena.ofConfined()) {
            assertTrue(
                    AudioToolboxProvider.claims(request(CodecId.AAC, STEREO, new byte[] {0x11, (byte) 0x90}, arena)));
            assertFalse(AudioToolboxProvider.claims(request(CodecId.AAC, STEREO, new byte[0], arena)), "ADTS");
            assertTrue(AudioToolboxProvider.claims(request(CodecId.AC3, STEREO, new byte[0], arena)));
            assertTrue(AudioToolboxProvider.claims(request(CodecId.EAC3, STEREO, new byte[0], arena)));
            var nine = new TrackParams.Audio(48_000, 9, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            assertFalse(AudioToolboxProvider.claims(request(CodecId.AC3, nine, new byte[0], arena)));
            var unknown = new TrackParams.Audio(0, 0, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            assertFalse(AudioToolboxProvider.claims(request(CodecId.EAC3, unknown, new byte[0], arena)));
            assertFalse(AudioToolboxProvider.claims(request(CodecId.OPUS, STEREO, new byte[0], arena)));
            assertFalse(AudioToolboxProvider.claims(request(CodecId.AAC, VIDEO, new byte[] {0x11, 0x10}, arena)));
        }
    }

    @Test
    @DisplayName("opening what is not claimed is refused before anything native is touched")
    void openRefuses() {
        try (var arena = Arena.ofConfined()) {
            assertThrows(
                    RuntimeException.class,
                    () -> new AudioToolboxProvider().open(request(CodecId.OPUS, STEREO, new byte[0], arena)));
            assertThrows(
                    RuntimeException.class,
                    () -> new VideoToolboxProvider().open(request(CodecId.VP9, VIDEO, new byte[0], arena)));
        }
    }

    @Test
    @DisplayName("PlatformDecoders lists every system's providers by name, on any system")
    void list() {
        var names = PlatformDecoders.providers().stream().map(p -> p.name()).toList();
        assertEquals(
                List.of(
                        VideoToolboxProvider.NAME,
                        AudioToolboxProvider.NAME,
                        "gstreamer-video",
                        "gstreamer-audio",
                        "mediafoundation-video",
                        "mediafoundation-audio"),
                names);
        assertEquals(
                PlatformDecoders.available(),
                PlatformDecoders.unavailableReason().isEmpty());
    }
}
