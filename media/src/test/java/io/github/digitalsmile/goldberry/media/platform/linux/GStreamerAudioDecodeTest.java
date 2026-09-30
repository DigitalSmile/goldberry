package io.github.digitalsmile.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.SampleFormat;
import io.github.digitalsmile.goldberry.media.platform.fixtures.Fixtures;
import io.github.digitalsmile.goldberry.media.platform.fixtures.Tones;

/// GStreamer decoding the fixtures' audio. Lossy audio is not bit-exact, so
/// the checks are on what survives encoding: the tone's frequency and level, the
/// channel each tone was put in, and timing that runs on without a gap or an
/// overlap, whichever decoder the system ranks first.
@DisplayName("GStreamer, on the fixtures' audio")
class GStreamerAudioDecodeTest {

    private static final int RATE = 48_000;
    /// The fixtures' tone: 12000/32768 of full scale.
    private static final double TONE_PEAK = 12_000 / 32_768.0;

    private final GStreamerAudioProvider provider = new GStreamerAudioProvider();

    @BeforeEach
    void requireGStreamer() {
        GStreamerRequirement.enforce();
    }

    /// What a track decoded to: the samples of each channel, and the frames.
    private record Decoded(float[][] channels, List<Long> pts, List<Integer> sizes) {}

    private Decoded decodeAll(String name) {
        var interleaved = new ArrayList<float[]>();
        var pts = new ArrayList<Long>();
        var sizes = new ArrayList<Integer>();
        var channelCount = new int[1];
        Fixtures.decode(name, MediaType.AUDIO, provider::open, frame -> {
            var audio = assertInstanceOf(AudioFrame.class, frame);
            assertEquals(SampleFormat.F32, audio.format());
            assertEquals(RATE, audio.sampleRate());
            channelCount[0] = audio.channels();
            var samples = new float[audio.samples() * audio.channels()];
            var plane = audio.planes().getFirst();
            for (var i = 0; i < samples.length; i++) {
                samples[i] = plane.getAtIndex(JAVA_FLOAT, i);
            }
            interleaved.add(samples);
            pts.add(audio.ptsNanos());
            sizes.add(audio.samples());
        });
        var count = channelCount[0];
        var total = sizes.stream().mapToInt(Integer::intValue).sum();
        var channels = new float[count][total];
        var at = 0;
        for (var chunk : interleaved) {
            for (var i = 0; i < chunk.length / count; i++) {
                for (var c = 0; c < count; c++) {
                    channels[c][at + i] = chunk[i * count + c];
                }
            }
            at += chunk.length / count;
        }
        return new Decoded(channels, pts, sizes);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-h264-high.mp4", "tone-aac.mkv", "tone-eac3.mp4"})
    @DisplayName("the 440 Hz tone at its level, in both channels, about a second of it")
    void tone(String name) {
        var decoded = decodeAll(name);
        assertEquals(2, decoded.channels().length);
        for (var channel : decoded.channels()) {
            // A second of tone, give or take the codec's priming and padding.
            assertEquals(RATE, channel.length, 3 * 1024, "samples");
            var middle = Arrays.copyOfRange(channel, RATE / 4, 3 * RATE / 4);
            assertEquals(440, dominant(middle, 100, 1000), 1, "frequency");
            assertEquals(TONE_PEAK / Math.sqrt(2), rms(middle), 0.02, "level");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-h264-high.mp4", "tone-aac.mkv", "tone-eac3.mp4"})
    @DisplayName("each frame starts where the one before it ended")
    void continuousTiming(String name) {
        var decoded = decodeAll(name);
        assertTrue(decoded.pts().size() > 10);
        for (var i = 1; i < decoded.pts().size(); i++) {
            assertTrue(decoded.pts().get(i) != Frame.NO_PTS);
            var expected = decoded.pts().get(i - 1) + decoded.sizes().get(i - 1) * 1_000_000_000L / RATE;
            assertEquals(expected, decoded.pts().get(i), 1_000, "the time of frame " + i);
        }
        // The first frame is timed by the first packet: within the priming of zero.
        assertEquals(0, decoded.pts().getFirst(), 50_000_000L);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tones-aac-5.1.mp4", "tones-ac3-5.1.mkv"})
    @DisplayName("5.1 comes out in FFmpeg's order: FL FR FC LFE BL BR")
    void channelOrder(String name) {
        var decoded = decodeAll(name);
        assertEquals(6, decoded.channels().length);
        // make-platform-fixtures.sh puts one frequency in each channel, in this order.
        int[] expected = {200, 300, 400, 60, 500, 600};
        var found = new int[6];
        for (var c = 0; c < 6; c++) {
            var middle = Arrays.copyOfRange(decoded.channels()[c], RATE / 4, 3 * RATE / 4);
            found[c] = dominant(middle, 40, 800);
        }
        for (var c = 0; c < 6; c++) {
            assertEquals(expected[c], found[c], 2, "channel " + c + " of " + Arrays.toString(found));
        }
    }

    @Test
    @DisplayName("a flush drops what is in flight, and timing restarts at the next packet")
    void flushAndSeek() {
        try (var demuxer = Fixtures.demux("tone-aac.mkv")) {
            var track = demuxer.info().defaultTrack(MediaType.AUDIO).orElseThrow();
            demuxer.select(Set.of(track.index()));
            try (var decoder = provider.open(demuxer.request(track.index()))) {
                for (var sent = 0; sent < 5; ) {
                    if (decoder.receive() instanceof Received.NeedsInput) {
                        try (var packet = demuxer.read()) {
                            assertTrue(decoder.send(packet));
                        }
                        sent++;
                    }
                }
                decoder.flush();
                demuxer.seek(600_000_000L);
                var pts = new ArrayList<Long>();
                Fixtures.run(demuxer, decoder, frame -> pts.add(frame.ptsNanos()));
                assertFalse(pts.isEmpty());
                // Matroska seeks to the packet at or before the target.
                assertTrue(pts.getFirst() <= 600_000_000L && pts.getFirst() > 500_000_000L, pts.getFirst() + " ns");
            }
        }
    }

    @Test
    @DisplayName("the provider claims AAC, AC-3 and E-AC-3, and nothing else")
    void claims() {
        try (var demuxer = Fixtures.demux("clip-h264-high.mp4")) {
            var video = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            var audio = demuxer.info().defaultTrack(MediaType.AUDIO).orElseThrow();
            assertTrue(provider.supports(demuxer.request(audio.index())));
            assertFalse(provider.supports(demuxer.request(video.index())));
        }
        for (var name : List.of("tone-eac3.mp4", "tones-ac3-5.1.mkv")) {
            try (var demuxer = Fixtures.demux(name)) {
                var audio = demuxer.info().defaultTrack(MediaType.AUDIO).orElseThrow();
                assertTrue(provider.supports(demuxer.request(audio.index())), name);
            }
        }
    }

    static int dominant(float[] samples, int from, int to) {
        return Tones.dominant(samples, RATE, from, to);
    }

    static double rms(float[] samples) {
        return Tones.rms(samples);
    }
}
