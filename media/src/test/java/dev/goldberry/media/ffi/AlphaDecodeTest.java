package dev.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// A VP9 sticker's alpha, from the demuxer's side data to premultiplied pixels.
///
/// `sticker-vp9-alpha.webm` is lossless, 64×64 at 10 fps for a second, red
/// everywhere. Its alpha: in frame `n` an opaque square at x `4n` to `4n + 15`, y
/// 8 to 23; half alpha (128) from row 40 down; transparent elsewhere.
@DisplayName("A VP9 picture's alpha, against FFmpeg")
class AlphaDecodeTest {

    private static final String STICKER = "sticker-vp9-alpha.webm";

    private Ffmpeg ffmpeg;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
        ffmpeg = FfmpegLibraries.get();
    }

    private Demuxer open(String name) {
        return Demuxer.open(
                ffmpeg, Source.of(URI.create("mem:///" + name)), new MemoryIO(VideoDecodeTest.fixture(name)));
    }

    /// The alpha the fixture was made with, at `(x, y)` of picture `n`.
    private static int expectedAlpha(int n, int x, int y) {
        if (y < 32) {
            return x >= 4 * n && x <= 4 * n + 15 && y >= 8 && y <= 23 ? 255 : 0;
        }
        return y >= 40 ? 128 : 0;
    }

    /// Decodes every picture of `name`'s video track through [Decoders], which
    /// reads the track's flag as the Engine does.
    private void decode(String name, Consumer<VideoFrame> consumer) {
        try (var demuxer = open(name)) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder =
                    Decoders.open(ffmpeg, demuxer, track.index(), List.of(), 0).decoder()) {
                var ended = false;
                while (!ended) {
                    switch (decoder.receive()) {
                        case Received.Decoded(var frame) -> consumer.accept(assertInstanceOf(VideoFrame.class, frame));
                        case Received.NeedsInput _ -> {
                            try (var packet = demuxer.read()) {
                                if (packet == null) {
                                    decoder.sendEnd();
                                } else {
                                    assertTrue(decoder.send(packet));
                                }
                            }
                        }
                        case Received.Ended _ -> ended = true;
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the track says it has alpha, and every packet carries a VP9 frame of it beside the picture")
    void sideDataArrives() {
        try (var demuxer = open(STICKER)) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            assertTrue(assertInstanceOf(TrackParams.Video.class, track.params()).alpha(), "AlphaMode is read");
            demuxer.select(Set.of(track.index()));
            var packets = 0;
            for (var read = demuxer.read(); read != null; read = demuxer.read()) {
                try (var packet = read) {
                    packets++;
                    var alpha = packet.alpha();
                    assertNotEquals(MemorySegment.NULL, alpha, "packet " + packets + " has its alpha");
                    assertTrue(alpha.byteSize() > 0);
                    // A VP9 uncompressed header starts with the frame marker, 0b10:
                    // the BlockAddID before it is gone.
                    assertEquals(0x80, alpha.get(JAVA_BYTE, 0) & 0xC0, "a VP9 frame, not the 8-byte ID");
                    assertFalse(packet.data().equals(alpha));
                }
            }
            assertEquals(10, packets);
        }
    }

    @Test
    @DisplayName("a track without alpha says so, and its packets carry none")
    void noSideDataWithoutAlpha() {
        try (var demuxer = open("clip-vp9.webm")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            assertFalse(
                    assertInstanceOf(TrackParams.Video.class, track.params()).alpha());
            demuxer.select(Set.of(track.index()));
            try (var packet = demuxer.read()) {
                assertEquals(MemorySegment.NULL, packet.alpha());
            }
        }
    }

    @Test
    @DisplayName(
            "decodes the alpha stream beside the picture: I420A, its fourth plane the shape the clip was made with")
    void alphaPlane() {
        var pictures = new ArrayList<Long>();
        decode(STICKER, frame -> {
            var n = pictures.size();
            pictures.add(frame.ptsNanos());
            assertEquals(PixelFormat.I420A, frame.format());
            assertEquals(64, frame.width());
            assertEquals(64, frame.height());
            var alpha = frame.planes().get(3);
            int stride = frame.strides().get(3);
            for (var y = 0; y < 64; y++) {
                for (var x = 0; x < 64; x++) {
                    assertEquals(
                            expectedAlpha(n, x, y),
                            alpha.get(JAVA_BYTE, (long) y * stride + x) & 0xFF,
                            "alpha at (" + x + ", " + y + ") of picture " + n);
                }
            }
        });
        assertEquals(10, pictures.size());
        assertEquals(100_000_000L, pictures.get(1) - pictures.get(0), "10 fps");
    }

    @Test
    @DisplayName("a stream without alpha decodes as it always did: I420, three planes")
    void noAlphaUnchanged() {
        var formats = new ArrayList<PixelFormat>();
        decode("clip-vp9.webm", frame -> formats.add(frame.format()));
        assertEquals(25, formats.size());
        assertTrue(formats.stream().allMatch(PixelFormat.I420::equals), formats.toString());
    }

    @Test
    @DisplayName("without the track's flag the same packets decode opaque, as before this change")
    void alphaIgnoredWithoutTheFlag() {
        try (var demuxer = open(STICKER)) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = FfmpegDecoder.open(
                    ffmpeg, demuxer.codecParameters(track.index()), demuxer.timeBase(track.index()))) {
                try (var packet = demuxer.read()) {
                    assertTrue(decoder.send(packet));
                }
                var frame = assertInstanceOf(
                        VideoFrame.class,
                        assertInstanceOf(Received.Decoded.class, receive(decoder))
                                .frame());
                assertEquals(PixelFormat.I420, frame.format());
            }
        }
    }

    private static Received receive(FfmpegDecoder decoder) {
        var received = decoder.receive();
        // Frame threads may hold the first picture until the end is sent.
        if (received == Received.NEEDS_INPUT) {
            decoder.sendEnd();
            received = decoder.receive();
        }
        return received;
    }

    @Test
    @DisplayName("converts a picture with alpha to premultiplied BGRA: opaque red, nothing, and half red at half alpha")
    void premultipliedConversion() {
        var checked = new ArrayList<Integer>();
        decode(STICKER, frame -> {
            if (!checked.isEmpty()) {
                return;
            }
            var stride = frame.width() * 4;
            var pixels = ByteBuffer.allocateDirect(stride * frame.height()).order(ByteOrder.LITTLE_ENDIAN);
            try (var converter = new VideoConverter(ffmpeg)) {
                converter.toBgra(frame, MemorySegment.ofBuffer(pixels), stride);
            }
            for (var y = 0; y < 64; y++) {
                for (var x = 0; x < 64; x++) {
                    var argb = pixels.getInt(y * stride + x * 4);
                    var a = argb >>> 24;
                    var r = (argb >>> 16) & 0xFF;
                    var g = (argb >>> 8) & 0xFF;
                    var b = argb & 0xFF;
                    var expected = expectedAlpha(0, x, y);
                    assertEquals(expected, a, "alpha at (" + x + ", " + y + ")");
                    assertTrue(r <= a && g <= a && b <= a, "premultiplied at (" + x + ", " + y + ")");
                    if (expected == 0) {
                        assertEquals(0, argb, "transparent is all zeroes");
                    } else {
                        // Red, through BT.601's limited range and back: 253 at most.
                        assertEquals(expected * 253 / 255.0, r, 3, "red at (" + x + ", " + y + ")");
                        assertTrue(g <= 2 && b <= 2, "no green or blue at (" + x + ", " + y + ")");
                    }
                }
            }
            checked.add(1);
        });
        assertEquals(List.of(1), checked);
    }

    @Test
    @DisplayName("premultiplies with the toolkit's rounding, and leaves opaque pixels alone")
    void premultiplyRounding() {
        try (var arena = Arena.ofConfined()) {
            var pixels = arena.allocate(16);
            var view = pixels.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
            view.putInt(0, 0x80FF8040)
                    .putInt(4, 0x00FFFFFF)
                    .putInt(8, 0xFF123456)
                    .putInt(12, 0x01FFFFFF);
            VideoConverter.premultiply(pixels, 16, 4, 1);
            assertEquals(0x80804020, view.getInt(0), "(c × 128 + 127) / 255");
            assertEquals(0, view.getInt(4), "transparent");
            assertEquals(0xFF123456, view.getInt(8), "opaque, untouched");
            assertEquals(0x01010101, view.getInt(12), "nearly transparent white");
        }
    }
}
