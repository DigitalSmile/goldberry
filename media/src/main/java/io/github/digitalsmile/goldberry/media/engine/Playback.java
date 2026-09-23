package io.github.digitalsmile.goldberry.media.engine;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.MediaClock;
import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.MediaInfo;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.TimeRange;
import io.github.digitalsmile.goldberry.media.Track;
import io.github.digitalsmile.goldberry.media.VideoPicture;
import io.github.digitalsmile.goldberry.media.audio.AudioFormat;
import io.github.digitalsmile.goldberry.media.audio.AudioSink;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.codec.Frame;
import io.github.digitalsmile.goldberry.media.codec.MediaType;
import io.github.digitalsmile.goldberry.media.ffi.Decoders;
import io.github.digitalsmile.goldberry.media.ffi.Demuxer;
import io.github.digitalsmile.goldberry.media.ffi.Ffmpeg;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.MediaIOs;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.io.UnsupportedSchemeException;

/// One source, open and playing: the demux thread, a decode thread per track, and
/// the state they share (`docs/goldberry-media.md` §3).
///
/// ## The threads
///
/// **Demux** opens the source, chooses the tracks, checks that each can be
/// decoded, opens the sink, and reads packets into one [PacketQueue] per track.
/// It owns seeking: a request becomes `avformat_seek_file`, a new Serial and a
/// flush of every queue. Requests that arrive while one runs are coalesced, so
/// only the latest runs. It stops reading only when **every** queue is full,
/// so that one track's full queue cannot starve the other's decoder.
///
/// **Audio** ([AudioWorker]) decodes, converts and writes to the sink. **Video**
/// ([VideoWorker]) decodes, converts to BGRA and queues pictures in a
/// [FrameQueue], where they wait for the master clock.
///
/// ## The clock
///
/// The master clock is the audio clock while there is audio: the presentation
/// time just past the last sample written, less what the sink still holds. A
/// source with no audio runs on a free-running clock over the [MediaClock], and
/// a video whose audio ends first hands over to one at the audio's last position
/// ([MasterClock]).
///
/// ## Starting, and the water marks
///
/// The sink is opened paused. Playback starts, from [PlaybackState#BUFFERING] to
/// [PlaybackState#PLAYING], when every track is ready: the sink holds
/// [#START_THRESHOLD_NANOS] of audio, and the first picture is queued. So the
/// first picture and the first sample leave together.
///
/// It also waits for the **high water mark** (§4): until every track has been
/// demuxed that far past the clock ([#bufferedAheadNanos()]), or the source has
/// ended, or the queues are full and the demux thread could not read more if it
/// tried. For a local file that takes milliseconds; for a network source it is
/// the cushion a stall is survived on.
///
/// The **low water mark is empty.** A track stalls when its decoder runs out of
/// packets with the source not at its end, and only then does playback go back to
/// [PlaybackState#BUFFERING]: the sink is paused and the free-running clock held,
/// until the high water mark is reached again (S3). Pausing earlier, with media
/// still in hand, would trade one long silence for a later one.
///
/// ## Stopping
///
/// [#close()] aborts the queues and the demuxer's I/O, which wakes every blocked
/// wait, joins the threads, then closes the sink. Every native object is freed by
/// the thread that used it.
public final class Playback implements AutoCloseable {

    /// How much decoded audio the audio thread keeps queued in the sink, at a
    /// rate of 1. Faster, it keeps more stream time, so the device waits no
    /// shorter a wall-clock time for the next write ([#sinkTargetNanos()]).
    static final long SINK_TARGET_NANOS = 200_000_000L;

    /// The slowest and fastest a player plays (§3, "Rate"): a quarter and four
    /// times the speed.
    public static final float MIN_RATE = 0.25f;

    public static final float MAX_RATE = 4f;

    /// How much the sink must hold before audio counts as ready: the low water
    /// mark, for a local file.
    static final long START_THRESHOLD_NANOS = 100_000_000L;

    /// How much media each packet queue holds.
    static final long QUEUE_NANOS = 2_000_000_000L;

