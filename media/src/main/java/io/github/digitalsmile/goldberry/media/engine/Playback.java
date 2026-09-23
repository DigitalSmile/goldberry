package io.github.digitalsmile.goldberry.media.engine;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.MediaInfo;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.AudioSink;
import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.Decoder;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.codec.Received;
import io.github.digitalsmile.goldberry.media.ffi.Decoders;
import io.github.digitalsmile.goldberry.media.ffi.Demuxer;
import io.github.digitalsmile.goldberry.media.ffi.Ffmpeg;
import io.github.digitalsmile.goldberry.media.ffi.Resampler;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MediaIOs;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.io.UnsupportedSchemeException;

/// One source, open and playing: the demux thread, the audio thread, and the
/// state they share (`docs/goldberry-media.md` §3).
///
/// Phase 2 plays the audio track. The video thread joins it in phase 3.
///
/// ## The two threads
///
/// **Demux** opens the source, opens the audio decoder and the sink, then reads
/// packets into the [PacketQueue] until the end. It owns seeking: a request
/// becomes `avformat_seek_file`, a new Serial and a queue flush. Requests that
/// arrive while one runs are coalesced, so only the latest runs.
///
/// **Audio** takes packets, decodes, converts to the sink's format, discards what
/// lies before a seek target (an accurate seek), and writes to the sink. It keeps
/// about [#SINK_TARGET_NANOS] queued in the sink, which is both the latency and
/// the cushion against a stall.
///
/// ## The clock
///
/// The audio clock is the master clock (§3). The audio thread records the
/// presentation time of the end of the last sample it wrote. The sink says how
/// many samples are still queued, and the difference is the time playing now
/// ([#positionNanos()]). Pausing the sink freezes it, and a seek resets it to the
/// target.
///
/// ## Stopping
///
/// [#close()] aborts the queue and the demuxer's I/O, which wakes every blocked
/// wait, joins both threads, then closes the sink. Every native object is freed
/// by the thread that used it.
public final class Playback implements AutoCloseable {

    /// How much decoded audio the audio thread keeps queued in the sink.
    static final long SINK_TARGET_NANOS = 200_000_000L;

    /// How much the sink must hold before [PlaybackState#BUFFERING] becomes
    /// [PlaybackState#PLAYING]: the low water mark, for a local file.
    static final long START_THRESHOLD_NANOS = 100_000_000L;

    /// How much media the packet queue holds.
    static final long QUEUE_NANOS = 2_000_000_000L;

    private static final Logger LOG = Logs.of(Playback.class);

    /// What the Engine tells its owner. Called on the Engine's threads.
    public interface Listener {
        /// The state, or what the source holds, or the decoder, changed.
        void changed(Playback playback);
    }

    private final Ffmpeg ffmpeg;
    private final Source source;
    private final @Nullable List<? extends MediaIOProvider> ioProviders;
    private final List<? extends DecoderProvider> decoderProviders;
    private final AudioSink sink;
    private final Listener listener;
    private final PacketQueue queue = new PacketQueue(QUEUE_NANOS);
    private final AtomicReference<@Nullable Long> pendingSeek = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition wake = lock.newCondition();
    private final Thread demuxThread;
    private final Thread audioThread;

    private volatile PlaybackState state = PlaybackState.OPENING;
    private volatile @Nullable MediaInfo info;
    private volatile @Nullable MediaError error;
    private volatile @Nullable String decoderName;
    private volatile @Nullable Demuxer demuxer;
    private volatile AudioFormat format = AudioFormat.DEFAULT;
    private volatile boolean paused;
    private volatile boolean stopping;
    /// The current Serial. The demux thread's alone; the audio thread learns it
    /// from the flush markers in the queue.
    private int serial;
    /// The sample index, at the sink's rate, just past the last sample written to
    /// the sink. The clock is kept in samples so that it adds up exactly; it
    /// becomes nanoseconds only when it is read.
    private volatile long writtenEndSample;
    /// The Serial of the latest seek, written by the demux thread when it flushes.
    /// The audio thread compares it with the Serial it is playing, to stop waiting
    /// on a full sink the moment its work has become stale.
    private volatile int latestSerial;
    /// The Serial the audio thread is playing. The audio thread's alone.
    private int audioSerial;
    /// What [#positionNanos()] reports while a seek settles: the target.
    private volatile long seekingToNanos = Frame.NO_PTS;

