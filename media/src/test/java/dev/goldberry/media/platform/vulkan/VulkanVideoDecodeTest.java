package dev.goldberry.media.platform.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.platform.fixtures.Fixtures;

/// Vulkan Video decoding the fixtures, against FFmpeg's own decoder, on a
/// machine whose GPU decodes; skipped on one without.
///
/// H.264, HEVC, VP9 and AV1 are bit-exact by specification, so every picture must hash to what
/// FFmpeg's decoder made of the same clip (`*.framemd5`), in the same order at
/// the same time.
@DisplayName("Vulkan Video, against FFmpeg's decoder")
class VulkanVideoDecodeTest {

    private final VulkanVideoProvider provider = new VulkanVideoProvider(VulkanVideoProvider.Policy.ALL);

    private void requireDecoding(String name) {
        Assumptions.assumeTrue(VideoDevice.get().isPresent(), "no Vulkan Video on this machine");
        try (var demuxer = Fixtures.demux(name)) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            Assumptions.assumeTrue(
                    provider.supports(demuxer.request(track.index())),
                    VideoDevice.get().orElseThrow().name() + " does not decode " + name);
        }
    }

    private List<VideoFrame> decodeAll(String name, List<String> hashes) {
        requireDecoding(name);
        var frames = new ArrayList<VideoFrame>();
        Fixtures.decode(name, MediaType.VIDEO, provider::open, frame -> {
            var picture = assertInstanceOf(VideoFrame.class, frame);
            hashes.add(Fixtures.md5(picture));
            frames.add(picture);
        });
        return frames;
    }

    @ParameterizedTest(name = "{0} matches {1}, as {2}")
    @CsvSource({
        "clip-h264-high.mp4, clip-h264-high.mp4, NV12",
        // Matroska stores no decoding times: the order must not need them.
        "clip-h264-high.mkv, clip-h264-high.mp4, NV12",
        "clip-h264-full-709.mp4, clip-h264-full-709.mp4, NV12",
        "clip-h264-720p.mp4, clip-h264-720p.mp4, NV12",
        "clip-hevc-256.mp4, clip-hevc-256.mp4, NV12",
        // Open GOPs: CRA pictures lead RASL pictures, which refer back past them.
        "clip-hevc-256-open.mp4, clip-hevc-256-open.mp4, NV12",
        "clip-hevc-256-10bit.mp4, clip-hevc-256-10bit.mp4, P010",
        // Alt-ref frames, hidden in superframes.
        "clip-vp9-256.webm, clip-vp9-256.webm, NV12",
        "clip-vp9-256-10bit.webm, clip-vp9-256-10bit.webm, P010",
        // Only where the GPU decodes AV1; this suite's NVIDIA Turing does not.
        "clip-av1-256.mkv, clip-av1-256.mkv, NV12",
    })
    @DisplayName("every picture is FFmpeg's, byte for byte, in presentation order at its time")
    void bitExact(String name, String reference, PixelFormat format) {
        var expected = Fixtures.frameHashes(reference);
        var hashes = new ArrayList<String>();
        var frames = decodeAll(name, hashes);

        assertEquals(expected.size(), frames.size(), "pictures");
        for (var i = 0; i < expected.size(); i++) {
            var frame = frames.get(i);
            assertEquals(format, frame.format(), "picture " + i);
            assertEquals(expected.get(i).ptsNanos(), frame.ptsNanos(), 1_000_000, "the time of picture " + i);
            assertEquals(expected.get(i).md5(), hashes.get(i), "the bytes of picture " + i);
        }
    }

    @Test
    @DisplayName("the crop is applied: 160×90 is coded as 160×96 and shown as 160×90")
    void cropped() {
        var frames = decodeAll("clip-h264-high.mp4", new ArrayList<>());
        assertEquals(160, frames.getFirst().width());
        assertEquals(90, frames.getFirst().height());
    }

    @ParameterizedTest(name = "{0}: {1}, full range {2}")
    @CsvSource({
        "clip-h264-high.mp4, BT601, false",
        "clip-h264-720p.mp4, BT709, false",
        "clip-h264-full-709.mp4, BT709, true",
    })
    @DisplayName("the matrix and range are the stream's, or the built-in decoder's defaults")
    void colour(String name, VideoFrame.ColorMatrix matrix, boolean fullRange) {
        for (var frame : decodeAll(name, new ArrayList<>())) {
            assertEquals(matrix, frame.matrix());
            assertEquals(fullRange, frame.fullRange());
        }
    }

    @Test
    @DisplayName("a flush drops what is in flight, and decoding from a keyframe after it is exact again")
    void flushAndSeek() {
        requireDecoding("clip-h264-high.mp4");
        var expected = Fixtures.frameHashes("clip-h264-high.mp4");
        try (var demuxer = Fixtures.demux("clip-h264-high.mp4")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = provider.open(demuxer.request(track.index()))) {
                var sent = 0;
                while (sent < 7) {
                    if (decoder.receive() instanceof Received.NeedsInput) {
                        try (var packet = demuxer.read()) {
                            assertTrue(decoder.send(packet));
                        }
                        sent++;
                    }
                }
                // Keyframes are every ten pictures, so 0.5 s lands on 0.4 s.
                decoder.flush();
                demuxer.seek(500_000_000L);
                var hashes = new ArrayList<String>();
                var times = new ArrayList<Long>();
                Fixtures.run(demuxer, decoder, frame -> {
                    hashes.add(Fixtures.md5((VideoFrame) frame));
                    times.add(frame.ptsNanos());
                });
                assertFalse(hashes.isEmpty());
                assertEquals(400_000_000L, times.getFirst(), 1_000_000);
                var first = expected.size() - hashes.size();
                assertEquals(10, first, "decoding resumed at the keyframe at 0.4 s");
                for (var i = 0; i < hashes.size(); i++) {
                    assertEquals(expected.get(first + i).md5(), hashes.get(i), "picture " + (first + i));
                }
            }
        }
    }

    @Test
    @DisplayName("a seek onto a CRA skips the RASL pictures before it, and every picture after is exact")
    void seekOntoCra() {
        requireDecoding("clip-hevc-256-open.mp4");
        var expected = new HashMap<Long, String>();
        for (var picture : Fixtures.frameHashes("clip-hevc-256-open.mp4")) {
            expected.put(picture.ptsNanos() / 1_000_000, picture.md5());
        }
        try (var demuxer = Fixtures.demux("clip-hevc-256-open.mp4")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = provider.open(demuxer.request(track.index()))) {
                demuxer.seek(500_000_000L);
                var times = new ArrayList<Long>();
                Fixtures.run(demuxer, decoder, frame -> {
                    var millis = frame.ptsNanos() / 1_000_000;
                    times.add(millis);
                    assertEquals(expected.get(millis), Fixtures.md5((VideoFrame) frame), "the picture at " + millis);
                });
                assertFalse(times.isEmpty());
                assertEquals(times.stream().sorted().toList(), times, "in presentation order");
                assertTrue(times.getFirst() >= 400, "nothing from before the CRA: " + times.getFirst());
            }
        }
    }

    @Test
    @DisplayName("VP9: a seek lands on a keyframe, and every picture after it is exact")
    void vp9Seek() {
        requireDecoding("clip-vp9-256.webm");
        var expected = new HashMap<Long, String>();
        for (var picture : Fixtures.frameHashes("clip-vp9-256.webm")) {
            expected.put(picture.ptsNanos() / 1_000_000, picture.md5());
        }
        try (var demuxer = Fixtures.demux("clip-vp9-256.webm")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = provider.open(demuxer.request(track.index()))) {
                var first = new ArrayList<String>();
                Fixtures.run(demuxer, decoder, frame -> first.add(Fixtures.md5((VideoFrame) frame)));
                assertEquals(expected.size(), first.size(), "the whole stream first");
                decoder.flush();
                demuxer.seek(500_000_000L);
                var times = new ArrayList<Long>();
                Fixtures.run(demuxer, decoder, frame -> {
                    var millis = frame.ptsNanos() / 1_000_000;
                    times.add(millis);
                    assertEquals(expected.get(millis), Fixtures.md5((VideoFrame) frame), "the picture at " + millis);
                });
                assertFalse(times.isEmpty());
                assertEquals(400L, times.getFirst(), "the keyframe at 0.4 s");
            }
        }
    }

    @Test
    @DisplayName("VP9: show_existing_frame shows a slot's picture again, with no decode")
    void vp9ShowExistingFrame() {
        requireDecoding("clip-vp9-256.webm");
        try (var demuxer = Fixtures.demux("clip-vp9-256.webm");
                var arena = Arena.ofConfined()) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = provider.open(demuxer.request(track.index()))) {
                var shown = new ArrayList<String>();
                Packet last = null;
                for (var i = 0; i < 3; i++) {
                    var packet = demuxer.read();
                    assertTrue(decoder.send(packet));
                    shown.addAll(received(decoder));
                    if (last != null) {
                        last.close();
                    }
                    last = packet;
                }
                assertFalse(shown.isEmpty());
                // frame_marker, profile 0, show_existing_frame, frame_to_show_map_idx 0
                // (LAST, which every shown frame here refreshes).
                var existing = Packet.of(
                        arena.allocateFrom(JAVA_BYTE, (byte) 0x88),
                        last.streamIndex(),
                        last.pts() + 1,
                        last.pts() + 1,
                        0,
                        false,
                        last.timeBase());
                last.close();
                assertTrue(decoder.send(existing));
                var again = received(decoder);
                assertEquals(List.of(shown.getLast()), again);
            }
        }
    }

    /// Every picture `decoder` has ready, hashed.
    private static List<String> received(Decoder decoder) {
        var hashes = new ArrayList<String>();
        while (decoder.receive() instanceof Received.Decoded(var frame)) {
            hashes.add(Fixtures.md5((VideoFrame) frame));
        }
        return hashes;
    }

    @Test
    @DisplayName("a decoder can be flushed after the end, and decodes the stream again")
    void flushAfterTheEnd() {
        requireDecoding("clip-h264-high.mp4");
        try (var demuxer = Fixtures.demux("clip-h264-high.mp4")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = provider.open(demuxer.request(track.index()))) {
                var first = new ArrayList<String>();
                Fixtures.run(demuxer, decoder, frame -> first.add(Fixtures.md5((VideoFrame) frame)));
                decoder.flush();
                demuxer.seek(0);
                var second = new ArrayList<String>();
                Fixtures.run(demuxer, decoder, frame -> second.add(Fixtures.md5((VideoFrame) frame)));
                assertEquals(first, second);
            }
        }
    }

    @Test
    @DisplayName("HardwareDecoding.OFF, the audio track and the policy's codecs are honoured")
    void claims() {
        try (var demuxer = Fixtures.demux("clip-h264-high.mp4")) {
            var video = demuxer.request(
                    demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow().index());
            var audio = demuxer.request(
                    demuxer.info().defaultTrack(MediaType.AUDIO).orElseThrow().index());
            assertTrue(provider.claim(video).isPresent(), "the avcC names a profile and a size");
            assertFalse(provider.claim(video.withHardware(HardwareDecoding.OFF)).isPresent());
            assertFalse(provider.claim(audio).isPresent());
            assertFalse(new VulkanVideoProvider(VulkanVideoProvider.Policy.OFF).supports(video));
            var claim = provider.claim(video).orElseThrow();
            assertEquals(VulkanCodec.H264, claim.profile().codec());
            assertEquals(160, claim.width());
            assertEquals(96, claim.height());
        }
        for (var name : List.of("clip-hevc-256-10bit.mp4", "clip-vp9-256.webm", "clip-av1-256.mkv")) {
            try (var demuxer = Fixtures.demux(name)) {
                var claim = provider.claim(demuxer.request(demuxer.info()
                                .defaultTrack(MediaType.VIDEO)
                                .orElseThrow()
                                .index()))
                        .orElseThrow(() -> new AssertionError(name + " is not claimed"));
                assertEquals(256, claim.width(), name);
                assertEquals(144, claim.height(), name);
            }
        }
    }
}
