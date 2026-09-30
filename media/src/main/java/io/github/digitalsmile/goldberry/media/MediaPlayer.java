package io.github.digitalsmile.goldberry.media;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
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
import io.github.digitalsmile.goldberry.media.ffi.Hardware;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.picture.Picture;
import io.github.digitalsmile.goldberry.media.picture.PictureForm;
import io.github.digitalsmile.goldberry.media.picture.VideoPicture;
import io.github.digitalsmile.goldberry.media.picture.VideoPlanes;
import io.github.digitalsmile.goldberry.media.subtitle.Cue;
import io.github.digitalsmile.goldberry.media.subtitle.Subtitles;

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
///
/// ## Views, and the form of the pictures
///
/// A view that draws the pictures [#attachView]s with the [PictureForm] it
/// draws: converted to BGRA for a view that blits on the CPU, or as planes for
/// one that converts them on the GPU. The pictures are prepared as planes only
/// while every view attached asks for them; a player with no view attached, or
/// with one that draws on the CPU, converts them ([#pictureForm()]). A view that
/// asks for planes draws what [#shownPicture()] hands out, in either form.
public final class MediaPlayer implements AutoCloseable {

    private static final Logger LOG = Logs.of(MediaPlayer.class);

    private final Supplier<AudioSink> sinks;
    private final MediaClock clock;
    private final Duration highWaterMark;
    private final List<DecoderProvider> decoderProviders;
    private final @Nullable List<MediaIOProvider> ioProviders;
    private volatile HardwareDecoding hardwareDecoding;
    private final CopyOnWriteArrayList<Consumer<PlayerStatus>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private @Nullable Playback playback;
    private @Nullable AudioSink sink;
    private volatile float volume = 1f;
    private volatile boolean muted;
    private volatile float rate = 1f;
    private volatile Duration audioDelay = Duration.ZERO;
    private boolean closed;
    /// The views attached, and the form they decide on. Guarded by `lock`.
    private final List<Attachment> attachments = new ArrayList<>();
    private PictureForm pictureForm = PictureForm.CONVERTED;

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
        this.hardwareDecoding = builder.hardwareDecoding;
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
                    Hardware.of(hardwareDecoding),
                    newSink,
                    clock,
                    highWaterMark.toNanos(),
                    _ -> publish());
            next.setRate(rate);
            next.setAudioDelay(audioDelay.toNanos());
            next.setPictureForm(pictureForm);
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

    /// The picture to show now, in the form it was prepared in: a
    /// [VideoPicture], or [VideoPlanes] while every view attached asks for
    /// planes. What a view that draws planes asks for on every frame it paints.
    /// The pictures stay put as [Picture] says.
    ///
    /// Empty when nothing is open, when the source has no video, and before its
    /// first picture is decoded. Unlike [#currentPicture()], never empty for a
    /// picture's form alone.
    public Optional<Picture> shownPicture() {
        return current().flatMap(Playback::shownPicture);
    }

    /// What has happened to the pictures of the source open now: how many were
    /// decoded, and how many shown and dropped. All zero when nothing is open.
    public VideoStatistics videoStatistics() {
        return current().map(Playback::videoStatistics).orElse(VideoStatistics.NONE);
    }

    /// Attaches a view that draws this player's pictures in `form`, until the
    /// attachment is closed. The pictures are prepared as [PictureForm#PLANES]
    /// only while every attached view asks for planes, and the form carries
    /// over to the next source [#open]ed.
    ///
    /// A view attaches when it is shown and closes the attachment when it is
    /// not, and it may change its form in between ([Attachment#setForm]), as a
    /// view that finds it cannot reach a GPU does.
    public Attachment attachView(PictureForm form) {
        var attachment = new Attachment(this, form);
        synchronized (lock) {
            attachments.add(attachment);
            decideFormLocked();
        }
        return attachment;
    }

    /// The form the pictures are prepared in now: [PictureForm#PLANES] while at
    /// least one view is attached and every one asks for planes, and
    /// [PictureForm#CONVERTED] otherwise.
    public PictureForm pictureForm() {
        synchronized (lock) {
            return pictureForm;
        }
    }

    /// Decides the form from the attachments, and tells the playback when it
    /// changes. Under `lock`, so the playback hears the changes in order.
    private void decideFormLocked() {
        var planes = !attachments.isEmpty()
                && attachments.stream().allMatch(attachment -> attachment.form == PictureForm.PLANES);
        var form = planes ? PictureForm.PLANES : PictureForm.CONVERTED;
        if (form == pictureForm) {
            return;
        }
        pictureForm = form;
        LOG.debug("pictures are prepared as {} for {} view(s)", form, attachments.size());
        if (playback != null) {
            playback.setPictureForm(form);
        }
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

    /// The largest [#setAudioDelay] takes either way: more than any device's
    /// latency, and less than a correction that would be a different problem.
    public static final Duration MAX_AUDIO_DELAY = Duration.ofSeconds(2);

    /// Corrects how late the sound is heard, for keeping pictures with it
    /// (`docs/goldberry-media.md` §3, "Master clock", ADR-0474). Kept for the
    /// next source opened, as the rate is.
    ///
    /// The audio clock already takes off what the sink reports
    /// ([#audioLatency()]): SDL's buffers everywhere, and the device's own
    /// latency where a provider can read it (CoreAudio, on macOS). This is for
    /// the rest: a system with no provider yet, a Bluetooth device that
    /// under-reports, a receiver between the computer and the speakers. **Positive** when the sound is heard later
    /// than that, which holds the pictures back to meet it. **Negative** when the
    /// picture is the late one, as on a television that processes it, which
    /// brings the pictures forward.
    ///
    /// @throws IllegalArgumentException beyond [#MAX_AUDIO_DELAY] either way
    public void setAudioDelay(Duration delay) {
        Objects.requireNonNull(delay, "delay");
        if (delay.abs().compareTo(MAX_AUDIO_DELAY) > 0) {
            throw new IllegalArgumentException("an audio delay of " + delay + " is beyond " + MAX_AUDIO_DELAY);
        }
        this.audioDelay = delay;
        current().ifPresent(playback -> playback.setAudioDelay(delay.toNanos()));
    }

    /// What [#setAudioDelay] set: zero by default.
    public Duration audioDelay() {
        return audioDelay;
    }

    /// How long a sample takes from leaving the player to being heard, as the
    /// audio clock counts it now: the sink's latency and [#audioDelay()]. What
    /// the pictures are held back by, which a status panel can show. Zero with
    /// nothing open.
    public Duration audioLatency() {
        return current()
                .map(playback -> Duration.ofNanos(playback.audioLatencyNanos()))
                .orElse(Duration.ZERO);
    }

    /// Whether the built-in decoder may decode video on the platform's video
    /// engine: what [Builder#hardwareDecoding] set, or [#setHardwareDecoding].
    public HardwareDecoding hardwareDecoding() {
        return hardwareDecoding;
    }

    /// Decodes video on the platform's video engine, or not, **from the next
    /// source opened** (ADR-0470): the decoder of the source playing now is
    /// already chosen. To apply it at once, open the same source again and seek
    /// to where it was.
    public void setHardwareDecoding(HardwareDecoding mode) {
        this.hardwareDecoding = Objects.requireNonNull(mode, "mode");
    }

    /// Plays `track`, one of the source's audio or video tracks, in place of the
    /// one of its kind playing, from where playback is: a track menu's choice
    /// (`docs/goldberry-media.md` §6). Returns at once; the switch happens on the
    /// Engine's threads, and [PlayerStatus#audioTrack()] or
    /// [PlayerStatus#videoTrack()] says when it has. A track with no decoder is
    /// refused there, and the playing one plays on. A new video track comes in on
    /// the picture that covers the position; until it does, the old track's last
    /// picture stays up.
    ///
    /// A subtitle track is shown in place of whatever subtitles show, and
    /// [PlayerStatus#subtitles()] says so. Only text subtitles have cues (SubRip,
    /// WebVTT, ASS, MP4 text); a bitmap track shows nothing.
    ///
    /// @throws IllegalArgumentException when `track` is not an audio, video or
    ///                                  subtitle track of the open source, or is
    ///                                  its cover art
    /// @throws IllegalStateException    when nothing is open
    public void selectTrack(Track track) {
        current()
                .orElseThrow(() -> new IllegalStateException("nothing is open"))
                .select(track);
    }

    /// Shows no subtitles. Does nothing when nothing is open.
    public void hideSubtitles() {
        current().ifPresent(Playback::hideSubtitles);
        publish();
    }

    /// Reads a SubRip or WebVTT file and shows it in place of whatever subtitles
    /// show, for the source open now; opening another source drops it. The file
    /// is read through the player's protocols, so `file` may be any scheme a
    /// source may.
    ///
    /// **Reads the whole file on the calling thread**, so a network one is loaded
    /// off the UI thread.
    ///
    /// @throws IOException           when the file cannot be read, or is neither
    ///                               format
    /// @throws IllegalStateException when nothing is open
    public void loadSubtitles(Source file) throws IOException {
        Objects.requireNonNull(file, "file");
        var playback = current().orElseThrow(() -> new IllegalStateException("nothing is open"));
        var read = Subtitles.read(file, ioProviders != null ? ioProviders : serviceProtocols());
        playback.showSubtitles(new SubtitleSource.External(file), read);
    }

    /// The cues showing now, for a view to draw: none when no subtitles show, and
    /// usually one. Read as a picture is, on every frame that paints.
    public List<Cue> currentSubtitles() {
        return current().map(Playback::showingCues).orElse(List.of());
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
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
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
                Optional.ofNullable(playback.nowPlaying()),
                Optional.ofNullable(playback.audioTrack()),
                Optional.ofNullable(playback.videoTrack()),
                Optional.ofNullable(playback.subtitles()));
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

    private static List<MediaIOProvider> serviceProtocols() {
        return ServiceLoader.load(MediaIOProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
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

    /// A view attached to a [MediaPlayer] ([MediaPlayer#attachView]), and the
    /// form it draws pictures in. Closing it detaches the view; closing it
    /// again does nothing.
    public static final class Attachment implements AutoCloseable {

        private final MediaPlayer player;
        /// Guarded by the player's lock.
        private PictureForm form;
        private boolean closed;

        private Attachment(MediaPlayer player, PictureForm form) {
            this.player = player;
            this.form = Objects.requireNonNull(form, "form");
        }

        /// The form this view draws.
        public PictureForm form() {
            synchronized (player.lock) {
                return form;
            }
        }

        /// Says this view draws `form` from now on. Does nothing once closed.
        public void setForm(PictureForm form) {
            Objects.requireNonNull(form, "form");
            synchronized (player.lock) {
                if (closed || this.form == form) {
                    return;
                }
                this.form = form;
                player.decideFormLocked();
            }
        }

        /// Detaches the view.
        @Override
        public void close() {
            synchronized (player.lock) {
                if (closed) {
                    return;
                }
                closed = true;
                player.attachments.remove(this);
                player.decideFormLocked();
            }
        }

        @Override
        public String toString() {
            return "MediaPlayer.Attachment[" + form() + (closed ? ", closed]" : "]");
        }
    }

    /// How a [MediaPlayer] is made.
    public static final class Builder {

        private @Nullable Supplier<AudioSink> sinks;
        private @Nullable MediaClock clock;
        private Duration highWaterMark = Duration.ofNanos(Playback.HIGH_WATER_NANOS);
        private @Nullable List<? extends DecoderProvider> decoderProviders;
        private @Nullable List<? extends MediaIOProvider> ioProviders;
        private HardwareDecoding hardwareDecoding = HardwareDecoding.AUTO;

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

        /// Whether the built-in decoder may decode video on the platform's video
        /// engine (§3, "Fallback ladder"; ADR-0470). [HardwareDecoding#AUTO]
        /// unless set: every failure on the device falls back to software without
        /// the application seeing it. [HardwareDecoding#OFF] is for tests that
        /// compare pictures byte for byte.
        public Builder hardwareDecoding(HardwareDecoding mode) {
            this.hardwareDecoding = Objects.requireNonNull(mode, "mode");
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