    /// Opens `source` and starts playing it, on threads of its own.
    ///
    /// @param ioProviders the protocols, or null for the ones `ServiceLoader`
    ///                    finds
    public Playback(
            Ffmpeg ffmpeg,
            Source source,
            @Nullable List<? extends MediaIOProvider> ioProviders,
            List<? extends DecoderProvider> decoderProviders,
            AudioSink sink,
            Listener listener) {
        this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
        this.source = Objects.requireNonNull(source, "source");
        this.ioProviders = ioProviders;
        this.decoderProviders = List.copyOf(decoderProviders);
        this.sink = Objects.requireNonNull(sink, "sink");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.demuxThread =
                Thread.ofPlatform().name("goldberry-media-demux").daemon().unstarted(this::demux);
        this.audioThread =
                Thread.ofPlatform().name("goldberry-media-audio").daemon().unstarted(this::audio);
    }

    /// Starts the threads. Separate from the constructor, so that no thread sees
    /// a half-built object.
    public void start() {
        demuxThread.start();
    }

    /// Where playback is.
    public PlaybackState state() {
        return state;
    }

    /// What the source holds, once it is open.
    public @Nullable MediaInfo info() {
        return info;
    }

    /// Why playback failed, in [PlaybackState#ERROR].
    public @Nullable MediaError error() {
        return error;
    }

    /// The decoder playing the audio: a provider's name, or `ffmpeg`.
    public @Nullable String decoderName() {
        return decoderName;
    }

    /// The format the sink plays.
    public AudioFormat format() {
        return format;
    }

    /// What is playing now, in nanoseconds of stream time: the audio clock.
    public long positionNanos() {
        var seeking = seekingToNanos;
        if (seeking != Frame.NO_PTS) {
            return seeking;
        }
        return format.nanos(Math.max(writtenEndSample - sink.queuedSamples(), 0));
    }

    /// Pauses. The sink keeps what it has queued, and the clock stops with it.
    public void pause() {
        paused = true;
        sink.pause();
        if (state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING) {
            setState(PlaybackState.PAUSED);
        }
    }

    /// Plays after [#pause()].
    public void play() {
        paused = false;
        sink.resume();
        signal();
        if (state == PlaybackState.PAUSED) {
            setState(PlaybackState.PLAYING);
        }
    }

    /// Seeks to `positionNanos`: the demux thread moves to the keyframe before
    /// it, and the audio thread discards up to it, so the first sample heard is at
    /// the target (an accurate seek, §3). Coalesced: while one seek runs, only the
    /// latest request waits.
    public void seek(long positionNanos) {
        var target = Math.max(positionNanos, 0);
        seekingToNanos = target;
        pendingSeek.set(target);
        queue.wakeProducer();
        signal();
    }

    /// Stops both threads, frees everything, and closes the sink. Idempotent.
    @Override
    public void close() {
        if (stopping) {
            return;
        }
        stopping = true;
        queue.abort();
        var open = demuxer;
        if (open != null) {
            open.abort();
        }
        signal();
        join(demuxThread);
        join(audioThread);
        sink.close();
    }

    // ----------------------------------------------------------------- demux

