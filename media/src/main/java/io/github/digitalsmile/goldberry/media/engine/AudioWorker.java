package io.github.digitalsmile.goldberry.media.engine;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.ffi.Decoders;
import io.github.digitalsmile.goldberry.media.ffi.Demuxer;
import io.github.digitalsmile.goldberry.media.ffi.Resampler;

/// The audio decode thread of one [Playback] (`docs/goldberry-media.md` §3,
/// "Audio decode").
///
/// Takes packets, decodes, converts to the sink's format in one resampling pass,
/// discards what lies before a seek target (an accurate seek), and writes to the
/// sink. It keeps about [Playback#sinkTargetNanos()] queued in the sink, which
/// is both the latency and the cushion against a stall.
///
/// **It owns the audio clock's inputs.** It records the sample index just past
/// the last sample it wrote, and the sink says how many of them are still
/// queued; [Playback] turns the two into what is playing now.
///
/// **Confined to its thread.** Everything here but the constructor runs on the
/// audio thread, so its fields are that thread's alone. The constructor runs on
/// the demux thread before `Thread.start`, which publishes it.
///
/// **A seek is honoured while paused.** A paused thread still takes the
/// [PacketQueue.Item.Flush] a seek queues, clears the sink and moves the clock
/// to the target, and then waits. Otherwise the sink would still hold the old
/// position's samples, and pressing play after a paused seek would play a fifth
/// of a second of the place just left.
final class AudioWorker {

    private static final Logger LOG = Logs.of(AudioWorker.class);

    private final Playback playback;
    private final Demuxer demuxer;
    private final int stream;
    private final PacketQueue queue;
    private final AudioFormat format;
    /// The Serial this thread is playing. Its own.
    private int serial;
    /// Set when another track takes over: the thread stops at its next check,
    /// writes nothing more, and reports nothing more.
    private volatile boolean retired;

    AudioWorker(Playback playback, Demuxer demuxer, int stream, PacketQueue queue, AudioFormat format) {
        this.playback = playback;
        this.demuxer = demuxer;
        this.stream = stream;
        this.queue = queue;
        this.format = format;
    }

    /// Stops this thread for good, from any thread: another track plays now. The
    /// caller then aborts the queue, which wakes a thread waiting on it.
    void retire() {
        retired = true;
    }

    /// Whether this thread still has work: the playback runs and no other track
    /// has taken over.
    private boolean running() {
        return !playback.stopping() && !retired;
    }

    /// The thread's whole life: open the decoder, then decode until the playback
    /// stops, or another track takes over.
    void play() {
        Decoder decoder;
        var skip = 0;
        try {
            // Opened here, on the thread that will use it: the SPI promises a
            // decoder one thread (the track's decode thread), and a provider may
            // hold thread-confined state.
            var resolved = Decoders.open(playback.ffmpeg(), demuxer, stream, playback.decoderProviders(), 0);
            decoder = resolved.decoder();
            playback.audioDecoder(resolved.provider());
        } catch (MediaException e) {
            playback.fail(e.error(), e);
            return;
        }
        var discardBeforeNanos = Frame.NO_PTS;
        var nextPts = 0L;
        try (var resampler = new Resampler(playback.ffmpeg(), format.sampleRate(), format.channels());
                var arena = Arena.ofConfined()) {
            var output = new OutputBuffer(arena, format);
            while (running()) {
                if (playback.paused()) {
                    var flush = queue.takeFlush();
                    if (flush == null) {
                        playback.awaitWhile(() -> playback.paused() && !queue.flushQueued());
                        continue;
                    }
                    flushTo(flush, decoder, resampler);
                    discardBeforeNanos = flush.targetNanos();
                    nextPts = flush.targetNanos();
                    continue;
                }
                var item = queue.take(50, TimeUnit.MILLISECONDS);
                if (item == null) {
                    underrun();
                    continue;
                }
                switch (item) {
                    case PacketQueue.Item.Flush flush -> {
                        flushTo(flush, decoder, resampler);
                        discardBeforeNanos = flush.targetNanos();
                        nextPts = flush.targetNanos();
                    }
                    case PacketQueue.Item.Data(var packet, var packetSerial) -> {
                        try (packet) {
                            if (packetSerial != serial) {
                                continue;
                            }
                            try {
                                while (!decoder.send(packet)) {
                                    nextPts = drainFrames(decoder, resampler, output, discardBeforeNanos, nextPts);
                                }
                                nextPts = drainFrames(decoder, resampler, output, discardBeforeNanos, nextPts);
                            } catch (MediaException e) {
                                throw e;
                            } catch (RuntimeException e) {
                                // The fallback ladder's mid-stream rung: this decoder
                                // is done; the next candidate takes over from the
                                // next packet.
                                LOG.warn("audio decoder failed mid-stream; trying the next", e);
                                decoder.close();
                                skip++;
                                var resolved = Decoders.open(
                                        playback.ffmpeg(), demuxer, stream, playback.decoderProviders(), skip);
                                decoder = resolved.decoder();
                                playback.audioDecoder(resolved.provider());
                            }
                        }
                    }
                    case PacketQueue.Item.End(var endSerial) -> {
                        if (endSerial != serial) {
                            continue;
                        }
                        decoder.sendEnd();
                        nextPts = drainFrames(decoder, resampler, output, discardBeforeNanos, nextPts);
                        var tail = resampler.drain(output.segment(), output.capacity());
                        write(output.segment(), tail, format.samples(nextPts));
                        playOut();
                        decoder.flush();
                    }
                }
            }
        } catch (MediaException e) {
            if (!retired) {
                playback.fail(e.error(), e);
            }
        } catch (RuntimeException e) {
            if (!retired) {
                playback.fail(new MediaError.InvalidData(e.toString()), e);
            }
        } finally {
            decoder.close();
        }
    }

