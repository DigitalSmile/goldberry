package dev.goldberry.media.container.ogg;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.bitstream.TheoraHeaders;
import dev.goldberry.media.bitstream.XiphLacing;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.FrameRate;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;

/// The Ogg Theora mapping against FFmpeg's read of the same files: `ffprobe`'s
/// packet list, timed in frames from the granule positions, keyframes from each
/// frame's own bit; the identification header read into a track; and a seek
/// that lands on a keyframe, over the fixture's pages and over re-paged copies
/// in both granule conventions.
@DisplayName("OggContainer, Theora")
class OggTheoraTest {

    private static final Rational FRAME = new Rational(1, 25);

    private static OggContainer open(String name) {
        return OggContainer.open(ClipFixtures.source(name), ClipFixtures.io(name));
    }

    private static OggContainer open(byte[] bytes) {
        return OggContainer.open(Source.of(URI.create("mem:///repaged.ogv")), new MemoryIO(bytes));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-theora.ogv", "clip-theora-444.ogv"})
    @DisplayName("hands over the packets ffprobe listed: stream, time, size, keyframe and duration, in file order")
    void packetsAsFfprobe(String name) {
        List<ClipFixtures.Expected> actual;
        try (var container = open(name)) {
            assertEquals(FRAME, container.timeBase(0), "one tick a frame");
            actual = OggContainerTest.drain(container);
        }
        var expected = ClipFixtures.packets(name);
        assertEquals(expected.size(), actual.size(), "packet count");
        for (var i = 0; i < expected.size(); i++) {
            var want = expected.get(i);
            var got = actual.get(i);
            var at = "packet " + i + " of " + name;
            assertEquals(want.stream(), got.stream(), at + ": stream");
            assertEquals(want.pts(), got.pts(), at + ": pts");
            assertEquals(want.size(), got.size(), at + ": size");
            assertEquals(want.keyframe(), got.keyframe(), at + ": keyframe");
            if (got.duration() != 0) {
                assertEquals(want.duration(), got.duration(), at + ": duration");
            }
        }
    }

    @Test
    @DisplayName("describes a Theora and Vorbis clip: 160×90 yuv420p at 25/1, the three headers laced, a second long")
    void describes() {
        try (var container = open("clip-theora.ogv")) {
            var info = container.info();
            assertEquals(2, info.tracks().size());
            var video = info.tracks().get(0);
            assertEquals(CodecId.THEORA, video.codec());
            assertEquals("theora", video.codecName());
            assertTrue(video.isDefault());
            var params = assertInstanceOf(TrackParams.Video.class, video.params());
            assertAll(
                    () -> assertEquals(160, params.width()),
                    () -> assertEquals(90, params.height()),
                    () -> assertEquals(Optional.of("yuv420p"), params.pixelFormat()),
                    () -> assertEquals(Optional.of(new FrameRate(25, 1)), params.frameRate()));
            assertEquals(CodecId.VORBIS, info.tracks().get(1).codec());
            assertEquals(Optional.of(Duration.ofSeconds(1)), video.duration());
            assertEquals(Optional.of(Duration.ofSeconds(1)), info.duration());

            var request = container.request(0);
            assertEquals(FRAME, request.timeBase());
            var headers = XiphLacing.split(request.extradata().toArray(JAVA_BYTE));
            assertEquals(3, headers.size());
            assertTrue(TheoraHeaders.isHeader(headers.get(0), TheoraHeaders.IDENTIFICATION));
            assertTrue(TheoraHeaders.isHeader(headers.get(1), TheoraHeaders.COMMENT));
            assertTrue(TheoraHeaders.isHeader(headers.get(2), TheoraHeaders.SETUP));
            var identification = TheoraHeaders.identification(headers.get(0));
            assertAll(
                    () -> assertEquals(160, identification.frameWidth()),
                    () -> assertEquals(96, identification.frameHeight()),
                    () -> assertEquals(6, identification.picYFromBottom(), "the picture at the top of the frame"),
                    () -> assertEquals(0x030201, identification.version()));
        }
    }

    @Test
    @DisplayName("a seek lands on a keyframe at or before the target, and the pictures from there are the tail")
    void seeksToAKeyframe() {
        try (var container = open("clip-theora.ogv")) {
            var all = video(OggContainerTest.drain(container));
            for (var millis : new long[] {0, 300, 500, 860, 990}) {
                container.seek(Duration.ofMillis(millis).toNanos());
                var fromSeek = video(OggContainerTest.drain(container));
                assertFalse(fromSeek.isEmpty(), millis + " ms");
                var first = fromSeek.getFirst();
                assertTrue(first.keyframe(), millis + " ms: a keyframe first, " + first);
                assertTrue(first.pts() <= millis / 40, millis + " ms: at or before the target, " + first);
                assertEquals(all.subList(all.size() - fromSeek.size(), all.size()), fromSeek, millis + " ms");
            }
        }
    }

    @ParameterizedTest(name = "bitstream 3.2.{0}")
    @ValueSource(ints = {0, 1})
    @DisplayName("three frames a page, in either granule convention: the same packets, and seeks to the keyframe")
    void repaged(int revision) {
        List<ClipFixtures.Expected> original;
        try (var container = open("clip-theora.ogv")) {
            container.select(Set.of(0));
            original = OggContainerTest.drain(container);
        }
        var bytes = OggWriter.repagedTheora(ClipFixtures.bytes("clip-theora.ogv"), 3, revision == 0);
        try (var container = open(bytes)) {
            assertEquals(Optional.of(Duration.ofSeconds(1)), container.info().duration());
            var all = OggContainerTest.drain(container);
            assertEquals(original, all, "the packets of the fixture, timed the same");
            // Keyframes at 0, 10 and 20. A target on a page past a keyframe
            // starts from it; one on the page before a keyframe starts from the
            // one before.
            for (var frame : new int[] {0, 5, 13, 21, 24}) {
                container.seek(FRAME.toNanos(frame));
                var fromSeek = OggContainerTest.drain(container);
                var first = fromSeek.getFirst();
                assertTrue(first.keyframe(), "frame " + frame + ": " + first);
                assertEquals(frame / 10 * 10, first.pts(), "frame " + frame + " starts at its keyframe");
                assertEquals(all.subList(all.size() - fromSeek.size(), all.size()), fromSeek, "frame " + frame);
            }
        }
    }

    @Test
    @DisplayName("a damaged identification header fails the open as invalid data")
    void refusesADamagedHeader() {
        var bytes = OggWriter.repagedTheora(ClipFixtures.bytes("clip-theora.ogv"), 3, false);
        var packets = OggWriter.packets(bytes);
        var cut = Arrays.copyOf(packets.getFirst(), 20);
        var pages = List.of(
                new OggWriter.Page(OggPage.BOS, 0, List.of(cut)),
                new OggWriter.Page(OggPage.EOS, 0, List.of(packets.get(1))));
        var e = assertThrows(MediaException.class, () -> open(OggWriter.write(3, pages)));
        assertInstanceOf(MediaError.InvalidData.class, e.error());
    }

    private static List<ClipFixtures.Expected> video(List<ClipFixtures.Expected> packets) {
        return packets.stream().filter(packet -> packet.stream() == 0).toList();
    }
}