    private void demux() {
        MediaIO io = null;
        Demuxer opened = null;
        try {
            io = ioProviders == null ? MediaIOs.open(source) : MediaIOs.open(source, ioProviders);
            opened = Demuxer.open(ffmpeg, source, io);
            demuxer = opened;
            if (stopping) {
                return;
            }
            info = opened.info();
            var track = opened.info()
                    .defaultTrack(MediaType.AUDIO)
                    .orElseThrow(() -> new MediaException(new MediaError.InvalidData("no audio track to play")));
            var stream = track.index();
            opened.select(Set.of(stream));
            format = sink.open(AudioFormat.DEFAULT);
            audioInput = new AudioInput(opened, stream);
            audioThread.start();
            readPackets(opened);
        } catch (UnsupportedSchemeException e) {
            fail(new MediaError.UnsupportedScheme(e.scheme()), e);
        } catch (IOException e) {
            fail(new MediaError.Io(Objects.requireNonNullElse(e.getMessage(), e.toString())), e);
        } catch (MediaException e) {
            if (!stopping) {
                fail(e.error(), e);
            }
        } catch (RuntimeException e) {
            fail(new MediaError.InvalidData(e.toString()), e);
        } finally {
            queue.abort();
            join(audioThread);
            if (opened != null) {
                opened.close();
            }
            if (io != null) {
                try {
                    io.close();
                } catch (IOException e) {
                    LOG.debug("closing {} failed", source.uri(), e);
                }
            }
        }
    }

    private void readPackets(Demuxer opened) {
        var ended = false;
        while (!stopping) {
            var target = pendingSeek.getAndSet(null);
            if (target != null) {
                opened.seek(target);
                serial++;
                queue.flush(serial, target);
                latestSerial = serial;
                ended = false;
                if (state == PlaybackState.ENDED) {
                    setState(paused ? PlaybackState.PAUSED : PlaybackState.BUFFERING);
                }
                continue;
            }
            if (ended) {
                awaitWhile(() -> pendingSeek.get() == null);
                continue;
            }
            var packet = opened.read();
            if (packet == null) {
                queue.end(serial);
                ended = true;
            } else if (!queue.put(packet, serial) && stopping) {
                return;
            }
        }
    }

    // ----------------------------------------------------------------- audio

    /// What the demux thread hands the audio thread when it starts it.
    private record AudioInput(Demuxer demuxer, int stream) {}

    private volatile @Nullable AudioInput audioInput;

    private void audio() {
        var input = Objects.requireNonNull(audioInput);
        Decoder decoder;
        try {
            // Opened here, on the thread that will use it: the SPI promises a
            // decoder one thread (the track's decode thread), and a provider may
            // hold thread-confined state.
            var resolved = Decoders.open(ffmpeg, input.demuxer(), input.stream(), decoderProviders, 0);
            decoder = resolved.decoder();
            decoderName = resolved.provider();
        } catch (MediaException e) {
            if (!stopping) {
                fail(e.error(), e);
            }
            return;
        }
        setState(paused ? PlaybackState.PAUSED : PlaybackState.BUFFERING);
        var skip = 0;
        var discardBeforeNanos = Frame.NO_PTS;
        var nextPts = 0L;
        try (var resampler = new Resampler(ffmpeg, format.sampleRate(), format.channels());
                var arena = Arena.ofConfined()) {
            var output = new OutputBuffer(arena, format);
            while (!stopping) {
                if (paused) {
                    awaitWhile(() -> paused);
                    continue;
                }
                var item = queue.take(50, TimeUnit.MILLISECONDS);
                if (item == null) {
                    underrun();
                    continue;
                }
                switch (item) {
                    case PacketQueue.Item.Flush(var newSerial, var target) -> {
                        audioSerial = newSerial;
                        decoder.flush();
                        resampler.reset();
                        sink.clear();
                        discardBeforeNanos = target;
                        nextPts = target;
                        writtenEndSample = format.samples(target);
                    }
                    case PacketQueue.Item.Data(var packet, var packetSerial) -> {
                        try (packet) {
                            if (packetSerial != audioSerial) {
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
                                LOG.warn("decoder {} failed mid-stream; trying the next", decoderName, e);
                                decoder.close();
                                skip++;
                                var resolved =
                                        Decoders.open(ffmpeg, input.demuxer(), input.stream(), decoderProviders, skip);
                                decoder = resolved.decoder();
                                decoderName = resolved.provider();
                                listener.changed(this);
                            }
                        }
                    }
                    case PacketQueue.Item.End(var endSerial) -> {
                        if (endSerial != audioSerial) {
                            continue;
                        }
                        decoder.sendEnd();
                        nextPts = drainFrames(decoder, resampler, output, discardBeforeNanos, nextPts);
                        var tail = resampler.drain(output.segment(), output.capacity());
                        write(output, tail, nextPts);
                        playOut();
                        decoder.flush();
                    }
                }
            }
        } catch (MediaException e) {
            if (!stopping) {
                fail(e.error(), e);
            }
        } catch (RuntimeException e) {
            if (!stopping) {
                fail(new MediaError.InvalidData(e.toString()), e);
            }
        } finally {
            decoder.close();
        }
    }