    /// A seek: the decoder, the resampler and the sink start over at the target.
    private void flushTo(PacketQueue.Item.Flush flush, Decoder decoder, Resampler resampler) {
        serial = flush.serial();
        decoder.flush();
        resampler.reset();
        playback.sink().clear();
        playback.audioWritten(format.samples(flush.targetNanos()), false);
        playback.audioFlushed(serial);
    }

    /// Receives every frame the decoder has, converts, trims and writes each.
    ///
    /// @return the stream time after the last sample written
    private long drainFrames(
            Decoder decoder, Resampler resampler, OutputBuffer output, long discardBeforeNanos, long nextPts) {
        var pts = nextPts;
        while (running()) {
            var received = decoder.receive();
            if (!(received instanceof Received.Decoded(var frame))) {
                return pts;
            }
            if (!(frame instanceof AudioFrame audio)) {
                continue;
            }
            var framePts = audio.ptsNanos() == Frame.NO_PTS ? pts : audio.ptsNanos();
            output.ensure(resampler.capacityFor(audio));
            var written = resampler.convert(audio, output.segment(), output.capacity());
            var startSample = format.samples(framePts);
            var skipped = 0;
            if (discardBeforeNanos != Frame.NO_PTS && framePts < discardBeforeNanos) {
                skipped = (int) Math.min(written, format.samples(discardBeforeNanos) - startSample);
            }
            write(output.slice(skipped), written - skipped, startSample + skipped);
            pts = format.nanos(startSample + written);
        }
        return pts;
    }

    /// Writes `samples` samples that start at sample index `startSample`, after
    /// waiting for room. Dropped if a seek made them stale while waiting.
    private void write(MemorySegment data, int samples, long startSample) {
        if (samples <= 0) {
            return;
        }
        throttle();
        if (!running() || playback.latestSerial() != serial) {
            return;
        }
        playback.writeAudio(data, samples, startSample + samples);
        if (format.nanos(playback.sink().queuedSamples())
                >= Math.min(Playback.START_THRESHOLD_NANOS, playback.sinkTargetNanos())) {
            playback.audioReady();
        }
    }

    /// Waits while the sink holds more than its target: the backpressure that
    /// keeps the Engine a fixed distance ahead of the speaker.
    private void throttle() {
        while (running() && !playback.paused() && playback.latestSerial() == serial) {
            var excess = format.nanos(playback.sink().queuedSamples()) - playback.sinkTargetNanos();
            if (excess <= 0) {
                return;
            }
            Playback.sleep(Math.min(excess / 2, 20_000_000L));
        }
    }

    /// Plays out what the sink holds, then reports the end of the audio.
    private void playOut() {
        // A source shorter than the start threshold is ready when it has all been
        // written.
        playback.audioReady();
        while (running()
                && playback.sink().queuedSamples() > 0
                && playback.latestSerial() == serial
                && !playback.seekPending()) {
            Playback.sleep(10_000_000L);
        }
        // Then the last samples on their way to the ear (ADR-0474): done is when
        // they are heard, not when they left, or a track followed by another
        // would lose its last 200 ms over Bluetooth.
        if (running() && playback.latestSerial() == serial && !playback.seekPending()) {
            playback.audioTailStarted();
        }
        while (running()
                && playback.audioTailPending()
                && playback.latestSerial() == serial
                && !playback.seekPending()) {
            Playback.sleep(5_000_000L);
        }
        if (running() && playback.latestSerial() == serial && !playback.seekPending()) {
            playback.audioDone(serial);
        }
    }

    /// The queue ran dry and the source has not ended: a stall.
    private void underrun() {
        if (running() && !queue.ended() && playback.sink().queuedSamples() == 0) {
            playback.audioUnderrun();
        }
    }

    /// The audio thread's conversion buffer, grown to the largest frame seen.
    private static final class OutputBuffer {
        private final Arena arena;
        private final AudioFormat format;
        private MemorySegment segment;
        private int capacity;

        OutputBuffer(Arena arena, AudioFormat format) {
            this.arena = arena;
            this.format = format;
            this.capacity = 4096;
            this.segment = arena.allocate(JAVA_FLOAT, (long) capacity * format.channels());
        }

        void ensure(int samples) {
            if (samples > capacity) {
                capacity = Integer.highestOneBit(samples) << 1;
                segment = arena.allocate(JAVA_FLOAT, (long) capacity * format.channels());
            }
        }

        MemorySegment segment() {
            return segment;
        }

        int capacity() {
            return capacity;
        }

        MemorySegment slice(int samples) {
            return segment.asSlice((long) samples * format.bytesPerFrame());
        }
    }
}
