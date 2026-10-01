package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.platform.bitstream.ParameterSets.Signal;
import dev.goldberry.media.platform.bitstream.ParameterSetsTest;

/// The GStreamer package's pure Java: caps, pipeline descriptions, layouts,
/// colour and times. None of it needs GStreamer.
@DisplayName("The GStreamer providers' small parts")
class SmallPartsTest {

    private static final TrackParams VIDEO = new TrackParams.Video(
            160, 90, Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty());
    private static final TrackParams STEREO =
            new TrackParams.Audio(48_000, 2, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());

    private static DecoderRequest request(CodecId codec, TrackParams params, byte[] extradata, Arena arena) {
        var segment = extradata.length == 0 ? MemorySegment.NULL : arena.allocateFrom(JAVA_BYTE, extradata);
        return new DecoderRequest(codec, codec.ffmpegName(), params, segment, new Rational(1, 1000));
    }

    @Test
    @DisplayName("the five codecs, each with its parser, and nothing else")
    void codecs() {
        assertEquals(Optional.of(GstCodec.H264), GstCodec.of(CodecId.H264));
        assertEquals(Optional.of(GstCodec.EAC3), GstCodec.of(CodecId.EAC3));
        assertEquals(Optional.empty(), GstCodec.of(CodecId.VP9));
        assertEquals(Optional.empty(), GstCodec.of(CodecId.MPEG4));
        assertEquals("h265parse", GstCodec.HEVC.parser());
        assertEquals("ac3parse", GstCodec.EAC3.parser());
        assertTrue(GstCodec.HEVC.video());
        assertFalse(GstCodec.AAC.video());
    }