    /// How far ahead every track must be demuxed before playback starts, or
    /// resumes after a stall, unless the player says otherwise. Above
    /// [#QUEUE_NANOS] it is met when the queues fill.
    public static final long HIGH_WATER_NANOS = 1_000_000_000L;

    /// How many bytes of packets the audio queue holds, for a container that does
    /// not say how long its packets are. Two seconds of 24-bit 96 kHz PCM.
    static final long AUDIO_QUEUE_BYTES = 2L << 20;

    /// The same for video: two seconds of 4K VP9 at a generous bit rate.
    static final long VIDEO_QUEUE_BYTES = 16L << 20;

    private static final Logger LOG = Logs.of(Playback.class);

    /// What the Engine tells its owner. Called on the Engine's threads.
    public interface Listener {
        /// The state, or what the source holds, or a decoder, or the picture after
        /// a seek, changed.
        void changed(Playback playback);
    }

    /// A seek waiting for the demux thread.
    private record SeekRequest(long targetNanos, boolean accurate) {}

    private final Ffmpeg ffmpeg;
    private final Source source;
    private final @Nullable List<? extends MediaIOProvider> ioProviders;
    private final List<? extends DecoderProvider> decoderProviders;
    private final AudioSink sink;
    private final Listener listener;
    private final MasterClock clock;
    private final long highWaterNanos;
    private final AtomicReference<@Nullable SeekRequest> pendingSeek = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition wake = lock.newCondition();
    /// Guards the state and the readiness flags, which three threads change.
    private final Object gate = new Object();
    private final Thread demuxThread;
    private final List<Thread> workers = new ArrayList<>(2);

    private volatile PlaybackState state = PlaybackState.OPENING;
    private volatile @Nullable MediaInfo info;
    private volatile @Nullable MediaError error;
    private volatile @Nullable String audioDecoderName;
    private volatile @Nullable String videoDecoderName;
    private volatile @Nullable Demuxer demuxer;
    /// The source's bytes, once opened: what the buffered ranges and the title
    /// come from.
    private volatile @Nullable MediaIO io;
    private volatile @Nullable String nowPlaying;
    private volatile float rate = 1f;
    /// How long a picture lasts, as the video thread last measured it; 0 before
    /// it has.
    private volatile long frameNanos;
    private volatile @Nullable PacketQueue audioQueue;
    private volatile @Nullable PacketQueue videoQueue;
    private volatile @Nullable FrameQueue frames;
    private volatile AudioFormat format = AudioFormat.DEFAULT;
    private volatile boolean paused;
    private volatile boolean stopping;
    /// Set by [#close()] alone. Not [#stopping]: a failure stops the threads too,
    /// and the playback still has to be closed after one.
    private volatile boolean closed;
    /// The current Serial. The demux thread's alone; the decode threads learn it
    /// from the flush markers in their queues.
    private int serial;
    /// The Serial of the latest seek, written by the demux thread before it flushes.
    /// A decode thread compares it with the Serial it is playing, to stop waiting
    /// the moment its work has become stale.
    private volatile int latestSerial;
    /// The Serial whose flush the audio thread has honoured: the sink holds
    /// nothing older. Written by the audio thread.
    private volatile int audioSerial;
    /// Whether the demux thread has taken a seek request and not yet published
    /// its Serial. Set before the request is taken, so a request is always
    /// either pending or being carried out, never neither.
    private volatile boolean seeking;
    /// The sample index, at the sink's rate, just past the last sample written to
    /// the sink. The audio clock is kept in samples so that it adds up exactly; it
    /// becomes nanoseconds only when it is read.
    private volatile long writtenEndSample;
    /// What the position reads while a seek settles: the target.
    private volatile long seekingToNanos = Frame.NO_PTS;

    // Guarded by `gate`.
    private boolean hasAudio;
    private boolean hasVideo;
    private boolean audioReady;
    private boolean videoReady;
    private boolean audioDone;
    private boolean videoDone;

