package dev.goldberry.media.robustness;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.SplittableRandom;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.PictureLimit;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.decoder.BuiltInDecoders;
import dev.goldberry.media.decoder.pcm.PcmDecoder;

/// Feeds a decoder packets and holds it to the SPI while it chokes on them.
///
/// What a decoder may do with a damaged packet: decode it into a frame, decode
/// nothing, or throw a [MediaException], after which the Engine drops it. What
/// it may not do: crash the JVM, throw anything else, hand out a frame whose
/// memory cannot be read to its last row, answer [Received.Ended] before the end
/// was sent, refuse a packet or hand out frames forever, or fail to close.
///
/// A decoder that threw is closed, as the Engine closes it, and a fresh one is
/// opened for the packets after the failure, so that one damaged packet early in
/// a clip does not leave the rest of it untested.
final class DecodeHarness {

    /// How many calls a decoder gets to take a packet, or to run out of frames,
    /// before it is taken to be stuck: a thousand times what any fixture needs.
    private static final int PATIENCE = 4096;

    /// FLAC's highest rate, which is the highest of every codec here.
    private static final int MAX_RATE = 1_048_575;

    /// The most channels a codec here decodes: Opus's and Vorbis's 255.
    private static final int MAX_CHANNELS = 255;

    /// What one run did.
    ///
    /// @param frames   frames handed out and read
    /// @param failures [MediaException]s thrown, at open or after
    /// @param checksum a sum over every byte read, which keeps the reads
    record Outcome(int frames, int failures, long checksum) {}

    private final DecoderProvider provider;
    private final String context;
    /// Whether the samples are the container's own bytes, whose rate and
    /// channel count are whatever its header says.
    private final boolean pcm;
    private int frames;
    private int failures;
    private long checksum;

    private DecodeHarness(DecoderProvider provider, String context, boolean pcm) {
        this.provider = provider;
        this.context = context;
        this.pcm = pcm;
    }

    /// The built-in provider that decodes `track`.
    static DecoderProvider providerFor(DemuxedTrack track) {
        try (var arena = Arena.ofConfined()) {
            var request = track.request(arena, track.extradata());
            return BuiltInDecoders.providers().stream()
                    .filter(candidate -> candidate.supports(request))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no built-in decoder for " + track.codecName()));
        }
    }

    /// Runs `samples` through a decoder of `provider` opened on `track` with
    /// `extradata`, flushing before a packet when `flushes` says so, and
    /// answers what came out.
    ///
    /// @param context what the run is, with its seed, for a failure's message
    static Outcome decode(
            DecoderProvider provider,
            DemuxedTrack track,
            List<DemuxedTrack.Sample> samples,
            byte[] extradata,
            @Nullable SplittableRandom flushes,
            String context) {
        var harness = new DecodeHarness(provider, context, PcmDecoder.decodes(track.codec()));
        try (var arena = Arena.ofConfined()) {
            harness.run(arena, track.request(arena, extradata), track, samples, flushes);
        } catch (MediaException e) {
            throw new AssertionError(context + ": a MediaException escaped the harness", e);
        } catch (RuntimeException e) {
            throw new AssertionError(context + ": " + e, e);
        }
        return new Outcome(harness.frames, harness.failures, harness.checksum);
    }

    private void run(
            Arena arena,
            DecoderRequest request,
            DemuxedTrack track,
            List<DemuxedTrack.Sample> samples,
            @Nullable SplittableRandom flushes) {
        var decoder = open(request);
        if (decoder == null) {
            return;
        }
        try {
            for (var i = 0; i < samples.size(); i++) {
                try {
                    if (flushes != null && flushes.nextInt(8) == 0) {
                        decoder.flush();
                    }
                    feed(decoder, samples.get(i), arena, track);
                } catch (MediaException e) {
                    failures++;
                    closeTwice(decoder);
                    decoder = open(request);
                    if (decoder == null) {
                        return;
                    }
                }
            }
            try {
                decoder.sendEnd();
                drainToEnd(decoder);
            } catch (MediaException e) {
                failures++;
            }
        } finally {
            closeTwice(decoder);
        }
    }

    private @Nullable Decoder open(DecoderRequest request) {
        try {
            return provider.open(request);
        } catch (MediaException e) {
            failures++;
            return null;
        }
    }

    private void feed(Decoder decoder, DemuxedTrack.Sample sample, Arena arena, DemuxedTrack track) {
        try (var packet = sample.packet(arena, track.timeBase())) {
            var attempts = 0;
            while (!decoder.send(packet)) {
                drainAvailable(decoder);
                if (++attempts > PATIENCE) {
                    throw new AssertionError(context + ": the decoder refuses a packet however much is received");
                }
            }
        }
        drainAvailable(decoder);
    }

    /// Receives until the decoder asks for input.
    private void drainAvailable(Decoder decoder) {
        for (var i = 0; i < PATIENCE; i++) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> check(frame);
                case Received.NeedsInput _ -> {
                    return;
                }
                case Received.Ended _ -> throw new AssertionError(context + ": ended before the end was sent");
            }
        }
        throw new AssertionError(context + ": frames without end from one packet");
    }

    /// Receives until the decoder says it has ended.
    private void drainToEnd(Decoder decoder) {
        for (var i = 0; i < PATIENCE; i++) {
            switch (decoder.receive()) {
                case Received.Decoded(var frame) -> check(frame);
                case Received.NeedsInput _ -> throw new AssertionError(context + ": asks for input after the end");
                case Received.Ended _ -> {
                    return;
                }
            }
        }
        throw new AssertionError(context + ": never ends after the end was sent");
    }

    /// Reads every byte of `frame` a consumer would read, after checking that
    /// its shape is one a codec can make.
    private void check(Frame frame) {
        switch (frame) {
            case VideoFrame video -> {
                assertTrue(
                        PictureLimit.allows(video.width(), video.height()),
                        () -> context + ": a " + video.width() + "×" + video.height() + " picture past the cap");
                var format = video.format();
                for (var plane = 0; plane < format.planes(); plane++) {
                    var rows = format.planeRows(plane, video.height());
                    var rowBytes = format.planeRowBytes(plane, video.width());
                    var stride = video.strides().get(plane);
                    var data = video.planes().get(plane);
                    var row = new byte[rowBytes];
                    for (var y = 0; y < rows; y++) {
                        MemorySegment.copy(data, JAVA_BYTE, (long) y * stride, row, 0, rowBytes);
                        checksum += row[0] + row[rowBytes - 1];
                    }
                }
            }
            case AudioFrame audio -> {
                assertTrue(
                        pcm || (audio.sampleRate() <= MAX_RATE && audio.channels() <= MAX_CHANNELS),
                        () -> context + ": " + audio.channels() + " channels at " + audio.sampleRate() + " Hz");
                var bytes = audio.format().planeSize(audio.channels(), audio.samples());
                for (var plane : audio.planes()) {
                    for (var at = 0L; at < bytes; at++) {
                        checksum += plane.get(JAVA_BYTE, at);
                    }
                }
            }
        }
        frames++;
    }

    private void closeTwice(Decoder decoder) {
        decoder.close();
        decoder.close();
    }
}
