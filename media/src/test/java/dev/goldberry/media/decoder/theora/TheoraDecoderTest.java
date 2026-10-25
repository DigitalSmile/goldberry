package dev.goldberry.media.decoder.theora;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.bitstream.XiphLacing;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Containers;
import dev.goldberry.media.decoder.FrameHashes;

/// libtheora through the SPI, against FFmpeg's checksums of the same pictures:
/// Theora in Ogg and in Matroska, cropped from the coded 160×96 to the picture's
/// 160×90, 4:4:4 and 4:2:2 subsampled on the way out, a repeated frame, and
/// headers it must refuse.
@DisplayName("TheoraDecoder")
class TheoraDecoderTest {

    private static final long FRAME_NANOS = 40_000_000L;

    private final TheoraProvider provider = new TheoraProvider();

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-theora.ogv", "clip-theora.mkv"})
    @DisplayName("decodes a clip to the pictures FFmpeg decoded, byte for byte, cropped to 160×90")
    void matchesFfmpeg(String name) {
        assertEquals(25, FrameHashes.assertMatchesFfmpeg(name, provider));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-theora.ogv", "clip-theora.mkv"})
    @DisplayName("hands over I420 at 160×90, BT.601 in limited range, one picture every 40 ms from zero")
    void describesItsPictures(String name) {
        var times = new ArrayList<Long>();
        FrameHashes.decodeVideo(name, provider, frame -> {
            assertEquals(PixelFormat.I420, frame.format());
            assertEquals(160, frame.width());
            assertEquals(90, frame.height());
            assertEquals(VideoFrame.ColorMatrix.BT601, frame.matrix());
            assertFalse(frame.fullRange());
            times.add(frame.ptsNanos());
        });
        assertEquals(25, times.size());
        for (var i = 0; i < times.size(); i++) {
            assertEquals(i * FRAME_NANOS, times.get(i), "picture " + i);
        }
    }

    /// Subsampling leaves luma alone, so a 4:4:4 or 4:2:2 clip's Y plane is held
    /// to FFmpeg's sum of it alone (`-vf extractplanes=y -f framemd5`, in
    /// `<stem>-<extension>-luma.framemd5`); the chroma is libyuv's average.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-theora-444.ogv", "clip-theora-422.ogv"})
    @DisplayName("a 4:4:4 or 4:2:2 clip is handed over as 160×90 4:2:0, its luma untouched")
    void subsamples(String name) {
        var dot = name.lastIndexOf('.');
        var expected = sums("/dev/goldberry/media/container/" + name.substring(0, dot) + "-" + name.substring(dot + 1)
                + "-luma.framemd5");
        var luma = new ArrayList<String>();
        FrameHashes.decodeVideo(name, provider, frame -> {
            assertEquals(PixelFormat.I420, frame.format());
            assertEquals(160, frame.width());
            assertEquals(90, frame.height());
            assertEquals(80, frame.strides().get(1));
            luma.add(md5OfLuma(frame));
        });
        assertEquals(5, luma.size());
        assertEquals(expected, luma);
    }

    @Test
    @DisplayName("an empty packet repeats the picture before it at its own time, and the stream goes on unchanged")
    void repeatsAFrame() {
        var name = "clip-theora.ogv";
        var expected = FrameHashes.expected(name);
        var sums = new ArrayList<String>();
        var times = new ArrayList<Long>();
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name));
                var arena = Arena.ofConfined()) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            try (var decoder = provider.open(container.request(track.index()))) {
                var count = 0;
                for (var packet = container.read(); packet != null; packet = container.read()) {
                    try (var frame = packet) {
                        feed(decoder, frame, sums, times);
                    }
                    if (++count == 3) {
                        // A dropped frame, as an encoder writes one, at 3.5 frames.
                        var timed = Packet.of(
                                arena.allocate(1).asSlice(0, 0),
                                track.index(),
                                container.timeBase(track.index()).fromNanos(3 * FRAME_NANOS),
                                0,
                                1,
                                false,
                                container.timeBase(track.index()));
                        feed(decoder, timed, sums, times);
                    }
                }
                decoder.sendEnd();
                assertEquals(Received.ENDED, decoder.receive());
            }
        }
        assertEquals(26, sums.size());
        assertEquals(expected.get(2), sums.get(3), "the repeat is the picture before it");
        assertEquals(3 * FRAME_NANOS, times.get(3), "at the empty packet's time");
        var rest = new ArrayList<>(sums);
        rest.remove(3);
        assertEquals(expected, rest, "every other picture as FFmpeg decoded it");
    }

    private static void feed(Decoder decoder, Packet packet, List<String> sums, List<Long> times) {
        assertTrue(decoder.send(packet));
        while (decoder.receive() instanceof Received.Decoded(var frame)) {
            var picture = assertInstanceOf(VideoFrame.class, frame);
            sums.add(FrameHashes.md5(picture));
            times.add(picture.ptsNanos());
        }
    }

    @Test
    @DisplayName("after a flush, decodes from a keyframe to FFmpeg's pictures from there on")
    void flushesToAKeyframe() {
        var name = "clip-theora.ogv";
        var expected = FrameHashes.expected(name);
        var sums = new ArrayList<String>();
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            try (var decoder = provider.open(container.request(track.index()))) {
                var index = 0;
                for (var packet = container.read(); packet != null; packet = container.read(), index++) {
                    try (var frame = packet) {
                        if (index < 4) {
                            feed(decoder, frame, new ArrayList<>(), new ArrayList<>());
                        } else if (index == 4) {
                            decoder.flush();
                        } else if (index >= 10) {
                            assertTrue(index != 10 || frame.keyframe(), "picture 10 is a keyframe");
                            feed(decoder, frame, sums, new ArrayList<>());
                        }
                    }
                }
            }
        }
        assertEquals(expected.subList(10, expected.size()), sums);
    }

    @Test
    @DisplayName("an empty packet first, before any picture, hands over nothing")
    void repeatsNothingAtTheStart() {
        var name = "clip-theora.ogv";
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name));
                var arena = Arena.ofConfined()) {
            var request = container.request(0);
            try (var decoder = provider.open(request)) {
                var empty = Packet.of(arena.allocate(1).asSlice(0, 0), 0, 0, 0, 1, false, request.timeBase());
                assertTrue(decoder.send(empty));
                assertEquals(Received.NEEDS_INPUT, decoder.receive());
            }
        }
    }

    @Test
    @DisplayName("refuses a record that is not three Theora headers, and one that states a frame past 8K")
    void refusesBadHeaders() {
        var name = "clip-theora.ogv";
        byte[] record;
        DecoderRequest request;
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            request = container.request(0);
            record = request.extradata().toArray(JAVA_BYTE);
        }
        var headers = XiphLacing.split(record);
        assertEquals(3, headers.size());
        assertEquals("theora", new String(headers.getFirst(), 1, 6, StandardCharsets.US_ASCII));

        assertRefused(request, XiphLacing.lace(headers.subList(0, 2)), "not three");
        assertRefused(request, XiphLacing.lace(List.of(headers.get(1), headers.get(0), headers.get(2))), "header 0");
        assertRefused(request, new byte[0], "without its headers");

        // 9216 macroblock columns' worth: a frame 9216 wide, past the cap.
        var wide = headers.getFirst().clone();
        wide[10] = (byte) (9216 / 16 >> 8);
        wide[11] = (byte) (9216 / 16);
        assertRefused(request, XiphLacing.lace(List.of(wide, headers.get(1), headers.get(2))), "largest picture");
    }

    private void assertRefused(DecoderRequest request, byte[] record, String reason) {
        try (var arena = Arena.ofConfined()) {
            var extradata = record.length == 0 ? MemorySegment.NULL : arena.allocateFrom(JAVA_BYTE, record);
            var damaged = new DecoderRequest(CodecId.THEORA, "theora", request.params(), extradata, request.timeBase());
            var e = assertThrows(
                    MediaException.class, () -> provider.open(damaged).close());
            assertInstanceOf(MediaError.InvalidData.class, e.error());
            assertTrue(String.valueOf(e.getMessage()).contains(reason), e.getMessage());
        }
    }

    private static String md5OfLuma(VideoFrame frame) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var stride = frame.strides().getFirst();
        var row = new byte[frame.width()];
        for (var y = 0; y < frame.height(); y++) {
            MemorySegment.copy(frame.planes().getFirst(), JAVA_BYTE, (long) y * stride, row, 0, row.length);
            digest.update(row);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /// The sums in a `framemd5` fixture that is not named after its clip alone.
    private static List<String> sums(String resource) {
        String text;
        try (var in = Objects.requireNonNull(TheoraDecoderTest.class.getResourceAsStream(resource), resource)) {
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

    @ParameterizedTest(name = "{0}")
    @CsvSource({"clip-theora.ogv, yuv420p", "clip-theora-444.ogv, yuv444p", "clip-theora-422.ogv, yuv422p"})
    @DisplayName("the provider supports what the container describes, and nothing else")
    void supports(String name, String pixelFormat) {
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var request = container.request(0);
            assertTrue(provider.supports(request));
            var video = assertInstanceOf(TrackParams.Video.class, request.params());
            assertEquals(Optional.of(pixelFormat), video.pixelFormat());
            assertFalse(provider.supports(
                    new DecoderRequest(CodecId.VP8, "vp8", request.params(), request.extradata(), request.timeBase())));
        }
    }
}
