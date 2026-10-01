package dev.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// Video decode and CPU present's conversion against FFmpeg: phase 3's native half.
///
/// The clips are `fixtures/`'s: FFmpeg's `testsrc2` at 160×90 and 25 fps, one
/// second of it in each of the three shipped video codecs, and a fifth of a
/// second in the two VP9 profiles whose pictures are not 8-bit 4:2:0.
@DisplayName("Video decode and conversion, against FFmpeg")
class VideoDecodeTest {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final long FRAME_NANOS = 40_000_000L;

    private Ffmpeg ffmpeg;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
        ffmpeg = FfmpegLibraries.get();
    }

    static byte[] fixture(String name) {
        try (var in = VideoDecodeTest.class.getResourceAsStream("/dev/goldberry/media/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalStateException("no fixture " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Decodes every picture of `name`'s video track, handing each to `consumer`
    /// while it is still borrowed.
    private void decodeVideo(String name, Consumer<VideoFrame> consumer) {
        try (var demuxer = Demuxer.open(ffmpeg, Source.of(URI.create("mem:///" + name)), new MemoryIO(fixture(name)))) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = FfmpegDecoder.open(
                    ffmpeg, demuxer.codecParameters(track.index()), demuxer.timeBase(track.index()))) {
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

    @ParameterizedTest(name = "{0}: {1} pictures as {2}")
    @CsvSource({
        "clip-vp8.webm, 25, I420",
        "clip-vp9.webm, 25, I420",
        "clip-av1.mkv, 25, I420",
        "clip-av1.mp4, 25, I420",
        "clip-vp9-10bit.webm, 5, I010",
        "clip-vp9-444.webm, 5, I420",
    })
    @DisplayName("decodes every picture at its size, in order, 40 ms apart")
    void decodesEveryPicture(String name, int count, PixelFormat format) {
        var pts = new ArrayList<Long>();
        decodeVideo(name, frame -> {
            assertEquals(format, frame.format());
            assertEquals(WIDTH, frame.width());
            assertEquals(HEIGHT, frame.height());
            // Untagged and under 720 rows: BT.601, limited range.
            assertEquals(VideoFrame.ColorMatrix.BT601, frame.matrix());
            assertTrue(!frame.fullRange());
            pts.add(frame.ptsNanos());
        });
        assertEquals(count, pts.size());
        var first = pts.getFirst();
        for (var i = 0; i < pts.size(); i++) {
            assertTrue(pts.get(i) != Frame.NO_PTS, "picture " + i + " has no time");
            assertEquals(first + i * FRAME_NANOS, pts.get(i), 1_000_000, "picture " + i);
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"clip-vp9.webm", "clip-vp9-10bit.webm", "clip-vp9-444.webm", "clip-av1.mkv"})
    @DisplayName("converts to opaque BGRA, and 4:4:4, 4:2:0 and 10-bit agree on the test pattern's colours")
    void convertsToBgra(String name) {
        var reference = firstPicture("clip-vp9.webm");
        var converted = firstPicture(name);
        var drift = 0L;
        for (var i = 0; i < converted.length; i++) {
            assertEquals(0xFF, converted[i] >>> 24, "pixel " + i + " is not opaque");
            drift += channelDistance(converted[i], reference[i]);
        }
        // Different encoders at a low bit rate differ in detail, not in colour.
        var mean = drift / (double) (converted.length * 3);
        assertTrue(mean < 12, name + " is " + mean + " levels a channel from VP9's first picture");
    }

    @Test
    @DisplayName("the conversion is the same bytes every time it runs")
    void conversionIsRepeatable() {
        assertTrue(java.util.Arrays.equals(firstPicture("clip-av1.mkv"), firstPicture("clip-av1.mkv")));
    }

    @Test
    @DisplayName("a converter refuses a target too small for the picture, before swscale writes to it")
    void refusesSmallTarget() {
        decodeVideo("clip-vp9-10bit.webm", frame -> {
            try (var converter = new VideoConverter(ffmpeg);
                    var arena = Arena.ofConfined()) {
                var small = arena.allocate(16);
                assertThrows(IllegalArgumentException.class, () -> converter.toBgra(frame, small, WIDTH * 4));
                var target = arena.allocate((long) WIDTH * 4 * HEIGHT);
                assertThrows(IllegalArgumentException.class, () -> converter.toBgra(frame, target, WIDTH));
            }
        });
    }

    @Test
    @DisplayName("a closed converter refuses to convert")
    void closedConverter() {
        var converter = new VideoConverter(ffmpeg);
        converter.close();
        converter.close();
        decodeVideo("clip-vp9-10bit.webm", frame -> {
            try (var arena = Arena.ofConfined()) {
                var target = arena.allocate((long) WIDTH * 4 * HEIGHT);
                assertThrows(IllegalStateException.class, () -> converter.toBgra(frame, target, WIDTH * 4));
            }
        });
    }

    @Test
    @DisplayName("a picture whose format swscale cannot write is a MediaException, not a crash")
    void unsupportedConversion() {
        decodeVideo("clip-vp9-10bit.webm", frame -> {
            try (var converter = new VideoConverter(ffmpeg);
                    var arena = Arena.ofConfined()) {
                var target = arena.allocate((long) WIDTH * 4 * HEIGHT);
                var video = ffmpeg.constants().video();
                assertThrows(
                        MediaException.class,
                        () -> converter.convert(
                                WIDTH,
                                HEIGHT,
                                video.avPixelFormat(frame.format()),
                                frame.planes(),
                                frame.strides(),
                                video.swsCsItu601(),
                                false,
                                video.pixFmtNone(),
                                List.of(target),
                                List.of(WIDTH * 4)));
            }
        });
    }

    /// The first picture of `name`, as `0xAARRGGBB` ints.
    private int[] firstPicture(String name) {
        var pixels = new int[WIDTH * HEIGHT];
        var done = new boolean[1];
        decodeVideo(name, frame -> {
            if (done[0]) {
                return;
            }
            done[0] = true;
            try (var converter = new VideoConverter(ffmpeg);
                    var arena = Arena.ofConfined()) {
                var stride = WIDTH * 4 + 64;
                var target = arena.allocate((long) stride * HEIGHT);
                converter.toBgra(frame, target, stride);
                for (var y = 0; y < HEIGHT; y++) {
                    MemorySegment.copy(target, JAVA_INT, (long) y * stride, pixels, y * WIDTH, WIDTH);
                }
            }
        });
        return pixels;
    }

    private static int channelDistance(int a, int b) {
        var sum = 0;
        for (var shift = 0; shift < 24; shift += 8) {
            sum += Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF));
        }
        return sum;
    }
}
