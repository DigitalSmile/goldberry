package dev.goldberry.example.ui.capture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.Host;
import dev.goldberry.example.capture.AudioClip;
import dev.goldberry.example.capture.AudioClipRecorder;
import dev.goldberry.example.capture.RateMeter;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.media.MediaLines;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.view.AudioPlayer;
import dev.goldberry.mic.Microphone;
import dev.goldberry.mic.MicrophoneChange;
import dev.goldberry.mic.MicrophoneException;
import dev.goldberry.mic.Microphones;
import dev.goldberry.mic.Recording;
import dev.goldberry.mic.RecordingSpec;
import dev.goldberry.mic.RecordingStatus;
import dev.goldberry.mic.analysis.Levels;
import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SampleSource;
import dev.goldberry.mic.source.Signal;
import dev.goldberry.mic.source.SyntheticSource;
import dev.goldberry.mic.view.LevelMeter;
import dev.goldberry.mic.view.SpectrumView;
import dev.goldberry.mic.view.WaveformView;
import dev.goldberry.mic.view.draw.WaveformPlot;
import dev.goldberry.motion.Clock;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.core.Column;

/// The **Microphone** card: a picker of the system's microphones and a test
/// tone, the three widgets over what the one chosen hears, and a throwaway
/// clip recorded from it and played back.
///
/// ```text
/// ┌───────────────────────────────────────┐
/// │ MICROPHONE                     Docs ↗ │
/// │ [System default      ▾]               │  the picker, listed on open
/// │ [List again] [Start] [Stop]           │
/// │ ▮▮▮▮▮▮▮▮▮▮▯▯▯▯▯▯▯▯▯▯▯▯▯▯▯▯▯▯          │  level-meter
/// │ ▁▃▅▇▅▃▁▁▃▅▇▅                          │  spectrum-view
/// │ ∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿∿               │  waveform-view
/// │ Status   recording                    │
/// │ [Record a clip] [Play clip] [Discard] │  the clip
/// │ ▂▅▇▅▂▁▂▅▇▅▂                           │  its outline, played part lit
/// └───────────────────────────────────────┘
/// ```
///
/// The system is asked for its microphones as the card mounts, and followed as
/// they come and go; nothing records until Start. The test tone is a
/// [SyntheticSource] advanced by a ticker, so the widgets move on a machine
/// with no microphone. A clip is kept in memory, at most
/// [AudioClipRecorder#LONGEST], and played from a temporary WAV file by a
/// `MediaPlayer` of the card's own. Leaving the screen closes everything the
/// card opened and deletes the file.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
///
/// @param microphones what opens the system's microphones: `Microphones::open`,
///                    or a stand-in in a test or a picture
public record MicrophoneCard(Supplier<Microphones> microphones) implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "capture-microphone";

    /// The picker's key for the test tone.
    static final String TONE = "tone";

    /// How much of the test tone each tick delivers: two blocks of ten
    /// milliseconds, about a frame and a bit.
    static final Duration TICK = Duration.ofMillis(20);

    /// How often the card reads a clip being recorded or played.
    static final Duration CLIP_TICK = Duration.ofMillis(100);

    /// How often the metrics are read again while a source runs.
    static final Duration METRICS_TICK = Duration.ofMillis(500);

    /// How many buckets a clip's outline has.
    static final int OUTLINE_BUCKETS = 96;

    /// What the test tone plays: concert pitch over a little noise, so the
    /// spectrum has a peak and a floor.
    static final Signal TEST_TONE =
            new Signal.Mix(List.of(new Signal.Tone(440, 0.35), new Signal.Noise(0.04, 0x6f6c64L)));

    /// The card over the system's own microphones.
    public MicrophoneCard() {
        this(Microphones::open);
    }

    @Override
    public State<?> createState() {
        return new MicrophoneState();
    }

    /// Where the card is: what it opened, what it recorded, and what it says.
    static final class MicrophoneState extends State<MicrophoneCard> {

        /// What the widgets read while nothing runs: a source that never
        /// delivers, kept so the widgets do not restart at every build.
        private final SampleSource idle = SyntheticSource.of(new Signal.Silence());

        private final Ticker ticker = new Ticker(TICK, this::advanceTone);
        private final Ticker clipTicker = new Ticker(CLIP_TICK, this::followClip);
        private final Ticker metricsTicker = new Ticker(METRICS_TICK, this::refresh);
        private RateMeter meter = RateMeter.overTwoSecondsOf(Clock.system());
        private volatile Levels lastLevels = Levels.SILENCE;
        private @Nullable AutoCloseable meterFeed;
        private DeviceChoice<Microphone> choice =
                new DeviceChoice<>(TONE, "Test tone (440 Hz)", List.of(), MicrophoneState::label);
        private @Nullable Microphone chosen;
        private @Nullable Microphones opened;
        private @Nullable AutoCloseable changes;
        private @Nullable Recording recording;
        private @Nullable AutoCloseable recordingStatus;
        private @Nullable SyntheticSource tone;
        private @Nullable Host host;
        private String message = "";

        private @Nullable AudioClipRecorder clipRecorder;
        private @Nullable AutoCloseable clipFeed;
        private @Nullable AudioClip clip;
        private @Nullable Path clipFile;
        private @Nullable MediaPlayer player;
        private @Nullable AutoCloseable playerStatus;
        private String clipMessage = "No clip yet. Start a source, then record a clip of up to "
                + AudioClipRecorder.LONGEST.toSeconds() + " s.";

        @Override
        protected void initState() {
            list();
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var source = source();
            var content = new ArrayList<Widget>();
            content.add(new Select(
                            choice.key(Optional.ofNullable(chosen)),
                            this::choose,
                            choice.options().toArray(Option[]::new))
                    .id("microphone-source"));
            content.add(MediaLines.actions(List.of(
                    new Button("List again", () -> setState(this::list)).id("microphone-list"),
                    new Button("Start", () -> setState(this::start))
                            .disabled(running())
                            .id("microphone-start"),
                    new Button("Stop", () -> setState(this::stop))
                            .disabled(!running())
                            .id("microphone-stop"))));
            content.add(new Column(
                    List.of(
                            new LevelMeter(source).id("microphone-level"),
                            new SpectrumView(source).id("microphone-spectrum"),
                            new WaveformView(source).id("microphone-waveform")),
                    Attributes.NONE.id("microphone-readings").classes("capture-readings")));
            content.add(MediaLines.line("Status", status()).id("microphone-status"));
            content.add(MediaLines.caption(message).id("microphone-note"));
            content.add(metricsSection());
            content.add(clipSection());
            return new ShowcaseCard(
                            ID,
                            "Microphone",
                            "A recording is a sample source, and level-meter, spectrum-view and waveform-view draw"
                                    + " any source. Pick a microphone, or the test tone where there is none, start it,"
                                    + " and record a throwaway clip to play back.",
                            CaptureDocs.MICROPHONE)
                    .of(content);
        }

        /// What the running source delivers, as numbers: the format and block
        /// size, the rate measured over the last two seconds against the one
        /// asked, how evenly blocks arrive, the level, and for a recording how
        /// much is buffered and how much was dropped.
        private Widget metricsSection() {
            var lines = metrics().stream()
                    .map(metric -> (Widget) MediaLines.line(metric.label(), metric.value()))
                    .toList();
            return new Column(lines, Attributes.NONE.id("microphone-metrics").classes("capture-metrics"));
        }

        /// The metrics as label and value, `–` for each while nothing runs.
        List<Metric> metrics() {
            if (!running()) {
                return Metric.idle("Format", "Rate", "Blocks", "Level", "Buffered");
            }
            var source = source();
            var format = source.format();
            var reading = meter.reading();
            var levels = lastLevels;
            var blockFrames = reading.events() == 0 ? 0 : reading.total() / reading.events();
            var live = recording;
            return List.of(
                    new Metric(
                            "Format",
                            describe(format) + ", blocks of " + blockFrames + " frames ("
                                    + Metric.millis(Duration.ofNanos(format.nanosOf(blockFrames))) + ")"),
                    new Metric(
                            "Rate",
                            String.format(
                                    Locale.ROOT,
                                    "%,.0f frames/s of %,d asked",
                                    reading.amountPerSecond(),
                                    format.sampleRate())),
                    new Metric(
                            "Blocks",
                            reading.events() + " heard, every " + Metric.millis(reading.meanInterval())
                                    + ", longest gap " + Metric.millis(reading.longestInterval())),
                    new Metric(
                            "Level",
                            String.format(
                                    Locale.ROOT,
                                    "peak %.1f dBFS, RMS %.1f dBFS",
                                    levels.peakDecibels(),
                                    levels.rmsDecibels())),
                    new Metric(
                            "Buffered",
                            live == null
                                    ? "none: the test tone is made as it is read"
                                    : Metric.millis(Duration.ofNanos(format.nanosOf(live.availableFrames())))
                                            + " waiting, " + live.droppedFrames() + " frames dropped"));
        }

        /// The clip's controls, its outline, and its player once it has played.
        private Widget clipSection() {
            var recordingClip = clipRecorder != null;
            var parts = new ArrayList<Widget>();
            parts.add(MediaLines.actions(List.of(
                    new Button("Record a clip", () -> setState(this::recordClip))
                            .disabled(!running() || recordingClip)
                            .id("microphone-clip-record"),
                    new Button("Stop recording", () -> setState(this::finishClip))
                            .disabled(!recordingClip)
                            .id("microphone-clip-stop"),
                    new Button("Play clip", () -> setState(this::playClip))
                            .disabled(clip == null || recordingClip)
                            .id("microphone-clip-play"),
                    new Button("Discard", () -> setState(this::discardClip))
                            .disabled(clip == null && !recordingClip)
                            .id("microphone-clip-discard"))));
            var kept = clip;
            if (kept != null) {
                parts.add(new WaveformPlot(kept.envelope(OUTLINE_BUCKETS), played(kept), false, Attributes.NONE)
                        .id("microphone-clip-outline"));
            }
            var playing = player;
            if (playing != null) {
                parts.add(new AudioPlayer(playing).id("microphone-clip-player"));
            }
            parts.add(MediaLines.line("Clip", clipLine()).id("microphone-clip"));
            parts.add(MediaLines.caption(clipMessage).id("microphone-clip-note"));
            return new Column(
                    parts, Attributes.NONE.id("microphone-clip-section").classes("capture-clip"));
        }

        @Override
        protected void dispose() {
            stop();
            discardClip();
            clipTicker.close();
            metricsTicker.close();
            Subscriptions.close(changes);
            changes = null;
            var devices = opened;
            opened = null;
            if (devices != null) {
                devices.close();
            }
            super.dispose();
        }

        /// What the widgets read now.
        SampleSource source() {
            var live = recording;
            if (live != null) {
                return live;
            }
            var synthetic = tone;
            return synthetic != null ? synthetic : idle;
        }

        /// Whether a recording or the test tone runs.
        boolean running() {
            return recording != null || tone != null;
        }

        /// Asks the system for its microphones, opening its audio the first
        /// time, and follows them coming and going from then on.
        void list() {
            var devices = opened;
            if (devices == null) {
                try {
                    devices = widget().microphones().get();
                } catch (MicrophoneException e) {
                    message = "No microphones listed: " + e.getMessage() + ". The test tone still plays.";
                    return;
                }
                opened = devices;
                changes = devices.onChange(change -> Goldberry.ui().execute(() -> heard(change)));
            }
            relist(devices);
            var count = choice.devices().size() - 1;
            message = count == 0
                    ? "The system lists no microphone; its default is offered all the same."
                    : "The system lists " + count + (count == 1 ? " microphone" : " microphones")
                            + ", after its default.";
        }

        /// Takes what the picker chose, and restarts on it when something runs.
        void choose(String key) {
            setState(() -> {
                chosen = choice.device(key).orElse(null);
                if (running()) {
                    start();
                }
            });
        }

        /// Starts what is chosen, stopping whatever ran.
        void start() {
            stop();
            var microphone = chosen;
            var devices = opened;
            if (microphone == null || devices == null) {
                tone = SyntheticSource.of(TEST_TONE);
                ticker.start(host);
                message = "Playing the test tone, made in Java and advanced every " + TICK.toMillis() + " ms.";
                meter();
                return;
            }
            try {
                var started = devices.record(microphone, RecordingSpec.voice());
                recording = started;
                recordingStatus = started.onStatus(_ -> Goldberry.ui().execute(this::refresh));
                message = "Recording " + microphone.name() + " in " + describe(started.format()) + ".";
                meter();
            } catch (MicrophoneException e) {
                message = "It did not open: " + e.getMessage();
            }
        }

        /// Starts measuring what the running source delivers, from nothing.
        private void meter() {
            var measured = RateMeter.overTwoSecondsOf(clock());
            meter = measured;
            lastLevels = Levels.SILENCE;
            meterFeed = source().onSamples(block -> {
                measured.record(block.frames());
                lastLevels = Levels.of(block);
            });
            metricsTicker.start(host);
        }

        /// Stops whatever runs, and a clip being recorded from it. Idempotent.
        void stop() {
            if (clipRecorder != null) {
                finishClip();
            }
            ticker.close();
            metricsTicker.close();
            Subscriptions.close(meterFeed);
            meterFeed = null;
            tone = null;
            Subscriptions.close(recordingStatus);
            recordingStatus = null;
            var live = recording;
            recording = null;
            if (live != null) {
                live.close();
                message = "Stopped.";
            }
        }

        /// Starts keeping what the running source delivers, dropping any clip
        /// kept before.
        void recordClip() {
            if (!running() || clipRecorder != null) {
                return;
            }
            discardClip();
            var source = source();
            var recorder = new AudioClipRecorder(source.format(), AudioClipRecorder.LONGEST);
            clipRecorder = recorder;
            clipFeed = source.onSamples(recorder);
            clipTicker.start(host);
            clipMessage = "Recording a clip of " + describe(source.format()) + ".";
        }

        /// Stops recording the clip, and keeps what it has.
        void finishClip() {
            var recorder = clipRecorder;
            if (recorder == null) {
                return;
            }
            Subscriptions.close(clipFeed);
            clipFeed = null;
            clipRecorder = null;
            clipTicker.close();
            var kept = recorder.clip();
            if (kept.frames() == 0) {
                clipMessage = "The clip heard nothing; record again while the source runs.";
                return;
            }
            clip = kept;
            clipMessage = (recorder.full() ? "The clip is full: " : "Kept ") + seconds(kept.duration())
                    + ". Play it, or record another.";
        }

        /// Plays the clip from a temporary WAV file, through a player of the
        /// card's own, made on the first play.
        void playClip() {
            var kept = clip;
            if (kept == null) {
                return;
            }
            try {
                var file = clipFile;
                if (file == null) {
                    file = Files.createTempFile("goldberry-capture-", ".wav");
                    file.toFile().deleteOnExit();
                    kept.writeWav(file);
                    clipFile = file;
                }
                var playing = player;
                if (playing == null) {
                    playing = MediaPlayer.builder().build();
                    player = playing;
                    playerStatus = playing.onStatus(_ -> Goldberry.ui().execute(this::refresh));
                }
                playing.open(Source.of(file));
                clipTicker.start(host);
                clipMessage = "Playing the clip through the default output.";
            } catch (IOException | RuntimeException e) {
                clipMessage = "It did not play: " + e.getMessage();
            }
        }

        /// Drops the clip, stops its player and deletes its file. Idempotent.
        void discardClip() {
            if (clipRecorder != null) {
                Subscriptions.close(clipFeed);
                clipFeed = null;
                clipRecorder = null;
            }
            clipTicker.close();
            Subscriptions.close(playerStatus);
            playerStatus = null;
            var playing = player;
            player = null;
            if (playing != null) {
                playing.close();
            }
            var file = clipFile;
            clipFile = null;
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    // A temporary file; deleteOnExit has it.
                }
            }
            if (clip != null) {
                clip = null;
                clipMessage = "Discarded.";
            }
        }

        /// What the status line says.
        String status() {
            var live = recording;
            if (live != null) {
                var said = switch (live.status()) {
                    case RecordingStatus.Active _ -> "recording";
                    case RecordingStatus.Paused _ -> "paused";
                    case RecordingStatus.Lost(var reason) -> "lost: " + reason;
                    case RecordingStatus.Closed _ -> "closed";
                };
                var dropped = live.droppedFrames();
                return dropped == 0 ? said : said + ", " + dropped + " frames dropped";
            }
            return tone != null ? "test tone" : "stopped";
        }

        /// What the clip line says.
        String clipLine() {
            var recorder = clipRecorder;
            if (recorder != null) {
                return "recording, " + seconds(recorder.recorded());
            }
            var kept = clip;
            return kept == null ? "–" : seconds(kept.duration()) + ", " + describe(kept.format());
        }

        /// What the card says now, for a test.
        String message() {
            return message;
        }

        /// What the card says about its clip, for a test.
        String clipMessage() {
            return clipMessage;
        }

        /// The clip kept, or null.
        @Nullable
        AudioClip clip() {
            return clip;
        }

        /// The player the clip plays through, once it has played, or null.
        @Nullable
        MediaPlayer player() {
            return player;
        }

        /// The temporary file the clip was written to for playing, or null.
        @Nullable
        Path clipFile() {
            return clipFile;
        }

        /// The devices the picker offers now.
        List<Microphone> devices() {
            return choice.devices();
        }

        /// How much of `kept` has played: all of it while nothing plays.
        private double played(AudioClip kept) {
            var playing = player;
            if (playing == null) {
                return 1;
            }
            var status = playing.status();
            if (status.state() == PlaybackState.ENDED) {
                return 1;
            }
            var total = kept.duration().toNanos();
            return total == 0 ? 1 : Math.clamp((double) status.position().toNanos() / total, 0, 1);
        }

        private void heard(MicrophoneChange change) {
            var devices = opened;
            if (!isMounted() || devices == null) {
                return;
            }
            setState(() -> {
                relist(devices);
                message = switch (change) {
                    case MicrophoneChange.Added(var microphone) -> "Plugged in: " + microphone.name() + ".";
                    case MicrophoneChange.Removed(var microphone) -> "Taken away: " + microphone.name() + ".";
                };
            });
        }

        /// The system's default first, then every device, and a chosen one
        /// that went away no longer chosen.
        private void relist(Microphones devices) {
            var listed = new ArrayList<Microphone>();
            listed.add(devices.systemDefault());
            listed.addAll(devices.list());
            choice = choice.listing(listed);
            if (chosen != null && !listed.contains(chosen) && recording == null) {
                chosen = null;
            }
        }

        private void advanceTone() {
            var synthetic = tone;
            if (synthetic != null && isMounted()) {
                synthetic.advance(ticker.interval());
            }
        }

        /// Reads the clip being recorded or played: finishes one that filled,
        /// and stops following a player that ended.
        private void followClip() {
            if (!isMounted()) {
                return;
            }
            setState(() -> {
                var recorder = clipRecorder;
                if (recorder != null && recorder.full()) {
                    finishClip();
                }
                if (recorder == null && !playerMoving()) {
                    clipTicker.close();
                }
            });
        }

        /// The window's clock, which the metrics are measured against: the
        /// system's in a window, the test's in a test.
        private Clock clock() {
            var window = host;
            return window == null ? Clock.system() : window.clock();
        }

        /// Rebuilds, and follows the clip again when its player was started
        /// from its own controls.
        private void refresh() {
            if (isMounted()) {
                setState(() -> {
                    if (playerMoving()) {
                        clipTicker.start(host);
                    }
                });
            }
        }

        /// Whether the clip's player is opening or playing.
        private boolean playerMoving() {
            var playing = player;
            return playing != null
                    && switch (playing.status().state()) {
                        case OPENING, BUFFERING, PLAYING -> true;
                        case IDLE, PAUSED, ENDED, ERROR -> false;
                    };
        }

        private static String label(Microphone microphone) {
            return microphone.isSystemDefault() ? "System default" : microphone.name();
        }

        static String describe(SampleFormat format) {
            return String.format(Locale.ROOT, "%,d Hz", format.sampleRate()) + ", "
                    + (format.channels() == 1 ? "mono" : format.channels() + " channels");
        }

        static String seconds(Duration duration) {
            return String.format(Locale.ROOT, "%.1f s", duration.toMillis() / 1000.0);
        }
    }
}