    @Test
    @DisplayName("the source caps carry the stream as the container stores it, configuration and all")
    void sourceCaps() {
        try (var arena = Arena.ofConfined()) {
            assertEquals(
                    "video/x-h264, stream-format=(string)avc, alignment=(string)au, codec_data=(buffer)01640028,"
                            + " width=(int)160, height=(int)90",
                    GstCodec.H264.sourceCaps(request(CodecId.H264, VIDEO, new byte[] {1, 0x64, 0, 0x28}, arena)));
            assertEquals(
                    "audio/mpeg, mpegversion=(int)4, stream-format=(string)raw, codec_data=(buffer)1190,"
                            + " rate=(int)48000, channels=(int)2",
                    GstCodec.AAC.sourceCaps(request(CodecId.AAC, STEREO, new byte[] {0x11, (byte) 0x90}, arena)));
            // No configuration, and no rate: nothing is invented.
            var unknown = new TrackParams.Audio(0, 0, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            assertEquals(
                    "audio/x-eac3, framed=(boolean)true",
                    GstCodec.EAC3.sourceCaps(request(CodecId.EAC3, unknown, new byte[0], arena)));
        }
    }

    @Test
    @DisplayName("decoders are asked for at the stream's size, as decodebin asks")
    void decoderCaps() {
        try (var arena = Arena.ofConfined()) {
            assertEquals(
                    "video/x-h265, width=(int)160, height=(int)90",
                    GstCodec.HEVC.decoderCaps(request(CodecId.HEVC, VIDEO, new byte[0], arena)));
            assertEquals("audio/x-ac3", GstCodec.AC3.decoderCaps(request(CodecId.AC3, STEREO, new byte[0], arena)));
        }
    }

    @Test
    @DisplayName("a pipeline is source, parser, decoder, converter, sink; and a decoder name is only a name")
    void pipeline() {
        try (var arena = Arena.ofConfined()) {
            var video = GstCodec.H264.pipeline(request(CodecId.H264, VIDEO, new byte[0], arena), "avdec_h264");
            assertTrue(
                    video.matches("appsrc name=src format=time caps=\"[^\"]+\" ! h264parse ! avdec_h264 ! videoconvert"
                            + " ! appsink name=sink sync=false max-buffers=4 caps=\"video/x-raw.*\""),
                    video);
            var audio = GstCodec.AC3.pipeline(request(CodecId.AC3, STEREO, new byte[0], arena), "a52dec");
            assertTrue(audio.contains("! ac3parse ! a52dec ! audioconvert ! appsink"), audio);
            assertTrue(audio.contains("F32LE") && audio.contains("interleaved"), audio);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> GstCodec.H264.pipeline(
                            request(CodecId.H264, VIDEO, new byte[0], arena), "avdec_h264 ! filesink location=/tmp/x"));
        }
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"NV12, NV12", "I420, I420", "P010_10LE, P010", "I420_10LE, I010"})
    @DisplayName("the frame contract's four layouts, by GStreamer's names")
    void formats(String gstreamer, PixelFormat format) {
        assertEquals(Optional.of(format), GstVideoDecoder.format(gstreamer));
    }

    @Test
    @DisplayName("any other layout is none of the contract's")
    void otherFormats() {
        assertEquals(Optional.empty(), GstVideoDecoder.format("Y444"));
        assertEquals(Optional.empty(), GstVideoDecoder.format(""));
    }

    @Test
    @DisplayName("the matrix is the stream's first, then GStreamer's, then the default by height")
    void matrix() {
        var tagged709 = new Signal(Signal.Range.LIMITED, 1);
        assertEquals(VideoFrame.ColorMatrix.BT709, GstVideoDecoder.matrix(tagged709, GstLayout.MATRIX_BT601, 90));
        assertEquals(
                VideoFrame.ColorMatrix.BT601,
                GstVideoDecoder.matrix(new Signal(Signal.Range.LIMITED, 6), GstLayout.MATRIX_BT709, 1080));
        assertEquals(
                VideoFrame.ColorMatrix.BT2020,
                GstVideoDecoder.matrix(new Signal(Signal.Range.LIMITED, 9), GstLayout.MATRIX_BT709, 1080));
        assertEquals(
                VideoFrame.ColorMatrix.BT2020, GstVideoDecoder.matrix(Signal.UNSPECIFIED, GstLayout.MATRIX_BT2020, 90));
        // Neither says: 720 rows up is BT.709, as the built-in decoder has it.
        assertEquals(VideoFrame.ColorMatrix.BT709, GstVideoDecoder.matrix(Signal.UNSPECIFIED, 0, 720));
        assertEquals(VideoFrame.ColorMatrix.BT601, GstVideoDecoder.matrix(Signal.UNSPECIFIED, 0, 719));
    }

    @Test
    @DisplayName("full range is the stream's word over GStreamer's, which some decoders lose")
    void fullRange() {
        assertTrue(GstVideoDecoder.fullRange(new Signal(Signal.Range.FULL, 1), GstLayout.RANGE_LIMITED));
        assertFalse(GstVideoDecoder.fullRange(new Signal(Signal.Range.LIMITED, 1), GstLayout.RANGE_FULL));
        assertTrue(GstVideoDecoder.fullRange(Signal.UNSPECIFIED, GstLayout.RANGE_FULL));
        assertFalse(GstVideoDecoder.fullRange(Signal.UNSPECIFIED, 0));
    }

    @Test
    @DisplayName("the track's size is the most that is shown")
    void visible() {
        assertEquals(90, GstVideoDecoder.visible(96, 90));
        assertEquals(96, GstVideoDecoder.visible(96, 0));
        assertEquals(80, GstVideoDecoder.visible(80, 90));
    }

    @Test
    @DisplayName("a buffer of interleaved f32 holds its bytes over four per channel frames")
    void audioFrames() {
        assertEquals(1024, GstAudioDecoder.frames(1024 * 2 * 4, 2));
        assertEquals(1536, GstAudioDecoder.frames(1536 * 6 * 4, 6));
        assertEquals(0, GstAudioDecoder.frames(7, 2));
    }

    @Test
    @DisplayName("times go into GStreamer an hour later and come out as they went in, none as none")
    void times() {
        assertEquals(GstPipeline.TIME_OFFSET_NANOS - 21_000_000L, GstPipeline.time(-21_000_000L));
        assertEquals(-21_000_000L, GstPipeline.nanos(GstPipeline.time(-21_000_000L)));
        assertEquals(Gst.CLOCK_TIME_NONE, GstPipeline.time(Frame.NO_PTS));
        assertEquals(Frame.NO_PTS, GstPipeline.nanos(Gst.CLOCK_TIME_NONE));
        assertTrue(GstPipeline.time(-GstPipeline.TIME_OFFSET_NANOS + 1) > 0, "the earliest time a stream starts at");
    }

    @Test
    @DisplayName("decoders are tried highest rank first, and by name at the same rank")
    void ranked() {
        var ranked = Gst.Candidate.ranked(List.of(
                new Gst.Candidate("openh264dec", 64),
                new Gst.Candidate("nvh264dec", 257),
                new Gst.Candidate("avdec_h264", 256),
                new Gst.Candidate("a_h264dec", 256)));
        assertEquals(
                List.of("nvh264dec", "a_h264dec", "avdec_h264", "openh264dec"),
                ranked.stream().map(Gst.Candidate::name).toList());
    }

    @Test
    @DisplayName("only Linux binds GStreamer; any other system is unavailable before a library is opened")
    void onlyLinux() {
        assertTrue(GStreamer.isLinux("Linux"));
        assertFalse(GStreamer.isLinux("Mac OS X"));
        var state = GStreamer.load("Windows 11");
        assertTrue(
                state instanceof GStreamer.State.Unavailable(var reason) && reason.contains("Windows 11"),
                state::toString);
    }

    @Test
    @DisplayName("the providers claim what the macOS ones claim, and open nothing they do not claim")
    void claims() {
        try (var arena = Arena.ofConfined()) {
            assertTrue(
                    GStreamerAudioProvider.claims(request(CodecId.AAC, STEREO, new byte[] {0x11, (byte) 0x90}, arena)));
            assertFalse(GStreamerAudioProvider.claims(request(CodecId.AAC, STEREO, new byte[0], arena)), "ADTS");
            assertTrue(GStreamerAudioProvider.claims(request(CodecId.AC3, STEREO, new byte[0], arena)));
            var nine = new TrackParams.Audio(48_000, 9, Optional.empty(), OptionalInt.empty(), OptionalLong.empty());
            assertFalse(GStreamerAudioProvider.claims(request(CodecId.EAC3, nine, new byte[0], arena)));
            assertFalse(GStreamerAudioProvider.claims(request(CodecId.OPUS, STEREO, new byte[0], arena)));
            assertFalse(GStreamerAudioProvider.claims(request(CodecId.AAC, VIDEO, new byte[] {0x11, 0x10}, arena)));

            var video = new GStreamerVideoProvider();
            assertFalse(video.supports(request(CodecId.VP9, VIDEO, ParameterSetsTest.AVCC_HIGH, arena)));
            assertFalse(video.supports(request(CodecId.H264, VIDEO, new byte[0], arena)), "no avcC");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> video.open(request(CodecId.VP9, VIDEO, ParameterSetsTest.AVCC_HIGH, arena)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GStreamerAudioProvider().open(request(CodecId.OPUS, STEREO, new byte[0], arena)));
        }
    }
}
