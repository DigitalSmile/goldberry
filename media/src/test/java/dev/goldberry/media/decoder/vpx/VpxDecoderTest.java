package dev.goldberry.media.decoder.vpx;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.decoder.FrameHashes;
import dev.goldberry.media.ffm.MediaNatives;

/// libvpx through the SPI, against FFmpeg's checksums of the same pictures:
/// VP8 and VP9 in 8 and 10 bits, a 4:4:4 clip subsampled on the way out, the
/// colour tags, and a sticker whose alpha rides beside its pictures.
@DisplayName("VpxDecoder")
class VpxDecoderTest {

    private static final long FRAME_NANOS = 40_000_000L;

    private final VpxProvider provider = new VpxProvider();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    @ParameterizedTest(name = "{0}: {1} pictures")
    @CsvSource({
        "clip-vp8.webm, 25",
        "clip-vp9.webm, 25",
        "clip-vp9-10bit.webm, 5",
        "clip-vp9-2020-10bit.webm, 5",
        "clip-two-angles.mkv, 25",
    })
    @DisplayName("decodes a clip to the pictures FFmpeg's libvpx decoded, byte for byte")
    void matchesFfmpeg(String name, int pictures) {
        assertEquals(pictures, FrameHashes.assertMatchesFfmpeg(name, provider));
    }

    @Test
    @DisplayName("the sticker's colour matches FFmpeg's, and its alpha is the square over the half-clear band")
    void stickerWithAlpha() {
        var name = "sticker-vp9-alpha.webm";
        var expected = FrameHashes.expected(name);
        var actual = new ArrayList<String>();
        FrameHashes.decodeVideo(name, provider, frame -> {
            assertEquals(PixelFormat.I420A, frame.format());
            assertEquals(64, frame.width());
            assertEquals(64, frame.height());
            actual.add(FrameHashes.md5(colourOf(frame)));
            assertAlphaIsTheSticker(frame);
        });
        assertEquals(10, actual.size());
        assertEquals(expected, actual);
    }

    /// `frame` without its alpha plane: what FFmpeg's `framemd5` summed.
    private static VideoFrame colourOf(VideoFrame frame) {
        return new VideoFrame(
                PixelFormat.I420,
                frame.width(),
                frame.height(),
                frame.planes().subList(0, 3),
                frame.strides().subList(0, 3),
                frame.matrix(),
                frame.fullRange(),
                frame.ptsNanos());
    }

    /// The fixture's alpha: opaque in a square in the top half, clear around
    /// it, and half in the bottom 24 rows.
    private static void assertAlphaIsTheSticker(VideoFrame frame) {
        var alpha = frame.planes().get(3);
        var stride = frame.strides().get(3);
        var opaque = 0;
        var clear = 0;
        var half = 0;
        for (var y = 0; y < frame.height(); y++) {
            for (var x = 0; x < frame.width(); x++) {
                var value = alpha.get(JAVA_BYTE, (long) y * stride + x) & 0xFF;
                if (value >= 240) {
                    opaque++;
                } else if (value <= 16) {
                    clear++;
                } else if (value >= 96 && value <= 160 && y >= frame.height() - 24) {
                    half++;
                }
            }
        }
        assertTrue(opaque > 0, "an opaque square");
        assertTrue(clear > 0, "a clear surround");
        assertTrue(half > 0, "a half-alpha band");
    }