    /// Receives every frame the decoder has, converts, trims and writes each.
    ///
    /// @return the stream time after the last sample written
    private long drainFrames(
            Decoder decoder, Resampler resampler, OutputBuffer output, long discardBeforeNanos, long nextPts) {
        var pts = nextPts;
        while (!stopping) {
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

    private void write(OutputBuffer output, int samples, long nextPtsNanos) {
        write(output.segment(), samples, format.samples(nextPtsNanos));
    }

    /// Writes `samples` samples that start at sample index `startSample`, after
    /// waiting for room. Dropped if a seek made them stale while waiting.
    private void write(MemorySegment data, int samples, long startSample) {
        if (samples <= 0) {
            return;
        }
        throttle();
        if (stopping || latestSerial != audioSerial) {
            return;
        }
        sink.write(data, samples);
        writtenEndSample = startSample + samples;
        seekingToNanos = Frame.NO_PTS;
        if (state == PlaybackState.BUFFERING
                && format.nanos(sink.queuedSamples()) >= Math.min(START_THRESHOLD_NANOS, SINK_TARGET_NANOS)) {
            setState(PlaybackState.PLAYING);
        }
    }

    /// Waits while the sink holds more than its target: the backpressure that
    /// keeps the Engine a fixed distance ahead of the speaker.
    private void throttle() {
        while (!stopping && !paused && latestSerial == audioSerial) {
            var excess = format.nanos(sink.queuedSamples()) - SINK_TARGET_NANOS;
            if (excess <= 0) {
                return;
            }
            sleep(Math.min(excess / 2, 20_000_000L));
        }
    }

    /// Plays out what the sink holds, then reports the end.
    private void playOut() {
        if (state == PlaybackState.BUFFERING) {
            setState(PlaybackState.PLAYING);
        }
        while (!stopping && sink.queuedSamples() > 0 && pendingSeek.get() == null) {
            sleep(10_000_000L);
        }
        if (!stopping && pendingSeek.get() == null) {
            seekingToNanos = Frame.NO_PTS;
            setState(PlaybackState.ENDED);
        }
    }

    /// The queue ran dry and the source has not ended: a stall.
    private void underrun() {
        if (state == PlaybackState.PLAYING && !queue.ended() && sink.queuedSamples() == 0) {
            setState(PlaybackState.BUFFERING);
        }
    }

    // ----------------------------------------------------------------- shared

    private void fail(MediaError failure, Throwable cause) {
        LOG.debug("playback of {} failed: {}", source.uri(), failure.message(), cause);
        error = failure;
        setState(PlaybackState.ERROR);
        stopping = true;
        queue.abort();
        signal();
    }

    private void setState(PlaybackState next) {
        if (state == next || (state == PlaybackState.ERROR && next != PlaybackState.ERROR)) {
            return;
        }
        state = next;
        listener.changed(this);
    }

    private void signal() {
        lock.lock();
        try {
            wake.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// Waits while `condition` holds, for at most 50 ms: long enough not to
    /// spin, and short enough that a missed signal costs a frame, not a hang.
    private void awaitWhile(BooleanSupplier condition) {
        lock.lock();
        try {
            var remaining = TimeUnit.MILLISECONDS.toNanos(50);
            while (!stopping && condition.getAsBoolean() && remaining > 0) {
                remaining = wake.awaitNanos(remaining);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopping = true;
        } finally {
            lock.unlock();
        }
    }

    private static void sleep(long nanos) {
        try {
            TimeUnit.NANOSECONDS.sleep(Math.max(nanos, 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread thread) {
        if (thread == Thread.currentThread() || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