    /// Opens `source` and starts playing it, on threads of its own.
    ///
    /// @param ioProviders    the protocols, or null for the ones `ServiceLoader`
    ///                       finds
    /// @param time           what a source with no audio is timed against
    /// @param highWaterNanos how far ahead to demux before playing, and before
    ///                       playing on after a stall
    public Playback(
            Ffmpeg ffmpeg,
            Source source,
            @Nullable List<? extends MediaIOProvider> ioProviders,
            List<? extends DecoderProvider> decoderProviders,
            AudioSink sink,
            MediaClock time,
            long highWaterNanos,
            Listener listener) {
        if (highWaterNanos < 0) {
            throw new IllegalArgumentException("highWaterNanos " + highWaterNanos);
        }
        this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
        this.source = Objects.requireNonNull(source, "source");
        this.ioProviders = ioProviders;
        this.decoderProviders = List.copyOf(decoderProviders);
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = new MasterClock(time);
        this.highWaterNanos = highWaterNanos;
        this.listener = Objects.requireNonNull(listener, "listener");
        this.demuxThread =
                Thread.ofPlatform().name("goldberry-media-demux").daemon().unstarted(this::demux);
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
        return audioDecoderName;
    }

    /// The decoder playing the video: a provider's name, or `ffmpeg`.
    public @Nullable String videoDecoderName() {
        return videoDecoderName;
    }

    /// The format the sink plays.
    public AudioFormat format() {
        return format;
    }

    /// What is playing now, in nanoseconds of stream time: the master clock, or
    /// the target while a seek settles.
    public long positionNanos() {
        return presentationNanos();
    }

    /// How much is demuxed past the position, in nanoseconds: the least any
    /// playing track holds, or, once every track has reached the end of the
    /// source, what is left of it. Zero before the first packet.
    public long bufferedAheadNanos() {
        var now = presentationNanos();
        var least = Long.MAX_VALUE;
        var most = 0L;
        var queues = 0;
        var ended = 0;
        for (var queue : queues()) {
            queues++;
            var end = queue.endNanos();
            var ahead = end == Frame.NO_PTS ? 0 : Math.max(end - now, 0);
            most = Math.max(most, ahead);
            if (queue.ended()) {
                ended++;
            } else {
                least = Math.min(least, ahead);
            }
        }
        if (queues == 0) {
            return 0;
        }
        return ended == queues ? most : least;
    }

    /// What the source's bytes have fetched, as stretches of the presentation:
    /// each byte range mapped in proportion to the source's length and duration.
    /// Empty for a source that reports no ranges, or whose length or duration is
    /// unknown.
    public List<TimeRange> bufferedRanges() {
        var bytes = io;
        var described = info;
        if (bytes == null || described == null || described.duration().isEmpty()) {
            return List.of();
        }
        var size = bytes.size();
        if (size.isEmpty() || size.getAsLong() <= 0) {
            return List.of();
        }
        var ranges = bytes.buffered();
        if (ranges.isEmpty()) {
            return List.of();
        }
        var total = (double) size.getAsLong();
        var duration = described.duration().get().toNanos();
        var mapped = new ArrayList<TimeRange>(ranges.size());
        for (var range : ranges) {
            mapped.add(new TimeRange(
                    Duration.ofNanos(Math.round(duration * Math.min(range.start() / total, 1))),
                    Duration.ofNanos(Math.round(duration * Math.min(range.end() / total, 1)))));
        }
        return List.copyOf(mapped);
    }

    /// How fast playback runs: 1 is as recorded.
    public float rate() {
        return rate;
    }

    /// Plays `requested` times as fast, pitch and all, from now on: the sink
    /// resamples, and the free-running clock counts faster (§3, "Rate").
    ///
    /// @return the rate playback runs at now: `requested`, or the old one when the
    ///         sink cannot play at it
    /// @throws IllegalArgumentException outside [#MIN_RATE] and [#MAX_RATE]
    public float setRate(float requested) {
        if (!(requested >= MIN_RATE && requested <= MAX_RATE)) {
            throw new IllegalArgumentException("rate " + requested + " is not within " + MIN_RATE + " and " + MAX_RATE);
        }
        boolean changed;
        synchronized (gate) {
            changed = requested != rate && sink.setRate(requested);
            if (changed) {
                clock.setRate(requested);
                rate = requested;
            }
        }
        notifyIf(changed);
        return rate;
    }

