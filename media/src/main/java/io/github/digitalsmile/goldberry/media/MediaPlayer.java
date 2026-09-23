package io.github.digitalsmile.goldberry.media;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.media.audio.AudioSink;
import io.github.digitalsmile.goldberry.media.audio.SdlAudioSink;
import io.github.digitalsmile.goldberry.media.codec.DecoderProvider;
import io.github.digitalsmile.goldberry.media.engine.Playback;
import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibraries;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.Source;

/// Plays audio and video: the non-visual Engine of `docs/goldberry-media.md` §3,
/// with no widget attached. `audio-player`, `video-view`, `media-controls` and
/// `media-player` are built over one.
///
/// ```java
/// var player = MediaPlayer.builder().build(); // plays on the default device
/// player.onStatus(status -> ui.post(() -> render(status)));
/// player.open(Source.of(Path.of("episode.webm")));
/// player.seek(Duration.ofMinutes(12));
/// ```
///
/// [#open] returns at once. Opening, buffering and playing happen on the Engine's
/// own threads, and progress arrives as [PlayerStatus] values. **Status listeners
/// are called on those threads.** A UI hands the value to its own thread, or
/// reads [#status()] on its frame tick, which is what the widgets do.
///
/// The default audio track and the default video track play, whichever of the
/// two the source has. Cover art is never played as video. The picture to show
/// now is [#currentPicture()], which a view asks for on every frame it paints.
public final class MediaPlayer implements AutoCloseable {

    private static final Logger LOG = Logs.of(MediaPlayer.class);

    private final Supplier<AudioSink> sinks;
    private final MediaClock clock;
    private final Duration highWaterMark;
    private final List<DecoderProvider> decoderProviders;
    private final @Nullable List<MediaIOProvider> ioProviders;
    private final CopyOnWriteArrayList<Consumer<PlayerStatus>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private @Nullable Playback playback;
    private @Nullable AudioSink sink;
    private volatile float volume = 1f;
    private volatile boolean muted;
    private volatile float rate = 1f;
    private boolean closed;

    private MediaPlayer(Builder builder) {
        this.sinks = builder.sinks != null ? builder.sinks : SdlAudioSink::new;
        this.clock = builder.clock != null ? builder.clock : MediaClock.system();
        this.highWaterMark = builder.highWaterMark;
        this.decoderProviders = builder.decoderProviders != null
                ? List.copyOf(builder.decoderProviders)
                : ServiceLoader.load(DecoderProvider.class).stream()
                        .map(ServiceLoader.Provider::get)
                        .toList();
        this.ioProviders = builder.ioProviders == null ? null : List.copyOf(builder.ioProviders);
    }

    /// A builder. The sink defaults to [SdlAudioSink], the default playback
    /// device, and the providers to the ones [ServiceLoader] finds.
    public static Builder builder() {
        return new Builder();
    }

