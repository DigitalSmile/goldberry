package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.FfmpegRequirement;
import io.github.digitalsmile.goldberry.media.MediaCapabilities;
import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.Wav;
import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.CodecId;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.DecoderRequest;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.Packet;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.codec.SampleFormat;
import io.github.digitalsmile.goldberry.media.io.MemoryIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// Demux, decode and resample against FFmpeg: phase 2's native half.
///
/// The input is a sine written by [Wav], so every decoded sample is known in
/// advance and compared exactly: PCM decoding is lossless, and s16→f32 in
/// swresample is a multiplication by 2⁻¹⁵.
@DisplayName("Demux, decode and resample, against FFmpeg")
class DecodeTest {

    private static final int RATE = 48_000;
    private static final double TONE = 440;
    private static final int AMPLITUDE = 12_000;

    private Ffmpeg ffmpeg;

    @BeforeEach
    void requireFfmpeg() {
        FfmpegRequirement.enforce();
        ffmpeg = FfmpegLibraries.get();
    }

    private Demuxer demuxer(byte[] wav) {
        return Demuxer.open(ffmpeg, Source.of(URI.create("mem:///tone.wav")), new MemoryIO(wav));
    }

    /// Every frame `decoder` produces from `demuxer`, handed to `consumer` while
    /// it is still valid.
    private static void decodeAll(Demuxer demuxer, Decoder decoder, Consumer<AudioFrame> consumer) {
        var ended = false;
        while (!ended) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> consumer.accept((AudioFrame) frame);
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

    @Test
    @DisplayName("decodes every sample of a PCM track exactly, with the built-in decoder")
    void decodesExactly() {
        var frames = RATE / 2;
        try (var demuxer = demuxer(Wav.sine(RATE, 2, frames, TONE, AMPLITUDE))) {
            demuxer.select(Set.of(0));
            var resolved = Decoders.open(ffmpeg, demuxer, 0, List.of(), 0);
            assertEquals(Decoders.BUILT_IN, resolved.provider());
            var seen = new int[1];
            try (var decoder = resolved.decoder()) {
                decodeAll(demuxer, decoder, frame -> {
                    assertEquals(SampleFormat.S16, frame.format());
                    assertEquals(RATE, frame.sampleRate());
                    assertEquals(2, frame.channels());
                    var data = frame.planes().getFirst();
                    for (var i = 0; i < frame.samples(); i++) {
                        var expected = Wav.sineSample(RATE, seen[0] + i, TONE, AMPLITUDE);
                        assertEquals(expected, data.getAtIndex(JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 2L * i));
                        assertEquals(
                                expected, data.getAtIndex(JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 2L * i + 1));
                    }
                    seen[0] += frame.samples();
                });
            }
            assertEquals(frames, seen[0]);
        }
    }

    @Test
    @DisplayName("converts s16 to f32 at the same rate exactly, and to another rate in proportion")
    void resamples() {
        try (var demuxer = demuxer(Wav.sine(8_000, 1, 8_000, TONE, AMPLITUDE));
                var same = new Resampler(ffmpeg, 8_000, 1);
                var up = new Resampler(ffmpeg, RATE, 2);
                var arena = Arena.ofConfined();
                var decoder = Decoders.open(ffmpeg, demuxer, 0, List.of(), 0).decoder()) {
            var sameOut = new ArrayList<Float>();
            var upTotal = new int[1];
            decodeAll(demuxer, decoder, frame -> {
                var capacity = same.capacityFor(frame);
                var out = arena.allocate(JAVA_FLOAT, capacity);
                var written = same.convert(frame, out, capacity);
                for (var i = 0; i < written; i++) {
                    sameOut.add(out.getAtIndex(JAVA_FLOAT, i));
                }
                var upCapacity = up.capacityFor(frame);
                upTotal[0] += up.convert(frame, arena.allocate(JAVA_FLOAT, (long) upCapacity * 2), upCapacity);
            });
            var tail = arena.allocate(JAVA_FLOAT, 4096L * 2);
            upTotal[0] += up.drain(tail, 4096);

            assertEquals(8_000, sameOut.size());
            for (var i = 0; i < sameOut.size(); i += 97) {
                assertEquals(Wav.sineSample(8_000, i, TONE, AMPLITUDE) / 32768f, sameOut.get(i), 0f);
            }
            // Six times the samples, less at most a filter's length at the edges.
            assertTrue(Math.abs(upTotal[0] - RATE) <= 64, "resampled to " + upTotal[0]);
        }
    }

    @Test
    @DisplayName("seeks to a keyframe at or before the target")
    void seeks() {
        try (var demuxer = demuxer(Wav.silence(RATE, 2, RATE * 2))) {
            demuxer.select(Set.of(0));
            demuxer.seek(1_000_000_000L);
            try (var packet = demuxer.read()) {
                assertTrue(packet != null);
                var pts = packet.ptsNanos();
                assertTrue(pts <= 1_000_000_000L && pts > 900_000_000L, "landed at " + pts);
            }
        }
    }

    @Test
    @DisplayName("reads nothing of a stream that is not selected")
    void discards() {
        try (var demuxer = demuxer(Wav.silence(8_000, 1, 8_000))) {
            demuxer.select(Set.of());
            try (var packet = demuxer.read()) {
                assertEquals(null, packet);
            }
        }
    }

    @Test
    @DisplayName("a codec no decoder plays is UnsupportedCodec, naming it (S7)")
    void unsupported() {
        try (var demuxer = demuxer(Wav.withFormatTag(Wav.silence(8_000, 1, 800), 6))) {
            assertEquals("pcm_alaw", demuxer.info().tracks().getFirst().codecName());
            var error = assertThrows(MediaException.class, () -> Decoders.open(ffmpeg, demuxer, 0, List.of(), 0));
            assertEquals(new MediaError.UnsupportedCodec(List.of("pcm_alaw")), error.error());
        }
    }

    /// The fake provider of phase 2's exit criterion: a sine generator that claims
    /// PCM and ignores the packets' contents.
    static final class SineProvider implements DecoderProvider {
        private final int priority;
        private final boolean failToOpen;

        SineProvider(int priority, boolean failToOpen) {
            this.priority = priority;
            this.failToOpen = failToOpen;
        }

        @Override
        public String name() {
            return failToOpen ? "broken" : "sine";
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supports(DecoderRequest request) {
            return request.codec() == CodecId.PCM_S16LE;
        }

        @Override
        public Decoder open(DecoderRequest request) {
            if (failToOpen) {
                throw new IllegalStateException("this provider never opens");
            }
            return new Decoder() {
                private final Arena arena = Arena.ofConfined();
                private final MemorySegment plane = arena.allocate(JAVA_FLOAT, 160);
                private int pending;
                private boolean ending;

                @Override
                public boolean send(Packet packet) {
                    pending++;
                    return true;
                }

                @Override
                public void sendEnd() {
                    ending = true;
                }

                @Override
                public Received receive() {
                    if (pending > 0) {
                        pending--;
                        return new Received.Decoded(
                                new AudioFrame(SampleFormat.F32, 8_000, 1, 160, List.of(plane), Frame.NO_PTS));
                    }
                    return ending ? Received.ENDED : Received.NEEDS_INPUT;
                }

                @Override
                public void flush() {
                    pending = 0;
                    ending = false;
                }

                @Override
                public void close() {
                    arena.close();
                }
            };
        }
    }

    @Test
    @DisplayName("a provider is chosen over the built-in decoder, the highest priority first")
    void providerWins() {
        try (var demuxer = demuxer(Wav.silence(8_000, 1, 800))) {
            var providers = List.of(new SineProvider(1, false), new SineProvider(5, true), new SineProvider(3, false));
            var resolved = Decoders.open(ffmpeg, demuxer, 0, providers, 0);
            try (var decoder = resolved.decoder()) {
                // The priority-5 provider fails to open, so the next one down is used.
                assertEquals("sine", resolved.provider());
                var formats = new ArrayList<SampleFormat>();
                decodeAll(demuxer, decoder, frame -> formats.add(frame.format()));
                assertFalse(formats.isEmpty());
                assertTrue(formats.stream().allMatch(SampleFormat.F32::equals));
            }
        }
    }

    @Test
    @DisplayName("walks the fallback ladder: skipping the providers that failed ends at the built-in decoder")
    void fallbackLadder() {
        try (var demuxer = demuxer(Wav.silence(8_000, 1, 800))) {
            var providers = List.of(new SineProvider(2, false), new SineProvider(1, false));
            assertEquals("sine", Decoders.open(ffmpeg, demuxer, 0, providers, 1).provider());
            var builtIn = Decoders.open(ffmpeg, demuxer, 0, providers, 2);
            builtIn.decoder().close();
            assertEquals(Decoders.BUILT_IN, builtIn.provider());
            assertThrows(MediaException.class, () -> Decoders.open(ffmpeg, demuxer, 0, providers, 3));
        }
    }

    @Test
    @DisplayName("describes a track for a provider, with its time base")
    void request() {
        try (var demuxer = demuxer(Wav.silence(RATE, 2, 480))) {
            var request = demuxer.request(0);
            assertEquals(CodecId.PCM_S16LE, request.codec());
            assertEquals(RATE, request.timeBase().den());
            assertInstanceOf(io.github.digitalsmile.goldberry.media.codec.TrackParams.Audio.class, request.params());
        }
    }

    @Test
    @DisplayName("capabilities come from the build: the free codecs are there, and the patented ones are not")
    void capabilities() {
        var capabilities = MediaCapabilities.current();
        for (var codec : List.of(
                CodecId.VP8,
                CodecId.VP9,
                CodecId.AV1,
                CodecId.OPUS,
                CodecId.VORBIS,
                CodecId.FLAC,
                CodecId.MP3,
                CodecId.PCM_S16LE)) {
            assertTrue(capabilities.canDecode(codec), codec + " in " + capabilities.decoders());
        }
        for (var codec : List.of(CodecId.H264, CodecId.HEVC, CodecId.AAC, CodecId.AC3)) {
            assertFalse(capabilities.canDecode(codec), codec + " should not be built");
        }
        for (var format : List.of("matroska", "webm", "mp4", "ogg", "wav", "flac", "mp3")) {
            assertTrue(capabilities.canDemux(format), format + " in " + capabilities.demuxers());
        }
        assertFalse(capabilities.canDemux("mpegts"));
    }
}