    /// Moves `count` pictures on, or back for a negative count, and pauses there:
    /// an accurate seek to the shown picture's time plus `count` picture lengths,
    /// which lands on the picture that starts there (§6, `,` and `.`).
    ///
    /// @return false when there is no picture to step from: no video, or none
    ///         shown yet
    public boolean step(int count) {
        var queue = frames;
        if (queue == null) {
            return false;
        }
        var shown = queue.shownPtsNanos();
        if (shown == Long.MIN_VALUE) {
            return false;
        }
        var length = frameNanos > 0 ? frameNanos : VideoWorker.DEFAULT_FRAME_NANOS;
        var target = Math.max(shown + count * length, 0);
        var described = info;
        if (described != null && described.duration().isPresent()) {
            // Past the end is no picture at all: the last one is as far as it goes.
            target = Math.min(target, Math.max(described.duration().get().toNanos() - length, 0));
        }
        pause();
        seek(target, true);
        return true;
    }

    /// What the stream says is playing now, such as an ICY `StreamTitle`.
    public @Nullable String nowPlaying() {
        return nowPlaying;
    }

    /// The picture to show now, handed out to a view that will draw it (see
    /// [VideoPicture] for how long it stays valid), or empty before the first
    /// picture, and for a source with no video.
    public Optional<VideoPicture> currentPicture() {
        var queue = frames;
        return queue == null ? Optional.empty() : Optional.ofNullable(queue.present(presentationNanos(), true));
    }

    /// How long until a picture that is not yet due falls due, in nanoseconds of
    /// stream time, or empty when none is waiting (nothing decoded yet, the end,
    /// or no video). A picture already due is not counted: the next
    /// [#currentPicture()] shows it.
    public java.util.OptionalLong nanosUntilNextPicture() {
        var queue = frames;
        if (queue == null) {
            return java.util.OptionalLong.empty();
        }
        var now = presentationNanos();
        var next = queue.nextPtsAfter(now);
        return next == Long.MIN_VALUE ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(next - now);
    }

    /// Pauses. The sink keeps what it has queued, and the clock stops with it.
    public void pause() {
        boolean changed;
        synchronized (gate) {
            paused = true;
            sink.pause();
            clock.hold();
            changed = (state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING)
                    && setStateLocked(PlaybackState.PAUSED);
        }
        notifyIf(changed);
    }

    /// Plays after [#pause()].
    public void play() {
        boolean changed = false;
        synchronized (gate) {
            paused = false;
            if (state == PlaybackState.PAUSED) {
                if (readyLocked()) {
                    startOutputLocked();
                    changed = setStateLocked(PlaybackState.PLAYING);
                } else {
                    changed = setStateLocked(PlaybackState.BUFFERING);
                }
            } else if (state == PlaybackState.PLAYING || state == PlaybackState.ENDED) {
                startOutputLocked();
            }
        }
        signal();
        notifyIf(changed);
    }

    /// Seeks to `positionNanos`: the demux thread moves to the keyframe before
    /// it; with `accurate`, the decode threads discard up to it, so the first
    /// sample heard and the first picture shown are the target's (§3). Coalesced:
    /// while one seek runs, only the latest request waits.
    public void seek(long positionNanos, boolean accurate) {
        var target = Math.max(positionNanos, 0);
        seekingToNanos = target;
        pendingSeek.set(new SeekRequest(target, accurate));
        signal();
    }