    /// Opens `source` and starts playing it, closing whatever was open.
    ///
    /// Returns at once. The status goes [PlaybackState#OPENING], then
    /// [PlaybackState#BUFFERING], then [PlaybackState#PLAYING], or
    /// [PlaybackState#ERROR] with the reason.
    ///
    /// @throws MediaException with [MediaError.NativesUnavailable] when FFmpeg is
    ///                        not loaded. Every other failure arrives as a status
    public void open(Source source) {
        Objects.requireNonNull(source, "source");
        var ffmpeg = FfmpegLibraries.get();
        Playback previous;
        Playback next;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("player closed");
            }
            previous = playback;
            var newSink = sinks.get();
            newSink.setGain(gain());
            sink = newSink;
            next = new Playback(
                    ffmpeg,
                    source,
                    ioProviders,
                    decoderProviders,
                    newSink,
                    clock,
                    highWaterMark.toNanos(),
                    _ -> publish());
            next.setRate(rate);
            playback = next;
        }
        if (previous != null) {
            previous.close();
        }
        next.start();
        publish();
    }

    /// How long until a picture that is not yet due falls due, or empty when none
    /// is waiting. A picture already due is not counted, since the next
    /// [#currentPicture()] shows it. A view uses this to paint when the next
    /// picture falls due rather than on every frame of a 120 Hz display.
    public Optional<Duration> untilNextPicture() {
        return current().flatMap(playback -> {
            var nanos = playback.nanosUntilNextPicture();
            return nanos.isPresent() ? Optional.of(Duration.ofNanos(nanos.getAsLong())) : Optional.empty();
        });
    }

    /// Plays after [#pause()]. Does nothing when nothing is open.
    public void play() {
        current().ifPresent(Playback::play);
    }

    /// Pauses. The position stays where it is.
    public void pause() {
        current().ifPresent(Playback::pause);
    }

    /// Seeks to `position`, clamped at zero. The first sample heard and the first
    /// picture shown are the ones at `position`, not the keyframe before it
    /// ([SeekMode#ACCURATE]). Seeks requested while one runs are coalesced.
    public void seek(Duration position) {
        seek(position, SeekMode.ACCURATE);
    }

    /// Seeks to `position` the way `mode` says. [SeekMode#KEYFRAME] is what a seek
    /// bar asks for while it is dragged: the keyframe at or before the position
    /// is shown, fast. Seeks requested while one runs are coalesced.
    public void seek(Duration position, SeekMode mode) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mode, "mode");
        current().ifPresent(playback -> playback.seek(position.toNanos(), mode == SeekMode.ACCURATE));
        publish();
    }

    /// The picture to show now: the newest decoded picture whose time has come on
    /// the master clock. A view calls this on every frame it paints and draws what
    /// it gets. See [VideoPicture] for how long its pixels stay put.
    ///
    /// Empty when nothing is open, when the source has no video, and before its
    /// first picture is decoded.
    public Optional<VideoPicture> currentPicture() {
        return current().flatMap(Playback::currentPicture);
    }

    /// Sets the linear volume, 0 to 1.
    public void setVolume(float volume) {
        if (!(volume >= 0f && volume <= 1f)) {
            throw new IllegalArgumentException("volume " + volume + " is not within 0 and 1");
        }
        this.volume = volume;
        applyGain();
    }

    /// Plays `rate` times as fast, pitch and all (`docs/goldberry-media.md` §3,
    /// "Rate"): 0.5 is half speed an octave down, 2 twice the speed an octave up.
    /// Kept for the next source opened, as the volume is.
    ///
    /// @throws IllegalArgumentException outside 0.25 to 4
    /// @throws IllegalStateException    when the sink cannot play at `rate`
    public void setRate(float rate) {
        if (!(rate >= Playback.MIN_RATE && rate <= Playback.MAX_RATE)) {
            throw new IllegalArgumentException(
                    "rate " + rate + " is not within " + Playback.MIN_RATE + " and " + Playback.MAX_RATE);
        }
        var current = current();
        if (current.isPresent() && current.get().setRate(rate) != rate) {
            throw new IllegalStateException("the audio sink cannot play at rate " + rate);
        }
        this.rate = rate;
        publish();
    }

    /// Moves `count` pictures on, or back for a negative count, and pauses there:
    /// the `.` and `,` of a player (`docs/goldberry-media.md` §6). The step is the
    /// picture length the video has shown, so a step lands on the next picture's
    /// first instant, and the picture covering it is shown.
    ///
    /// @return false when there is nothing to step: no video, or no picture yet
    public boolean step(int count) {
        return current().map(playback -> playback.step(count)).orElse(false);
    }

    /// Silences the output, or restores it, keeping the volume.
    public void setMuted(boolean muted) {
        this.muted = muted;
        applyGain();
    }

    /// Where the player is now.
    public PlayerStatus status() {
        var current = current();
        if (current.isEmpty()) {
            return new PlayerStatus(
                    PlaybackState.IDLE,
                    Duration.ZERO,
                    Optional.empty(),
                    Optional.empty(),
                    volume,
                    muted,
                    rate,
                    Optional.empty(),
                    Optional.empty(),
                    Duration.ZERO,
                    List.of(),
                    Optional.empty());
        }
        var playback = current.get();
        return new PlayerStatus(
                playback.state(),
                Duration.ofNanos(playback.positionNanos()),
                Optional.ofNullable(playback.info()),
                Optional.ofNullable(playback.error()),
                volume,
                muted,
                playback.rate(),
                Optional.ofNullable(playback.decoderName()),
                Optional.ofNullable(playback.videoDecoderName()),
                Duration.ofNanos(playback.bufferedAheadNanos()),
                playback.bufferedRanges(),
                Optional.ofNullable(playback.nowPlaying()));
    }

    /// Calls `listener` with every new status. **On the Engine's threads.**
    ///
    /// Position is not pushed: it changes continuously, so it is read from
    /// [#status()] when it is needed, as are the buffered stretches. A status is
    /// pushed when the state, the source's description, the error, a decoder, the
    /// volume, the stream's title or a seek changes, and when the first picture
    /// after a seek is ready, so that a paused view shows where it was moved to.
    ///
    /// @return what removes the listener
    public AutoCloseable onStatus(Consumer<PlayerStatus> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /// Stops playback and frees everything. The player cannot be reopened.
    @Override
    public void close() {
        Playback toClose;
        synchronized (lock) {
            closed = true;
            toClose = playback;
            playback = null;
            sink = null;
        }
        if (toClose != null) {
            toClose.close();
        }
        publish();
    }

    private Optional<Playback> current() {
        synchronized (lock) {
            return Optional.ofNullable(playback);
        }
    }

    private float gain() {
        return muted ? 0f : volume;
    }

    private void applyGain() {
        AudioSink current;
        synchronized (lock) {
            current = sink;
        }
        if (current != null) {
            current.setGain(gain());
        }
        publish();
    }

    private void publish() {
        var status = status();
        for (var listener : listeners) {
            try {
                listener.accept(status);
            } catch (RuntimeException e) {
                // A listener's bug must not stop playback, or the other listeners.
                LOG.warn("a MediaPlayer status listener failed", e);
            }
        }
    }

    /// How a [MediaPlayer] is made.
    public static final class Builder {

        private @Nullable Supplier<AudioSink> sinks;
        private @Nullable MediaClock clock;
        private Duration highWaterMark = Duration.ofNanos(Playback.HIGH_WATER_NANOS);
        private @Nullable List<? extends DecoderProvider> decoderProviders;
        private @Nullable List<? extends MediaIOProvider> ioProviders;

        private Builder() {}

        /// Where audio goes: a new sink for every [MediaPlayer#open]. By default,
        /// an [SdlAudioSink] on the default playback device.
        public Builder sink(Supplier<AudioSink> sinks) {
            this.sinks = Objects.requireNonNull(sinks, "sinks");
            return this;
        }

        /// What a source with no audio is timed against: [MediaClock#system()]
        /// unless a test hands in a clock it moves itself (§7, S5).
        public Builder clock(MediaClock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /// How far ahead every track is demuxed before playback starts, and
        /// before it plays on after a network stall (`docs/goldberry-media.md`
        /// §4, the high water mark). One second unless set. A local file reaches
        /// it at once; for a stream it is the cushion against the next stall, paid
        /// for in start-up time. Zero starts as soon as there is anything to play.
        public Builder highWaterMark(Duration highWaterMark) {
            Objects.requireNonNull(highWaterMark, "highWaterMark");
            if (highWaterMark.isNegative()) {
                throw new IllegalArgumentException("highWaterMark " + highWaterMark);
            }
            this.highWaterMark = highWaterMark;
            return this;
        }

        /// Exactly these decoder providers, instead of the ones `ServiceLoader`
        /// finds.
        public Builder decoderProviders(List<? extends DecoderProvider> providers) {
            this.decoderProviders = List.copyOf(providers);
            return this;
        }

        /// Exactly these protocols, instead of the ones `ServiceLoader` finds.
        public Builder ioProviders(List<? extends MediaIOProvider> providers) {
            this.ioProviders = List.copyOf(providers);
            return this;
        }

        /// The player.
        public MediaPlayer build() {
            return new MediaPlayer(this);
        }
    }
}
