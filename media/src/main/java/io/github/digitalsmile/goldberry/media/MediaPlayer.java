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

/// Plays audio: the non-visual Engine of `docs/goldberry-media.md` §3, with no
/// widget attached. `audio-player` and `media-player` are built over one.
///
/// ```java
/// var player = MediaPlayer.builder().build(); // plays on the default device
/// player.onStatus(status -> ui.post(() -> render(status)));
/// player.open(Source.of(Path.of("episode.opus")));
/// player.seek(Duration.ofMinutes(12));
/// ```
///
/// [#open] returns at once. Opening, buffering and playing happen on the Engine's
/// own threads, and progress arrives as [PlayerStatus] values. **Status listeners
/// are called on those threads.** A UI hands the value to its own thread, or
/// reads [#status()] on its frame tick, which is what the widgets do.
///
/// Phase 2 plays the audio track of a source. Video joins in phase 3
/// (`docs/media-plan.md`).
public final class MediaPlayer implements AutoCloseable {

    private static final Logger LOG = Logs.of(MediaPlayer.class);

    private final Supplier<AudioSink> sinks;
    private final List<DecoderProvider> decoderProviders;
    private final @Nullable List<MediaIOProvider> ioProviders;
    private final CopyOnWriteArrayList<Consumer<PlayerStatus>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private @Nullable Playback playback;
    private @Nullable AudioSink sink;
    private volatile float volume = 1f;
    private volatile boolean muted;
    private boolean closed;

    private MediaPlayer(Builder builder) {
        this.sinks = builder.sinks != null ? builder.sinks : SdlAudioSink::new;
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
            next = new Playback(ffmpeg, source, ioProviders, decoderProviders, newSink, _ -> publish());
            playback = next;
        }
        if (previous != null) {
            previous.close();
        }
        next.start();
        publish();
    }

    /// Plays after [#pause()]. Does nothing when nothing is open.
    public void play() {
        current().ifPresent(Playback::play);
    }

    /// Pauses. The position stays where it is.
    public void pause() {
        current().ifPresent(Playback::pause);
    }

    /// Seeks to `position`, clamped at zero. The first sample heard is the one at
    /// `position`, not the keyframe before it. Seeks requested while one runs are
    /// coalesced.
    public void seek(Duration position) {
        Objects.requireNonNull(position, "position");
        current().ifPresent(playback -> playback.seek(position.toNanos()));
        publish();
    }

    /// Sets the linear volume, 0 to 1.
    public void setVolume(float volume) {
        if (!(volume >= 0f && volume <= 1f)) {
            throw new IllegalArgumentException("volume " + volume + " is not within 0 and 1");
        }
        this.volume = volume;
        applyGain();
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
                Optional.ofNullable(playback.decoderName()));
    }

    /// Calls `listener` with every new status. **On the Engine's threads.**
    ///
    /// Position is not pushed: it changes continuously, so it is read from
    /// [#status()] when it is needed. A status is pushed when the state, the
    /// source's description, the error, the decoder, the volume or a seek changes.
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
        private @Nullable List<? extends DecoderProvider> decoderProviders;
        private @Nullable List<? extends MediaIOProvider> ioProviders;

        private Builder() {}

        /// Where audio goes: a new sink for every [MediaPlayer#open]. By default,
        /// an [SdlAudioSink] on the default playback device.
        public Builder sink(Supplier<AudioSink> sinks) {
            this.sinks = Objects.requireNonNull(sinks, "sinks");
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