    /// Stops every thread, frees everything, and closes the sink. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        stopping = true;
        abortQueues();
        var open = demuxer;
        if (open != null) {
            open.abort();
        } else {
            // Still opening: a network source may be waiting on a server. Closing
            // its bytes ends a read FFmpeg is blocked in, and the interrupt ends a
            // wait for the first response.
            closeQuietly(io);
            demuxThread.interrupt();
        }
        signal();
        join(demuxThread);
        sink.close();
    }

    // ----------------------------------------------------------------- demux

    private void demux() {
        MediaIO bytes = null;
        Demuxer opened = null;
        try {
            bytes = ioProviders == null ? MediaIOs.open(source) : MediaIOs.open(source, ioProviders);
            io = bytes;
            if (stopping) {
                return;
            }
            opened = Demuxer.open(ffmpeg, source, bytes);
            demuxer = opened;
            if (stopping) {
                return;
            }
            var described = opened.info();
            info = described;
            var audio = described.defaultTrack(MediaType.AUDIO);
            var video = described.defaultTrack(MediaType.VIDEO);
            if (audio.isEmpty() && video.isEmpty()) {
                throw new MediaException(new MediaError.InvalidData("nothing to play: no audio or video track"));
            }
            requireDecoders(opened, audio, video);
            var selected = new HashSet<Integer>();
            audio.ifPresent(track -> selected.add(track.index()));
            video.ifPresent(track -> selected.add(track.index()));
            opened.select(selected);
            startWorkers(opened, audio, video);
            readPackets(
                    opened,
                    audio.map(Track::index).orElse(-1),
                    video.map(Track::index).orElse(-1));
        } catch (UnsupportedSchemeException e) {
            fail(new MediaError.UnsupportedScheme(e.scheme()), e);
        } catch (IOException e) {
            fail(new MediaError.Io(Objects.requireNonNullElse(e.getMessage(), e.toString())), e);
        } catch (MediaException e) {
            fail(e.error(), e);
        } catch (RuntimeException e) {
            fail(new MediaError.InvalidData(e.toString()), e);
        } finally {
            abortQueues();
            workers.forEach(Playback::join);
            if (opened != null) {
                opened.close();
            }
            closeQuietly(bytes);
        }
    }

    /// Every track about to be played has a decoder, or the source fails naming
    /// every codec that has none (§7, S7).
    private void requireDecoders(Demuxer opened, Optional<Track> audio, Optional<Track> video) {
        var unsupported = new ArrayList<String>(2);
        for (var track : List.of(video, audio)) {
            track.filter(chosen -> !Decoders.supports(ffmpeg, opened, chosen.index(), decoderProviders))
                    .ifPresent(chosen -> unsupported.add(chosen.codecName()));
        }
        if (!unsupported.isEmpty()) {
            throw new MediaException(new MediaError.UnsupportedCodec(unsupported));
        }
    }

    private void startWorkers(Demuxer opened, Optional<Track> audio, Optional<Track> video) {
        synchronized (gate) {
            hasAudio = audio.isPresent();
            hasVideo = video.isPresent();
        }
        if (audio.isPresent()) {
            var queue = new PacketQueue(QUEUE_NANOS, AUDIO_QUEUE_BYTES);
            audioQueue = queue;
            format = sink.open(AudioFormat.DEFAULT);
            // Opened paused: the first sample waits for the first picture.
            sink.pause();
            clock.followAudio(this::audioClockNanos);
            workers.add(Thread.ofPlatform()
                    .name("goldberry-media-audio")
                    .daemon()
                    .unstarted(new AudioWorker(this, opened, audio.get().index(), queue, format)::play));
        }
        if (video.isPresent()) {
            var queue = new PacketQueue(QUEUE_NANOS, VIDEO_QUEUE_BYTES);
            var pictures = new FrameQueue();
            videoQueue = queue;
            frames = pictures;
            workers.add(Thread.ofPlatform()
                    .name("goldberry-media-video")
                    .daemon()
                    .unstarted(new VideoWorker(this, opened, video.get().index(), queue, pictures)::play));
        }
        boolean changed;
        synchronized (gate) {
            changed = setStateLocked(paused ? PlaybackState.PAUSED : PlaybackState.BUFFERING);
        }
        notifyIf(changed);
        workers.forEach(Thread::start);
    }

    private void readPackets(Demuxer opened, int audioStream, int videoStream) {
        var ended = false;
        while (!stopping) {
            if (pendingSeek.get() != null) {
                seeking = true;
                var request = pendingSeek.getAndSet(null);
                if (request != null) {
                    seekTo(opened, request);
                }
                seeking = false;
                ended = false;
                continue;
            }
            if (ended) {
                awaitWhile(() -> pendingSeek.get() == null);
                continue;
            }
            if (mustWait()) {
                // Full: as buffered as it will get.
                startIfBuffered();
                awaitWhile(() -> pendingSeek.get() == null && mustWait(), 10);
                continue;
            }
            var packet = opened.read();
            if (packet == null) {
                forEachQueue(queue -> queue.end(serial));
                ended = true;
                startIfBuffered();
                continue;
            }
            var target = packet.streamIndex() == audioStream
                    ? audioQueue
                    : packet.streamIndex() == videoStream ? videoQueue : null;
            if (target == null) {
                packet.close();
            } else if (!target.put(packet, serial) && stopping) {
                return;
            }
            startIfBuffered();
            followTitle();
        }
    }

    /// Starts, or plays on after a stall, if buffering was all that held it.
    private void startIfBuffered() {
        if (state != PlaybackState.BUFFERING) {
            return;
        }
        boolean changed;
        synchronized (gate) {
            changed = maybeStartLocked();
        }
        notifyIf(changed);
    }

    /// Publishes a new title when the stream announces one.
    private void followTitle() {
        var bytes = io;
        if (bytes == null) {
            return;
        }
        var title = bytes.nowPlaying().orElse(null);
        if (!Objects.equals(title, nowPlaying)) {
            nowPlaying = title;
            listener.changed(this);
        }
    }

    /// Whether every track has been demuxed to the high water mark, or cannot be
    /// demuxed further: the source has ended, or the queues are full.
    private boolean bufferedLocked() {
        var queues = queues();
        if (queues.isEmpty()) {
            return true;
        }
        if (queues.stream().allMatch(PacketQueue::ended) || mustWait()) {
            return true;
        }
        return bufferedAheadNanos() >= highWaterNanos;
    }

    private void seekTo(Demuxer opened, SeekRequest request) {
        opened.seek(request.targetNanos());
        serial++;
        // Before the flushes, so a decode thread that takes its flush at once
        // already finds the new Serial the latest.
        latestSerial = serial;
        var pictures = frames;
        if (pictures != null) {
            pictures.flush(serial);
        }
        forEachQueue(queue -> queue.flush(serial, request.targetNanos(), request.accurate()));
        boolean changed = false;
        synchronized (gate) {
            audioDone = false;
            videoDone = false;
            videoReady = false;
            if (hasAudio) {
                clock.followAudio(this::audioClockNanos);
            } else {
                clock.set(request.targetNanos());
            }
            if (state == PlaybackState.ENDED) {
                audioReady = false;
                changed = setStateLocked(paused ? PlaybackState.PAUSED : PlaybackState.BUFFERING);
            }
        }
        notifyIf(changed);
        // The decode threads may be waiting while paused; the flush is theirs to
        // take now.
        signal();
    }

    /// Whether the demux thread should stop reading: every queue is full, or one
    /// has run far past its bound.
    private boolean mustWait() {
        var queues = queues();
        if (queues.isEmpty()) {
            return false;
        }
        var allFull = true;
        for (var queue : queues) {
            if (queue.overflowing()) {
                return true;
            }
            allFull &= queue.full();
        }
        return allFull;
    }

    /// The packet queues there are: none before the tracks are chosen.
    private List<PacketQueue> queues() {
        var queues = new ArrayList<PacketQueue>(2);
        forEachQueue(queues::add);
        return queues;
    }

    private void forEachQueue(java.util.function.Consumer<PacketQueue> action) {
        var audio = audioQueue;
        if (audio != null) {
            action.accept(audio);
        }
        var video = videoQueue;
        if (video != null) {
            action.accept(video);
        }
    }

    private void abortQueues() {
        forEachQueue(PacketQueue::abort);
        var pictures = frames;
        if (pictures != null) {
            pictures.abort();
        }
    }

    // ------------------------------------------------- what the workers report

    Ffmpeg ffmpeg() {
        return ffmpeg;
    }

    List<? extends DecoderProvider> decoderProviders() {
        return decoderProviders;
    }

    AudioSink sink() {
        return sink;
    }

    boolean stopping() {
        return stopping;
    }

    boolean paused() {
        return paused;
    }

    int latestSerial() {
        return latestSerial;
    }

    boolean seekPending() {
        return pendingSeek.get() != null;
    }

    /// How much the audio thread keeps queued in the sink: [#SINK_TARGET_NANOS]
    /// of stream time, more when playing faster.
    long sinkTargetNanos() {
        return Math.round(SINK_TARGET_NANOS * Math.max(1f, rate));
    }

    void videoFrameNanos(long nanos) {
        frameNanos = nanos;
    }

    boolean clockRunning() {
        return clock.running();
    }

    /// What pictures are presented against: the master clock, or the target
    /// while a seek settles.
    long presentationNanos() {
        var seeking = seekingToNanos;
        return seeking != Frame.NO_PTS ? seeking : clock.nanos();
    }

    /// The audio clock: what the sink is playing now.
    private long audioClockNanos() {
        return format.nanos(Math.max(writtenEndSample - sink.queuedSamples(), 0));
    }

    /// The audio thread wrote up to `endSample`, or (not `played`) moved there by
    /// a seek. A write is the audio clock taking over from a seek's target.
    void audioWritten(long endSample, boolean played) {
        writtenEndSample = endSample;
        if (played) {
            seekingToNanos = Frame.NO_PTS;
        }
    }

    void audioDecoder(String name) {
        audioDecoderName = name;
        listener.changed(this);
    }

    void videoDecoder(String name) {
        videoDecoderName = name;
        listener.changed(this);
    }

    /// The audio thread has honoured the flush of `forSerial`: the sink holds
    /// nothing from before it. If playback was started meanwhile, the sink starts
    /// now.
    void audioFlushed(int forSerial) {
        audioSerial = forSerial;
        synchronized (gate) {
            if (sinkCurrent() && state == PlaybackState.PLAYING && !paused) {
                sink.resume();
            }
        }
    }

    /// Whether the sink holds nothing from before the latest seek: none is
    /// waiting or under way, and the audio thread has honoured the last one.
    private boolean sinkCurrent() {
        return pendingSeek.get() == null && !seeking && audioSerial == latestSerial;
    }

    /// The sink holds enough to start.
    void audioReady() {
        boolean changed;
        synchronized (gate) {
            audioReady = true;
            changed = maybeStartLocked();
        }
        notifyIf(changed);
    }

    /// The audio queue ran dry mid-stream and the sink with it: a stall. The sink
    /// is paused, so what arrives next waits for the high water mark rather than
    /// playing a packet at a time.
    void audioUnderrun() {
        boolean changed = false;
        synchronized (gate) {
            audioReady = false;
            if (state == PlaybackState.PLAYING) {
                sink.pause();
                changed = setStateLocked(PlaybackState.BUFFERING);
            }
        }
        notifyIf(changed);
    }

    /// The video queue ran dry mid-stream with no picture left to show: a stall,
    /// when pictures run on the free-running clock. With audio playing, the
    /// audio clock decides, and a picture short is only a picture late.
    void videoUnderrun() {
        boolean changed = false;
        synchronized (gate) {
            if (hasAudio && !audioDone) {
                return;
            }
            if (state == PlaybackState.PLAYING) {
                clock.hold();
                changed = setStateLocked(PlaybackState.BUFFERING);
            }
        }
        notifyIf(changed);
    }

    /// The audio of `forSerial` has been played to its last sample.
    void audioDone(int forSerial) {
        boolean changed;
        synchronized (gate) {
            if (forSerial != latestSerial) {
                return;
            }
            audioDone = true;
            audioReady = true;
            if (hasVideo && !videoDone) {
                // The picture outlasts the sound: time runs on without it.
                clock.freeRunFrom(audioClockNanos(), !paused);
            }
            var started = maybeStartLocked();
            var ended = maybeEndLocked();
            changed = started || ended;
        }
        notifyIf(changed);
    }

    /// The first picture since `forSerial`'s seek (or the start) is queued.
    void videoReady(int forSerial) {
        synchronized (gate) {
            if (forSerial != latestSerial) {
                return;
            }
            videoReady = true;
            if (!hasAudio || audioDone) {
                // A free-running clock waits for the first picture of a seek; the
                // audio clock waits for the first sample, which it does itself.
                seekingToNanos = Frame.NO_PTS;
                if (!paused && state == PlaybackState.PLAYING) {
                    clock.run();
                }
            }
            maybeStartLocked();
        }
        // Pushed whether or not the state moved: a paused view shows the picture
        // a seek landed on only if it is told there is one.
        listener.changed(this);
    }

    /// The last picture of `forSerial` has been shown for as long as a picture
    /// lasts.
    void videoDone(int forSerial) {
        boolean changed;
        synchronized (gate) {
            if (forSerial != latestSerial) {
                return;
            }
            videoDone = true;
            videoReady = true;
            var started = maybeStartLocked();
            var ended = maybeEndLocked();
            changed = started || ended;
        }
        notifyIf(changed);
    }

    /// Fails playback with `failure`, unless it is stopping anyway.
    void fail(MediaError failure, Throwable cause) {
        if (stopping) {
            return;
        }
        LOG.debug("playback of {} failed: {}", source.uri(), failure.message(), cause);
        error = failure;
        boolean changed;
        synchronized (gate) {
            changed = setStateLocked(PlaybackState.ERROR);
        }
        stopping = true;
        abortQueues();
        signal();
        notifyIf(changed);
    }

    // ------------------------------------------------------------------ state

    private boolean readyLocked() {
        return (!hasAudio || audioReady) && (!hasVideo || videoReady) && bufferedLocked();
    }

    /// BUFFERING becomes PLAYING when every track is ready and buffered.
    private boolean maybeStartLocked() {
        if (state != PlaybackState.BUFFERING || !readyLocked()) {
            return false;
        }
        if (!paused) {
            startOutputLocked();
        }
        return setStateLocked(PlaybackState.PLAYING);
    }

    /// PLAYING becomes ENDED when every track has played out.
    private boolean maybeEndLocked() {
        if ((hasAudio && !audioDone) || (hasVideo && !videoDone)) {
            return false;
        }
        if (state == PlaybackState.ERROR) {
            return false;
        }
        clock.hold();
        seekingToNanos = Frame.NO_PTS;
        return setStateLocked(PlaybackState.ENDED);
    }

    /// Starts the sink and the free-running clock. The sink only once the audio
    /// thread has honoured the latest seek: until then it may hold the old
    /// position's samples, and a play right after a paused seek would let the
    /// device pull them. [#audioFlushed] starts it then.
    private void startOutputLocked() {
        if (!hasAudio || sinkCurrent()) {
            sink.resume();
        }
        if (!clock.followingAudio()) {
            clock.run();
        }
    }

    /// Moves to `next`, and says whether that is a change. ERROR is final.
    private boolean setStateLocked(PlaybackState next) {
        if (state == next || (state == PlaybackState.ERROR && next != PlaybackState.ERROR)) {
            return false;
        }
        state = next;
        return true;
    }

    private void notifyIf(boolean changed) {
        if (changed) {
            listener.changed(this);
        }
    }

    // ----------------------------------------------------------------- shared

    void signal() {
        lock.lock();
        try {
            wake.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /// Waits while `condition` holds, for at most 50 ms: long enough not to
    /// spin, and short enough that a missed signal costs a frame, not a hang.
    void awaitWhile(BooleanSupplier condition) {
        awaitWhile(condition, 50);
    }

    private void awaitWhile(BooleanSupplier condition, long millis) {
        lock.lock();
        try {
            var remaining = TimeUnit.MILLISECONDS.toNanos(millis);
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

    static void sleep(long nanos) {
        try {
            TimeUnit.NANOSECONDS.sleep(Math.max(nanos, 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeQuietly(@Nullable MediaIO bytes) {
        if (bytes == null) {
            return;
        }
        try {
            bytes.close();
        } catch (IOException e) {
            LOG.debug("closing {} failed", source.uri(), e);
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
}
