package io.github.digitalsmile.goldberry.media.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;
import io.github.digitalsmile.goldberry.media.platform.fixtures.Fixtures;

/// GStreamer decoding the fixtures, against FFmpeg's own decoder.
///
/// H.264 and HEVC decoding is bit-exact by specification, so every picture
/// GStreamer hands over must hash to what FFmpeg's decoder made of the same clip
/// (`*.framemd5`), and arrive in the same order at the same time, whichever
/// decoder the system ranks first. The hashes are of NV12 and P010, and a
/// planar picture is hashed as its semi-planar twin ([Fixtures#md5]).
@DisplayName("GStreamer, against FFmpeg's decoder")
class GStreamerVideoDecodeTest {

    private final GStreamerVideoProvider provider = new GStreamerVideoProvider();

    @BeforeEach
    void requireGStreamer() {
        GStreamerRequirement.enforce();
    }

    private List<VideoFrame> decodeAll(String name, List<String> hashes) {
        var frames = new ArrayList<VideoFrame>();
        Fixtures.decode(name, MediaType.VIDEO, provider::open, frame -> {
            var picture = assertInstanceOf(VideoFrame.class, frame);
            hashes.add(Fixtures.md5(picture));
            frames.add(picture);
        });
        return frames;
    }

    @ParameterizedTest(name = "{0} matches {1}, {2}-byte samples")
    @CsvSource({
        "clip-h264-high.mp4, clip-h264-high.mp4, 1",
        // Matroska stores no decoding times: the order must not need them.
        "clip-h264-high.mkv, clip-h264-high.mp4, 1",
        "clip-h264-full-709.mp4, clip-h264-full-709.mp4, 1",
        "clip-h264-720p.mp4, clip-h264-720p.mp4, 1",
        "clip-hevc.mp4, clip-hevc.mp4, 1",
        "clip-hevc-10bit.mp4, clip-hevc-10bit.mp4, 2",
    })
    @DisplayName("every picture is FFmpeg's, byte for byte, in presentation order at its time")
    void bitExact(String name, String reference, int bytesPerSample) {
        var expected = Fixtures.frameHashes(reference);
        var hashes = new ArrayList<String>();
        var frames = decodeAll(name, hashes);

        assertEquals(expected.size(), frames.size(), "pictures");
        for (var i = 0; i < expected.size(); i++) {
            var frame = frames.get(i);
            assertEquals(bytesPerSample, frame.format().bytesPerSample(), "picture " + i + " is " + frame.format());
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
        // Untagged and under 720 rows: BT.601, limited, the built-in decoder's default.
        "clip-h264-high.mp4, BT601, false",
        // Untagged at 720 rows: BT.709 by the same default.
        "clip-h264-720p.mp4, BT709, false",
        // Tagged in the VUI: what the stream says.
        "clip-h264-full-709.mp4, BT709, true",
        "clip-hevc-10bit.mp4, BT2020, false",
    })
    @DisplayName("the matrix and range are the stream's, or the built-in decoder's defaults")
    void colour(String name, VideoFrame.ColorMatrix matrix, boolean fullRange) {
        var frames = decodeAll(name, new ArrayList<>());
        for (var frame : frames) {
            assertEquals(matrix, frame.matrix());
            assertEquals(fullRange, frame.fullRange());
        }
    }

    @Test
    @DisplayName("a flush drops what is in flight, and decoding from a keyframe after it is exact again")
    void flushAndSeek() {
        var expected = Fixtures.frameHashes("clip-h264-high.mp4");
        try (var demuxer = Fixtures.demux("clip-h264-high.mp4")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = provider.open(demuxer.request(track.index()))) {
                // Some pictures in, some out.
                var sent = 0;
                while (sent < 7) {
                    if (decoder.receive() instanceof Received.NeedsInput) {
                        try (var packet = demuxer.read()) {
                            assertTrue(decoder.send(packet));
                        }
                        sent++;
                    }
                }
                // A seek: keyframes are every ten pictures, so 0.5 s lands on 0.4 s.
                decoder.flush();
                demuxer.seek(500_000_000L);
                var after = new ArrayList<VideoFrame>();
                var hashes = new ArrayList<String>();
                Fixtures.run(demuxer, decoder, frame -> {
                    var picture = (VideoFrame) frame;
                    after.add(picture);
                    hashes.add(Fixtures.md5(picture));
                });
                assertFalse(after.isEmpty());
                assertEquals(400_000_000L, after.getFirst().ptsNanos(), 1_000_000);
                var first = expected.size() - after.size();
                assertEquals(10, first, "decoding resumed at the keyframe at 0.4 s");
                for (var i = 0; i < after.size(); i++) {
                    assertEquals(expected.get(first + i).md5(), hashes.get(i), "picture " + (first + i));
                }
            }
        }
    }

    @Test
    @DisplayName("a decoder can be flushed after the end, and decodes the stream again")
    void flushAfterTheEnd() {
        try (var demuxer = Fixtures.demux("clip-hevc.mp4")) {
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
    @DisplayName("the provider claims H.264 and HEVC with a configuration record, and nothing else")
    void claims() {
        try (var demuxer = Fixtures.demux("clip-h264-high.mp4")) {
            var video = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            var audio = demuxer.info().defaultTrack(MediaType.AUDIO).orElseThrow();
            assertEquals(CodecId.H264, video.codec());
            assertTrue(provider.supports(demuxer.request(video.index())));
            assertFalse(provider.supports(demuxer.request(audio.index())));
        }
    }
}
