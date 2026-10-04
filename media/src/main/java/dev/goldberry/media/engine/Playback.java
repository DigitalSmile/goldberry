package dev.goldberry.media.engine;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaClock;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaInfo;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.SubtitleSource;
import dev.goldberry.media.TimeRange;
import dev.goldberry.media.Track;
import dev.goldberry.media.VideoStatistics;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.AudioSink;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.ffi.Decoders;
import dev.goldberry.media.ffi.Demuxer;
import dev.goldberry.media.ffi.Ffmpeg;
import dev.goldberry.media.ffi.Hardware;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MediaIOs;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.io.UnsupportedSchemeException;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.PictureForm;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.subtitle.Cue;
import dev.goldberry.media.subtitle.Subtitles;

/// One source, open and playing: the demux thread, a decode thread per track, and
/// the state they share.
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
/// ([VideoWorker]) decodes, prepares and queues pictures in a [FrameQueue],
/// where they wait for the master clock: converted to BGRA, or as their planes
/// for a view that converts them on the GPU ([#setPictureForm]).
///
/// ## The clock
///
/// The master clock is the audio clock while there is audio: the presentation
/// time just past the last sample written, less what the sink still holds, less
/// how long a sample takes from there to the ear ([AudioSink#latencyNanos()] and
/// [#setAudioDelay]): the clock is what is heard, not what was written. A
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
/// It also waits for the **high water mark**: until every track has been
/// demuxed that far past the clock ([#bufferedAheadNanos()]), or the source has
/// ended, or the queues are full and the demux thread could not read more if it
/// tried. For a local file that takes milliseconds; for a network source it is
/// the cushion a stall is survived on.
///
/// The **low water mark is empty.** A track stalls when its decoder runs out of
/// packets with the source not at its end, and only then does playback go back to
/// [PlaybackState#BUFFERING]: the sink is paused and the free-running clock held,
/// until the high water mark is reached again. Pausing earlier, with media
/// still in hand, would trade one long silence for a later one.
///
/// ## Switching tracks
///
/// A track menu's choice ([#select]) is the demux thread's to carry out, between
/// two packets. An audio or video track is switched by **retiring** the decode
/// thread playing the old one and starting one on the new, then seeking to where
/// playback is: the seek is the whole of the synchronisation. A video switch
/// keeps the [FrameQueue], so the picture on screen stays up until the new
/// track's first picture replaces it.
///
/// ## Looping
///
/// A looping playback ([#setLooping]) does not end. At the end of the source the
/// demux thread seeks back to its start and queues a seam in every queue, with
/// the length of the source as the offset of the passes after it: the decode
/// threads drain their decoders there and move what follows on by that offset.
/// So the clock, the pictures' times and the sound run on across the seam, the
/// queues fill ahead of it as they do anywhere else, and the position reported
/// ([#positionNanos()]) is the time within the source. The length is measured
/// from the packets ([PassLength]), or is the container's duration when they say
/// none. A source that cannot seek, or a live one, ends.
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

    /// The slowest and fastest a player plays: a quarter and four
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
    private final Hardware hardware;
    private final AudioSink sink;
    private final Listener listener;
    private final MasterClock clock;
    private final long highWaterNanos;
    private final AtomicReference<@Nullable SeekRequest> pendingSeek = new AtomicReference<>();
    /// Makes a seek's two writes, the position it pins and the request, one step,
    /// so [#reseek] can tell whether the application's seek is waiting.
    private final Object seekLock = new Object();
    /// A track to switch to, waiting for the demux thread.
    private final AtomicReference<@Nullable Track> pendingTrack = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition wake = lock.newCondition();
    /// Guards the state and the readiness flags, which three threads change.
    private final Object gate = new Object();
    private final Thread demuxThread;
    private final List<Thread> workers = new ArrayList<>(2);
    // The demux thread's own: what plays, and the audio thread playing it.
    private int audioStream = -1;
    private int videoStream = -1;
    /// The subtitle track whose packets become cues, or -1. Written by any thread
    /// ([#hideSubtitles], [#showSubtitles]) and read by the demux thread.
    private volatile int subtitleStream = -1;
    private volatile CodecId subtitleCodec = CodecId.UNKNOWN;
    private final SubtitleTimeline cues = new SubtitleTimeline();
    private volatile @Nullable SubtitleSource subtitles;
    private @Nullable AudioWorker audioWorker;
    private @Nullable Thread audioThread;
    private @Nullable VideoWorker videoWorker;
    private @Nullable Thread videoThread;

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
    private volatile @Nullable Track audioTrack;
    private volatile @Nullable Track videoTrack;
    private volatile float rate = 1f;
    /// How long a picture lasts, as the video thread last measured it; 0 before
    /// it has.
    private volatile long frameNanos;
    private volatile @Nullable PacketQueue audioQueue;
    private volatile @Nullable PacketQueue videoQueue;
    private volatile @Nullable FrameQueue frames;
    /// Held around a write to the sink and the end it moves to, and around the
    /// clock's reading of both ([#writeAudio]).
    private final Object audioWrite = new Object();
    /// Pictures decoded, and dropped late, by the video threads.
    private final LongAdder videoDecoded = new LongAdder();
    private final LongAdder videoLate = new LongAdder();
    /// What the video thread prepares each picture as; set by [#setPictureForm].
    private volatile PictureForm pictureForm = PictureForm.CONVERTED;
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
    /// The sample the audio clock never reads before: the target of the last
    /// seek. Taking the device's latency off the clock would otherwise show a
    /// seek landing, then the position stepping back by the latency while the
    /// first samples travel to the ear.
    private volatile long audioFloorSample;
    /// The application's correction to the device's latency, in wall-clock
    /// nanoseconds: positive when the sound is heard later than the
    /// device says.
    private volatile long audioDelayNanos;
    /// The time since the queue emptied at the end of the track, which the
    /// latency is still counting down.
    private final AudioTail tail;
    /// What the position reads while a seek settles: the target.
    private volatile long seekingToNanos = Frame.NO_PTS;
    /// Whether the source starts over at its end; set by [#setLooping].
    private volatile boolean looping;
    /// How long one pass over a looping source is, once the demux thread has
    /// wrapped around once; 0 before.
    private volatile long loopPeriodNanos;
    /// What the passes queued now are moved by: every pass queued since the last
    /// seek, end to end. Written by the demux thread.
    private volatile long loopOffsetNanos;
    // The demux thread's own: how long a pass over the source is, as far as
    // its packets have said, and whether one has been read since the last seam,
    // so a source with none never wraps around and around.
    private final PassLength passLength = new PassLength();
    private boolean readSinceWrap;

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
    /// @param hardware       whether the built-in decoder tries a device for
    ///                       video
    /// @param time           what a source with no audio is timed against
    /// @param highWaterNanos how far ahead to demux before playing, and before
    ///                       playing on after a stall
    public Playback(
            Ffmpeg ffmpeg,
            Source source,
            @Nullable List<? extends MediaIOProvider> ioProviders,
            List<? extends DecoderProvider> decoderProviders,
            Hardware hardware,
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
        this.hardware = Objects.requireNonNull(hardware, "hardware");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = new MasterClock(time);
        this.tail = new AudioTail(time);
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
        return sourceNanos(presentationNanos());
    }

    /// `nanos` on the playback's clock as a time within the source. The same
    /// but for a looping source that has wrapped, whose clock runs on past its
    /// length: there, the time within the pass playing, or within the last
    /// pass once the playback has ended.
    long sourceNanos(long nanos) {
        var period = loopPeriodNanos;
        if (period <= 0) {
            return nanos;
        }
        if (state == PlaybackState.ENDED) {
            return Math.clamp(nanos - loopOffsetNanos, 0, period);
        }
        return Math.floorMod(nanos, period);
    }

    /// Whether the source starts over when it ends: see the type's
    /// documentation. Takes effect at the next end the demux thread reads, which
    /// is up to a queue's length ahead of what is heard; a playback that has
    /// ended already stays ended until a seek.
    public void setLooping(boolean looping) {
        this.looping = looping;
        signal();
    }

    /// Whether the source starts over when it ends.
    public boolean looping() {
        return looping;
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

    /// The audio track playing, once the tracks are chosen.
    public @Nullable Track audioTrack() {
        return audioTrack;
    }

    /// The video track playing, once the tracks are chosen.
    public @Nullable Track videoTrack() {
        return videoTrack;
    }

    /// Where the subtitles showing come from, or null when none show.
    public @Nullable SubtitleSource subtitles() {
        return subtitles;
    }

    /// The cues showing now: none when no subtitles are chosen.
    public List<Cue> showingCues() {
        return subtitles == null ? List.of() : cues.showing(Duration.ofNanos(positionNanos()));
    }

    /// Shows the cues of `file`, read already, in place of whatever subtitles
    /// show. A subtitle track of the source stops being read.
    public void showSubtitles(SubtitleSource.External file, List<Cue> read) {
        subtitleStream = -1;
        cues.replace(read);
        subtitles = file;
        listener.changed(this);
    }

    /// Shows no subtitles.
    public void hideSubtitles() {
        subtitleStream = -1;
        subtitles = null;
        cues.clear();
        listener.changed(this);
    }

    /// Plays `track` in place of the track of its kind playing, from where
    /// playback is: a track menu's choice.
    ///
    /// - An **audio** or **video** track: the demux thread retires the decode
    ///   thread of that kind, starts one on `track`, and makes an accurate seek
    ///   to the position, so the new track comes in at the right sample or on the
    ///   picture that covers the position. A track with no decoder is refused
    ///   there and the current one plays on.
    /// - A **subtitle** track is shown in place of any subtitles showing: the
    ///   demux thread selects it and seeks to the position, so the cues around it
    ///   are read.
    ///
    /// Requests coalesce: the latest wins.
    ///
    /// @throws IllegalArgumentException when `track` is not one of the source's
    ///                                  tracks, is cover art, or is of a kind
    ///                                  that does not play
    public void select(Track track) {
        Objects.requireNonNull(track, "track");
        var described = info;
        if (described == null || !described.tracks().contains(track)) {
            throw new IllegalArgumentException("not a track of " + source.uri() + ": " + track);
        }
        switch (track.type()) {
            case AUDIO, SUBTITLE -> {}
            case VIDEO -> {
                if (track.attachedPicture()) {
                    throw new IllegalArgumentException("cover art is not played as video: " + track);
                }
            }
            case ATTACHMENT, DATA ->
                throw new IllegalArgumentException(
                        "an audio, video or subtitle track can be chosen; " + track.type() + " cannot");
        }
        pendingTrack.set(track);
        signal();
    }

    /// How fast playback runs: 1 is as recorded.
    public float rate() {
        return rate;
    }

    /// Plays `requested` times as fast, pitch and all, from now on: the sink
    /// resamples, and the free-running clock counts faster.
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
    /// which lands on the picture that starts there: a player's `,` and `.`.
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

    /// The picture to show now, converted to BGRA and handed out to a view that
    /// will draw it (see [VideoPicture] for how long it stays valid), or empty
    /// before the first picture, for a source with no video, and while the
    /// picture shown is [PictureForm#PLANES].
    public Optional<VideoPicture> currentPicture() {
        return shownPicture()
                .flatMap(picture ->
                        picture instanceof VideoPicture converted ? Optional.of(converted) : Optional.empty());
    }

    /// The picture to show now, in the form it was prepared in, handed out to a
    /// view that will draw it; empty before the first picture, and for a source
    /// with no video.
    public Optional<Picture> shownPicture() {
        var queue = frames;
        return queue == null ? Optional.empty() : Optional.ofNullable(queue.present(presentationNanos(), true));
    }

    /// What the video thread prepares pictures as now.
    public PictureForm pictureForm() {
        return pictureForm;
    }

    /// Prepares the pictures decoded from now on as `form`. The video thread
    /// reads it for each picture.
    ///
    /// **To planes**, nothing else changes: the converted pictures queued play
    /// out, since a view that draws planes draws a [VideoPicture] too.
    ///
    /// **Back to converted**, the planes queued are of no use to a view that
    /// draws on the CPU, so an accurate seek to where playback is flushes them,
    /// and the picture covering the position comes back converted. It is the
    /// seek a track switch makes: the picture shown stays up until
    /// that one replaces it, the sound is flushed with the pictures, and a
    /// playback at its end plays its last picture's time again.
    public void setPictureForm(PictureForm form) {
        Objects.requireNonNull(form, "form");
        boolean flush;
        synchronized (seekLock) {
            flush = pictureForm == PictureForm.PLANES && form == PictureForm.CONVERTED;
            pictureForm = form;
        }
        if (flush && frames != null && !stopping) {
            reseek(presentationNanos());
        }
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
            tail.pause();
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
    /// sample heard and the first picture shown are the target's. Coalesced:
    /// while one seek runs, only the latest request waits.
    public void seek(long positionNanos, boolean accurate) {
        if (!canSeek()) {
            return;
        }
        var target = Math.max(positionNanos, 0);
        synchronized (seekLock) {
            seekingToNanos = target;
            pendingSeek.set(new SeekRequest(target, accurate));
        }
        signal();
    }

    /// Whether a seek can do anything: the bytes can be read out of order and the
    /// source is not live. A live stream has only a now, and a source that cannot
    /// seek has nowhere to go, so a seek on either is dropped and the stream plays
    /// on. Handed to the demuxer, it would be FFmpeg's generic seek, which rewinds
    /// to the last index entry it holds: inside the AVIO buffer that works, past
    /// it the unseekable source refuses, and which of the two happened depended
    /// on how far the demux thread had read. Before the source is open, nothing
    /// is known and the request waits for [#seekTo], which asks again.
    private boolean canSeek() {
        var bytes = io;
        return bytes == null || (bytes.isSeekable() && !bytes.isLive());
    }

    /// An accurate seek the Engine makes for itself, after a track switch, a
    /// subtitle track chosen, or a decoder that fell back. Unlike [#seek], it never
    /// replaces a seek the application asked for that has not run yet. That seek
    /// moves every queue anyway, and it is where the application wants to be.
    void reseek(long positionNanos) {
        if (!canSeek()) {
            return;
        }
        var target = Math.max(sourceNanos(positionNanos), 0);
        synchronized (seekLock) {
            if (pendingSeek.get() == null) {
                seekingToNanos = target;
                pendingSeek.set(new SeekRequest(target, true));
            }
        }
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
            readPackets(opened);
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
    /// every codec that has none.
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
        audioTrack = audio.orElse(null);
        videoTrack = video.orElse(null);
        audioStream = audio.map(Track::index).orElse(-1);
        videoStream = video.map(Track::index).orElse(-1);
        if (audio.isPresent()) {
            var queue = new PacketQueue(QUEUE_NANOS, AUDIO_QUEUE_BYTES);
            audioQueue = queue;
            format = sink.open(AudioFormat.DEFAULT);
            // Opened paused: the first sample waits for the first picture.
            sink.pause();
            clock.followAudio(this::audioClockNanos);
            workers.add(audioThread(opened, audio.get(), queue));
        }
        if (video.isPresent()) {
            var queue = new PacketQueue(QUEUE_NANOS, VIDEO_QUEUE_BYTES);
            var pictures = new FrameQueue();
            videoQueue = queue;
            frames = pictures;
            workers.add(videoThread(opened, video.get(), queue, pictures));
        }
        boolean changed;
        synchronized (gate) {
            changed = setStateLocked(paused ? PlaybackState.PAUSED : PlaybackState.BUFFERING);
        }
        notifyIf(changed);
        workers.forEach(Thread::start);
    }

    private void readPackets(Demuxer opened) {
        var ended = false;
        while (!stopping) {
            var track = pendingTrack.getAndSet(null);
            if (track != null) {
                switch (track.type()) {
                    case SUBTITLE -> showTrack(opened, track);
                    case VIDEO -> switchVideo(opened, track);
                    case AUDIO -> switchAudio(opened, track);
                    case ATTACHMENT, DATA -> {
                        // Refused by select(); nothing plays them.
                    }
                }
                continue;
            }
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
                awaitWhile(() -> pendingSeek.get() == null && pendingTrack.get() == null);
                continue;
            }
            if (mustWait()) {
                // Full: as buffered as it will get.
                startIfBuffered();
                awaitWhile(() -> pendingSeek.get() == null && pendingTrack.get() == null && mustWait(), 10);
                continue;
            }
            var packet = opened.read();
            if (packet == null) {
                if (looping && wrap(opened)) {
                    continue;
                }
                forEachQueue(queue -> queue.end(serial));
                ended = true;
                startIfBuffered();
                continue;
            }
            if (packet.streamIndex() == subtitleStream && packet.streamIndex() >= 0) {
                takeCue(packet);
                continue;
            }
            var target = packet.streamIndex() == audioStream
                    ? audioQueue
                    : packet.streamIndex() == videoStream ? videoQueue : null;
            if (target == null) {
                packet.close();
            } else {
                if (target == videoQueue) {
                    passLength.video(packet);
                } else {
                    passLength.audio(packet);
                }
                readSinceWrap = true;
                if (!target.put(packet, serial) && stopping) {
                    return;
                }
            }
            startIfBuffered();
            followTitle();
        }
    }

    /// Turns one subtitle packet into a cue for the timeline, and frees it.
    private void takeCue(Packet packet) {
        try (packet) {
            var start = packet.ptsNanos();
            if (start == Frame.NO_PTS || packet.data().byteSize() == 0) {
                return;
            }
            var end = start + packet.timeBase().toNanos(Math.max(packet.duration(), 0));
            Subtitles.fromPacket(
                            subtitleCodec,
                            packet.data().toArray(ValueLayout.JAVA_BYTE),
                            Duration.ofNanos(start),
                            Duration.ofNanos(end))
                    .ifPresent(cues::add);
        }
    }

    /// Shows `track`'s cues in place of any subtitles showing: selects its stream
    /// in the demuxer and seeks to where playback is, so its packets around the
    /// position are read.
    private void showTrack(Demuxer opened, Track track) {
        if (subtitles instanceof SubtitleSource.Embedded(var showing) && showing.index() == track.index()) {
            return;
        }
        cues.clear();
        subtitleCodec = track.codec();
        subtitleStream = track.index();
        selectStreams(opened);
        reseek(presentationNanos());
        // Published after the seek is asked for, so an application that waits for
        // it and then seeks is not undone by the Engine's own seek.
        subtitles = new SubtitleSource.Embedded(track);
        listener.changed(this);
    }

    /// An audio thread on `track`, reading `queue`: made, not started.
    private Thread audioThread(Demuxer opened, Track track, PacketQueue queue) {
        var worker = new AudioWorker(this, opened, track.index(), queue, format);
        var thread = Thread.ofPlatform().name("goldberry-media-audio").daemon().unstarted(worker::play);
        audioWorker = worker;
        audioThread = thread;
        return thread;
    }

    /// Whether `track` can be decoded, and if not the warning that `current`
    /// plays on. One message for the audio switch and the video one.
    private boolean decodes(Demuxer opened, Track track, Track current) {
        if (Decoders.supports(ffmpeg, opened, track.index(), decoderProviders)) {
            return true;
        }
        LOG.warn("no decoder for {} on track {}; track {} plays on", track.codecName(), track.index(), current.index());
        return false;
    }

    /// Plays `track` in place of the audio track playing: retires the audio
    /// thread, starts one on `track`, and seeks to where playback is, so that
    /// every queue starts over at one position and the new track comes in on the
    /// right sample. The sink plays what the old thread wrote until the seek's
    /// flush clears it, so the switch is not a silence.
    private void switchAudio(Demuxer opened, Track track) {
        var current = audioTrack;
        if (current == null || current.index() == track.index()) {
            return;
        }
        if (!decodes(opened, track, current)) {
            return;
        }
        var retiring = audioWorker;
        var retiringThread = audioThread;
        var retiringQueue = audioQueue;
        if (retiring != null) {
            retiring.retire();
        }
        if (retiringQueue != null) {
            retiringQueue.abort();
        }
        if (retiringThread != null) {
            join(retiringThread);
            workers.remove(retiringThread);
        }
        var queue = new PacketQueue(QUEUE_NANOS, AUDIO_QUEUE_BYTES);
        audioQueue = queue;
        audioStream = track.index();
        audioTrack = track;
        selectStreams(opened);
        synchronized (gate) {
            audioReady = false;
            audioDone = false;
        }
        var thread = audioThread(opened, track, queue);
        workers.add(thread);
        thread.start();
        reseek(presentationNanos());
        listener.changed(this);
    }

    /// A video thread on `track`, reading `queue` and filling `pictures`: made,
    /// not started.
    private Thread videoThread(Demuxer opened, Track track, PacketQueue queue, FrameQueue pictures) {
        var worker = new VideoWorker(this, opened, track.index(), queue, pictures);
        var thread = Thread.ofPlatform().name("goldberry-media-video").daemon().unstarted(worker::play);
        videoWorker = worker;
        videoThread = thread;
        return thread;
    }

    /// Shows `track` in place of the video track playing: retires the video
    /// thread, starts one on `track` over the same [FrameQueue], and seeks to
    /// where playback is. The picture on screen stays up until the new track's
    /// first picture, the one covering the position, replaces it, so the switch
    /// is a cut and not a frame of black.
    private void switchVideo(Demuxer opened, Track track) {
        var current = videoTrack;
        var pictures = frames;
        if (current == null || pictures == null || current.index() == track.index()) {
            return;
        }
        if (!decodes(opened, track, current)) {
            return;
        }
        var retiring = videoWorker;
        var retiringThread = videoThread;
        var retiringQueue = videoQueue;
        if (retiring != null) {
            retiring.retire();
        }
        // Wakes a thread waiting for a picture buffer, and one waiting for a
        // packet; the frame queue itself goes on to the next thread.
        pictures.releaseWaiters();
        if (retiringQueue != null) {
            retiringQueue.abort();
        }
        if (retiringThread != null) {
            join(retiringThread);
            workers.remove(retiringThread);
        }
        var queue = new PacketQueue(QUEUE_NANOS, VIDEO_QUEUE_BYTES);
        videoQueue = queue;
        videoStream = track.index();
        videoTrack = track;
        // The new track measures its own picture length.
        frameNanos = 0;
        selectStreams(opened);
        synchronized (gate) {
            videoReady = false;
            videoDone = false;
        }
        var thread = videoThread(opened, track, queue, pictures);
        workers.add(thread);
        thread.start();
        reseek(presentationNanos());
        listener.changed(this);
    }

    /// Tells the demuxer to read the streams that play: the audio, the video and
    /// the subtitle track chosen now. The rest are discarded.
    private void selectStreams(Demuxer opened) {
        var selected = new HashSet<Integer>();
        for (var stream : List.of(audioStream, videoStream, subtitleStream)) {
            if (stream >= 0) {
                selected.add(stream);
            }
        }
        opened.select(selected);
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

    /// Starts a looping source over: seeks to its start and queues a seam in
    /// every queue, moving the next pass on by the source's length.
    ///
    /// @return false when the source cannot start over, and ends instead: it
    ///         cannot seek, it is live, nothing says how long it is, or nothing
    ///         was read since it last started over
    private boolean wrap(Demuxer opened) {
        var bytes = io;
        var described = info;
        var measured = passLength.nanos();
        var period = measured != Frame.NO_PTS
                ? measured
                : described == null
                        ? 0
                        : described.duration().map(Duration::toNanos).orElse(0L);
        if (!readSinceWrap || period <= 0 || bytes == null || !bytes.isSeekable() || bytes.isLive()) {
            return false;
        }
        try {
            opened.seek(0);
        } catch (MediaException e) {
            LOG.warn("cannot start {} over; it ends", source.uri(), e);
            return false;
        }
        readSinceWrap = false;
        loopPeriodNanos = period;
        var offset = loopOffsetNanos + period;
        loopOffsetNanos = offset;
        forEachQueue(queue -> queue.seam(serial, offset));
        return true;
    }

    private void seekTo(Demuxer opened, SeekRequest request) {
        if (!canSeek()) {
            // Asked while the source was still opening, and now known to be a
            // seek that cannot be made: dropped, and the position is the clock's.
            seekingToNanos = Frame.NO_PTS;
            return;
        }
        opened.seek(request.targetNanos());
        loopOffsetNanos = 0;
        // A seek to the very end reads nothing before it, and a looping source
        // still starts over from there: only a wrap with nothing after it stops one.
        readSinceWrap = true;
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

    /// Whether the built-in decoder tries a device, for the video thread.
    Hardware hardware() {
        return hardware;
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

    /// The video thread received a picture from its decoder.
    void videoDecoded() {
        videoDecoded.increment();
    }

    /// The video thread dropped a picture before preparing it: late by a whole
    /// picture, with more waiting.
    void videoLate() {
        videoLate.increment();
    }

    /// What has happened to the pictures decoded since the source was opened.
    public VideoStatistics videoStatistics() {
        var queue = frames;
        return new VideoStatistics(
                videoDecoded.sum(),
                videoLate.sum(),
                queue == null ? 0 : queue.passedCount(),
                queue == null ? 0 : queue.shownCount());
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

    /// The audio clock: what is heard now. What has left the sink's
    /// queue, less how long it takes to reach the ear: the sink's latency and
    /// the application's delay, which are wall-clock time and so count
    /// [#rate()] times as much stream time. Once the queue has emptied at the
    /// end, the [AudioTail] counts the latency down, so the clock reaches the end
    /// as the last sample is heard. Never before the last seek's target.
    private long audioClockNanos() {
        long written;
        long queued;
        synchronized (audioWrite) {
            written = writtenEndSample;
            queued = sink.queuedSamples();
        }
        var left = format.nanos(Math.max(written - queued, 0));
        var latency = audioLatencyNanos();
        var inTail = tail.elapsedNanos();
        if (inTail != AudioTail.NONE) {
            latency = Math.max(latency - inTail, 0);
        }
        var heard = left - Math.round(latency * (double) rate);
        return Math.max(heard, format.nanos(audioFloorSample));
    }

    /// The audio thread found the queue empty at the end of the track: the tail
    /// starts, and lasts the latency.
    void audioTailStarted() {
        synchronized (gate) {
            tail.start(paused);
        }
    }

    /// Whether the last samples are still on their way to the ear.
    boolean audioTailPending() {
        var inTail = tail.elapsedNanos();
        return inTail != AudioTail.NONE && inTail < audioLatencyNanos();
    }

    /// How long a sample takes from leaving the sink's queue to being heard, in
    /// wall-clock nanoseconds: [AudioSink#latencyNanos()] and
    /// [#setAudioDelay]'s delay.
    public long audioLatencyNanos() {
        return sink.latencyNanos() + audioDelayNanos;
    }

    /// Corrects the sink's latency by `nanos`: positive holds pictures back for
    /// sound heard later than the device says, negative brings them forward for
    /// a picture that is itself late, as on a television.
    public void setAudioDelay(long nanos) {
        audioDelayNanos = nanos;
    }

    /// Writes `samples` to the sink, ending at `endSample`: the write and the
    /// end it moves to, together, under the lock [#audioClockNanos] reads both
    /// under. Apart, a reading between the two finds the queue grown by a packet
    /// and the end not yet moved, and the clock a packet behind for an instant,
    /// which a view waking for its next picture sleeps a picture too long on.
    void writeAudio(MemorySegment data, int samples, long endSample) {
        synchronized (audioWrite) {
            sink.write(data, samples);
            audioWritten(endSample, true);
        }
    }

    /// The audio thread wrote up to `endSample`, or (not `played`) moved there by
    /// a seek. A write is the audio clock taking over from a seek's target, once
    /// the sink is current: sound from before a seek that is asked for and has
    /// not run yet, still being written, would take the position back to where
    /// the seek left until the seek lands.
    void audioWritten(long endSample, boolean played) {
        tail.reset();
        if (!played) {
            // Before the end moves: after a seek backwards, a reading between the
            // two would otherwise find the new end held up by the old floor, a
            // target this seek has left behind.
            audioFloorSample = endSample;
        }
        writtenEndSample = endSample;
        if (played && sinkCurrent()) {
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
        tail.resume();
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