    @Test
    @DisplayName("the second angle, a VP8 track behind a VP9 one, decodes to FFmpeg's pictures of it")
    void secondAngle() {
        var name = "clip-two-angles.mkv";
        var expected = sums("/dev/goldberry/media/container/clip-two-angles-mkv-track1.framemd5");
        var actual = new ArrayList<String>();
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().tracks(MediaType.VIDEO).get(1);
            assertEquals(CodecId.VP8, track.codec());
            container.select(Set.of(track.index()));
            var request = container.request(track.index());
            assertTrue(provider.supports(request));
            try (var decoder = provider.open(request)) {
                FrameHashes.drain(container, decoder, frame -> {
                    var picture = assertInstanceOf(VideoFrame.class, frame);
                    assertEquals(96, picture.width());
                    assertEquals(54, picture.height());
                    assertEquals(PixelFormat.I420, picture.format());
                    actual.add(FrameHashes.md5(picture));
                });
            }
        }
        assertEquals(25, actual.size());
        assertEquals(expected, actual);
    }

    /// The sums in a `framemd5` fixture that is not named after its clip, made
    /// by `ffmpeg -i clip-two-angles.mkv -map 0:v:1 -f framemd5 -`.
    private static List<String> sums(String resource) {
        String text;
        try (var in = Objects.requireNonNull(VpxDecoderTest.class.getResourceAsStream(resource), resource)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var sums = new ArrayList<String>();
        for (var line : text.split("\n")) {
            if (!line.isBlank() && !line.startsWith("#")) {
                var fields = line.split(",");
                sums.add(fields[fields.length - 1].strip());
            }
        }
        return sums;
    }

    /// Subsampling leaves luma alone, so a 4:4:4 clip's Y plane is held to
    /// FFmpeg's sum of it alone (`-vf extractplanes=y -f framemd5`, in
    /// `<stem>-<extension>-luma.framemd5`); the chroma is libyuv's average, not
    /// swscale's, and is held to its range.
    @ParameterizedTest(name = "{0} becomes {1}")
    @CsvSource({"clip-vp9-444.webm, I420", "clip-vp9-444-10bit.webm, I010"})
    @DisplayName("a 4:4:4 clip is handed over as 160×90 4:2:0, its luma untouched")
    void subsamplesFourFourFour(String name, PixelFormat format) {
        var dot = name.lastIndexOf('.');
        var expected = sums("/dev/goldberry/media/container/" + name.substring(0, dot) + "-" + name.substring(dot + 1)
                + "-luma.framemd5");
        var luma = new ArrayList<String>();
        FrameHashes.decodeVideo(name, provider, frame -> {
            assertEquals(format, frame.format());
            assertEquals(160, frame.width());
            assertEquals(90, frame.height());
            assertTrue(frame.strides().get(0) >= 160 * format.bytesPerSample());
            assertTrue(frame.strides().get(1) >= 80 * format.bytesPerSample());
            luma.add(md5OfPlane(frame, 0));
            if (format == PixelFormat.I010) {
                assertChromaIsTenBit(frame);
            }
        });
        assertEquals(5, luma.size());
        assertEquals(expected, luma);
    }

    private static String md5OfPlane(VideoFrame frame, int plane) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var rows = frame.format().planeRows(plane, frame.height());
        var rowBytes = frame.format().planeRowBytes(plane, frame.width());
        var row = new byte[rowBytes];
        for (var y = 0; y < rows; y++) {
            MemorySegment.copy(
                    frame.planes().get(plane),
                    JAVA_BYTE,
                    (long) y * frame.strides().get(plane),
                    row,
                    0,
                    rowBytes);
            digest.update(row);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /// Every chroma sample fits 10 bits and the plane is not blank: what a
    /// stride counted in the wrong unit would spoil.
    private static void assertChromaIsTenBit(VideoFrame frame) {
        for (var plane = 1; plane <= 2; plane++) {
            var rows = frame.format().planeRows(plane, frame.height());
            var samples = frame.format().planeRowBytes(plane, frame.width()) / 2;
            var stride = frame.strides().get(plane);
            var data = frame.planes().get(plane);
            var distinct = new HashSet<Integer>();
            for (var y = 0; y < rows; y++) {
                for (var x = 0; x < samples; x++) {
                    var value = data.get(JAVA_SHORT_UNALIGNED, (long) y * stride + 2L * x) & 0xFFFF;
                    assertTrue(value <= 1023, "plane " + plane + " sample " + value);
                    distinct.add(value);
                }
            }
            assertTrue(distinct.size() > 1, "plane " + plane + " is blank");
        }
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"clip-vp9-10bit.webm, BT601", "clip-vp9-2020-10bit.webm, BT2020"})
    @DisplayName("a 10-bit clip is I010 in 16-bit words, with its matrix")
    void tenBit(String name, VideoFrame.ColorMatrix matrix) {
        var count = new int[1];
        FrameHashes.decodeVideo(name, provider, frame -> {
            assertEquals(PixelFormat.I010, frame.format());
            assertEquals(matrix, frame.matrix());
            assertFalse(frame.fullRange());
            assertTrue(frame.strides().get(0) >= 2 * frame.width());
            assertTrue(frame.strides().get(1) >= frame.width());
            count[0]++;
        });
        assertEquals(5, count[0]);
    }

    @Test
    @DisplayName("a BT.709 tag is read, and so is full range")
    void colourTags() {
        FrameHashes.decodeVideo("clip-vp9-709.webm", provider, frame -> {
            assertEquals(VideoFrame.ColorMatrix.BT709, frame.matrix());
            assertFalse(frame.fullRange());
        });
        FrameHashes.decodeVideo("clip-vp9-full.webm", provider, frame -> {
            assertEquals(VideoFrame.ColorMatrix.BT601, frame.matrix());
            assertTrue(frame.fullRange());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"clip-vp8.webm", "clip-vp9.webm"})
    @DisplayName("every picture is 160×90, limited-range BT.601, 40 ms after the one before")
    void describesPictures(String name) {
        var pts = new ArrayList<Long>();
        FrameHashes.decodeVideo(name, provider, frame -> {
            assertEquals(PixelFormat.I420, frame.format());
            assertEquals(160, frame.width());
            assertEquals(90, frame.height());
            assertEquals(VideoFrame.ColorMatrix.BT601, frame.matrix());
            assertFalse(frame.fullRange());
            pts.add(frame.ptsNanos());
        });
        assertEquals(25, pts.size());
        for (var i = 0; i < pts.size(); i++) {
            assertEquals(i * FRAME_NANOS, pts.get(i), "picture " + i);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"clip-vp8.webm", "clip-vp9.webm"})
    @DisplayName("a flush starts over: the first picture again after a seek to the start")
    void flushes(String name) {
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            try (var decoder = provider.open(container.request(track.index()))) {
                var first = firstPicture(container, decoder);
                container.seek(0);
                decoder.flush();
                assertEquals(first, firstPicture(container, decoder));
            }
        }
    }

    private static String firstPicture(MatroskaContainer container, Decoder decoder) {
        while (true) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> {
                    return FrameHashes.md5(assertInstanceOf(VideoFrame.class, frame));
                }
                case Received.NeedsInput _ -> {
                    try (var packet = container.read()) {
                        assertTrue(packet != null && decoder.send(packet));
                    }
                }
                case Received.Ended _ -> throw new AssertionError("ended before a picture");
            }
        }
    }

    @Test
    @DisplayName("a packet is refused while a picture of the last one is still to be fetched")
    void refusesWhilePicturesPend() {
        var name = "clip-vp9.webm";
        try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            var decoder = provider.open(container.request(track.index()));
            assertEquals(Received.NEEDS_INPUT, decoder.receive());
            try (var packet = container.read()) {
                assertTrue(packet != null && decoder.send(packet));
            }
            assertInstanceOf(Received.Decoded.class, decoder.receive());
            try (var packet = container.read()) {
                assertTrue(packet != null);
                assertFalse(decoder.send(packet), "the first picture is still borrowed");
                assertEquals(Received.NEEDS_INPUT, decoder.receive());
                assertTrue(decoder.send(packet));
            }
            assertInstanceOf(Received.Decoded.class, decoder.receive());
            decoder.sendEnd();
            assertEquals(Received.ENDED, decoder.receive());
            decoder.close();
            decoder.close();
        }
    }

    @Test
    @DisplayName("the provider is libvpx, for VP9 and VP8 and not AV1")
    void provider() {
        assertEquals("libvpx", provider.name());
        assertEquals(VpxProvider.NAME, provider.name());
        for (var name : List.of("clip-vp8.webm", "clip-vp9.webm")) {
            try (var container = MatroskaContainer.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
                assertTrue(provider.supports(container.request(0)), name);
            }
        }
        var av1 = "clip-av1.mkv";
        try (var container = MatroskaContainer.open(ClipFixtures.source(av1), ClipFixtures.io(av1))) {
            assertFalse(provider.supports(container.request(0)), "AV1 is not libvpx's");
        }
        assertTrue(MediaNatives.isAvailable());
    }
}
