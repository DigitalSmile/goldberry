package dev.goldberry.example.ui.capture;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.Host;
import dev.goldberry.camera.Camera;
import dev.goldberry.camera.CameraChange;
import dev.goldberry.camera.CameraException;
import dev.goldberry.camera.CameraFormat;
import dev.goldberry.camera.CameraInfo;
import dev.goldberry.camera.CameraPosition;
import dev.goldberry.camera.CameraSpec;
import dev.goldberry.camera.CameraStatus;
import dev.goldberry.camera.Cameras;
import dev.goldberry.camera.synthetic.SyntheticCamera;
import dev.goldberry.camera.view.CameraView;
import dev.goldberry.camera.view.Mirror;
import dev.goldberry.example.capture.PictureCopies;
import dev.goldberry.example.capture.RateMeter;
import dev.goldberry.example.capture.VideoClip;
import dev.goldberry.example.capture.VideoClipRecorder;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.media.MediaLines;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.PushedPictures;
import dev.goldberry.media.view.PictureView;
import dev.goldberry.motion.Clock;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.image.Fit;

/// The **Camera** card: a picker of the system's cameras and a test card, the
/// modes the one chosen offers, a `camera-view` of it, and a throwaway clip
/// recorded from it and played back.
///
/// ```text
/// ┌───────────────────────────────────────┐
/// │ CAMERA                         Docs ↗ │
/// │ [C922 Pro Stream    ▾] [Mirror ▾]     │  the camera, listed on open
/// │ [Best for a call: 1280×720 at 30 ▾]   │  its modes
/// │ [List again] [Start] [Stop]           │
/// │ ┌───────────────────────────────────┐ │
/// │ │ camera-view                       │ │
/// │ └───────────────────────────────────┘ │
/// │ Camera   C922 Pro Stream, unstated    │
/// │ Modes    218: 40 uncompressed, 178 …  │
/// │ Open     1280×720 at 30 fps, compr…   │
/// │ [Record a clip] [Play clip] [Discard] │  the clip
/// │ ┌───────────────────────────────────┐ │
/// │ │ picture-view of the clip          │ │
/// │ └───────────────────────────────────┘ │
/// └───────────────────────────────────────┘
/// ```
///
/// The cameras are listed as the card mounts; nothing opens until Start. A
/// camera opens in the mode picked, or for a call ([CameraSpec#CALL]) when
/// none is. The test card is a [SyntheticCamera] advanced by a ticker, in a
/// choice of two sizes. A clip is the pictures themselves, copied as they
/// show (there is no encoder), at most [VideoClipRecorder#LONGEST] and
/// [VideoClipRecorder#BUDGET] bytes, and played back unmirrored, as a
/// recording is. Leaving the screen closes the camera and drops the clip.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
///
/// @param cameras what opens the system's cameras: `Cameras::open`, or a
///                stand-in in a test or a picture
public record CameraCard(Supplier<Cameras> cameras) implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "capture-camera";

    /// The picker's key for the test card.
    static final String TEST_CARD = "test-card";

    /// The mode picker's key for the mode nearest a call's.
    static final String FOR_A_CALL = "call";

    /// What a mode's key starts with.
    static final String MODE = "mode-";

    /// How often the test card takes a picture, and a clip plays one: about
    /// 30 fps.
    static final Duration TICK = Duration.ofMillis(33);

    /// The test card's modes: two sizes at 30 fps.
    static final CameraInfo TEST_INFO = new CameraInfo(
            0,
            "Test card",
            CameraPosition.FRONT,
            List.of(CameraFormat.of(640, 360, 30), CameraFormat.of(1280, 720, 30)));

    /// The card over the system's own cameras.
    public CameraCard() {
        this(Cameras::open);
    }

    @Override
    public State<?> createState() {
        return new CameraState();
    }

    /// Where the card is: what it opened, what it recorded, and what it says.
    static final class CameraState extends State<CameraCard> {

        private final Ticker ticker = new Ticker(TICK, this::advanceTestCard);
        private final Ticker playTicker = new Ticker(TICK, this::advancePlayback);
        private final Ticker clipTicker = new Ticker(Duration.ofMillis(100), this::followClip);
        private final Ticker metricsTicker = new Ticker(MicrophoneCard.METRICS_TICK, this::refresh);
        private RateMeter meter = RateMeter.overTwoSecondsOf(Clock.system());
        private volatile @Nullable Picture lastPicture;
        private @Nullable AutoCloseable meterFeed;
        private DeviceChoice<CameraInfo> choice =
                new DeviceChoice<>(TEST_CARD, "Test card (synthetic)", List.of(), CameraState::label);
        private @Nullable CameraInfo chosen;
        private @Nullable CameraFormat mode;
        private Mirror mirror = Mirror.AUTO;
        private @Nullable Cameras opened;
        private @Nullable AutoCloseable changes;
        private @Nullable Camera camera;
        private @Nullable AutoCloseable cameraStatus;
        private @Nullable Host host;
        private String message = "";

        private @Nullable VideoClipRecorder clipRecorder;
        private @Nullable VideoClip clip;
        private final PushedPictures playback = new PushedPictures();
        private long playhead;
        private boolean playing;
        private String clipMessage = "No clip yet. Start a camera, then record a clip of up to "
                + VideoClipRecorder.LONGEST.toSeconds() + " s.";

        @Override
        protected void initState() {
            list();
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var content = new ArrayList<Widget>();
            content.add(MediaLines.actions(List.of(
                    new Select(
                                    choice.key(Optional.ofNullable(chosen)),
                                    this::choose,
                                    choice.options().toArray(Option[]::new))
                            .id("camera-source"),
                    new Select(
                                    mirror.name().toLowerCase(Locale.ROOT),
                                    this::mirror,
                                    new Option("auto", "Mirror unless it faces away"),
                                    new Option("on", "Mirror"),
                                    new Option("off", "Do not mirror"))
                            .id("camera-mirror"))));
            content.add(new Select(modeKey(), this::pickMode, modeOptions().toArray(Option[]::new))
                    .id("camera-mode-picker"));
            content.add(MediaLines.actions(List.of(
                    new Button("List again", () -> setState(this::list)).id("camera-list"),
                    new Button("Start", () -> setState(this::start))
                            .disabled(camera != null)
                            .id("camera-start"),
                    new Button("Stop", () -> setState(this::stop))
                            .disabled(camera == null)
                            .id("camera-stop"))));
            content.add(new CameraView(camera, Fit.CONTAIN, mirror, Attributes.NONE).id("camera-picture"));
            content.add(MediaLines.line("Camera", describe(info())).id("camera-info"));
            content.add(MediaLines.line("Modes", modesSummary(info())).id("camera-modes"));
            content.add(MediaLines.line("Driver", driverLine()).id("camera-driver"));
            content.add(MediaLines.line("Open", mode()).id("camera-mode"));
            content.add(MediaLines.line("Status", status()).id("camera-status"));
            content.add(MediaLines.caption(message).id("camera-note"));
            content.add(metricsSection());
            content.add(clipSection());
            return new ShowcaseCard(
                            ID,
                            "Camera",
                            "A camera is a picture source, and camera-view draws one with what to say while there"
                                    + " is no picture. Pick a camera and a mode, or the test card where there is"
                                    + " none, start it, and record a throwaway clip to play back.",
                            CaptureDocs.CAMERA)
                    .of(content);
        }

        /// What the open camera delivers, as numbers: the rate measured over the
        /// last two seconds against the mode's, how evenly pictures arrive, what a
        /// picture is, the bytes a second, and how many frames were dropped.
        private Widget metricsSection() {
            var lines = metrics().stream()
                    .map(metric -> (Widget) MediaLines.line(metric.label(), metric.value()))
                    .toList();
            return new Column(lines, Attributes.NONE.id("camera-metrics").classes("capture-metrics"));
        }

        /// The metrics as label and value, `–` for each while no camera is open.
        List<Metric> metrics() {
            var open = camera;
            if (open == null) {
                return Metric.idle("Rate", "Pictures", "Picture", "Throughput", "Dropped");
            }
            var reading = meter.reading();
            var picture = lastPicture;
            return List.of(
                    new Metric(
                            "Rate",
                            String.format(
                                    Locale.ROOT,
                                    "%.1f pictures/s of %.1f in the mode",
                                    reading.eventsPerSecond(),
                                    open.format().framesPerSecond())),
                    new Metric(
                            "Pictures",
                            reading.events() + " shown, every " + Metric.millis(reading.meanInterval())
                                    + ", longest gap " + Metric.millis(reading.longestInterval())),
                    new Metric(
                            "Picture",
                            picture == null
                                    ? "none yet"
                                    : picture.width() + "×" + picture.height() + " " + PictureCopies.kind(picture)
                                            + (open.format().compressed() ? ", decoded from Motion JPEG" : "")),
                    new Metric(
                            "Throughput",
                            String.format(Locale.ROOT, "%.1f MB/s of pictures", reading.amountPerSecond() / (1 << 20))),
                    new Metric("Dropped", open.droppedFrames() + " frames that did not decode"));
        }

        /// The clip's controls, and its pictures once it has some.
        private Widget clipSection() {
            var recordingClip = clipRecorder != null;
            var parts = new ArrayList<Widget>();
            parts.add(MediaLines.actions(List.of(
                    new Button("Record a clip", () -> setState(this::recordClip))
                            .disabled(camera == null || recordingClip)
                            .id("camera-clip-record"),
                    new Button("Stop recording", () -> setState(this::finishClip))
                            .disabled(!recordingClip)
                            .id("camera-clip-stop"),
                    new Button(playing ? "Stop playing" : "Play clip", () -> setState(this::togglePlayback))
                            .disabled(clip == null || recordingClip)
                            .id("camera-clip-play"),
                    new Button("Discard", () -> setState(this::discardClip))
                            .disabled(clip == null && !recordingClip)
                            .id("camera-clip-discard"))));
            if (clip != null) {
                parts.add(new PictureView(playback, Fit.CONTAIN, Attributes.NONE.id("camera-clip-picture")));
            }
            parts.add(MediaLines.line("Clip", clipLine()).id("camera-clip"));
            parts.add(MediaLines.caption(clipMessage).id("camera-clip-note"));
            return new Column(parts, Attributes.NONE.id("camera-clip-section").classes("capture-clip"));
        }

        @Override
        protected void dispose() {
            stop();
            discardClip();
            playTicker.close();
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

        /// The camera open now, or null.
        @Nullable
        Camera camera() {
            return camera;
        }

        /// How the view mirrors.
        Mirror mirroring() {
            return mirror;
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
        VideoClip clip() {
            return clip;
        }

        /// What the clip's view shows.
        PushedPictures playback() {
            return playback;
        }

        /// The cameras the picker offers now.
        List<CameraInfo> devices() {
            return choice.devices();
        }

        /// The camera picked: a device, or the test card.
        CameraInfo info() {
            var picked = chosen;
            return picked != null ? picked : TEST_INFO;
        }

        /// Asks the system for its cameras, and follows them from then on.
        void list() {
            var devices = opened;
            if (devices == null) {
                try {
                    devices = widget().cameras().get();
                } catch (CameraException e) {
                    message = "No cameras listed: " + e.getMessage() + ". The test card still shows.";
                    return;
                }
                opened = devices;
                changes = devices.onChange(change -> Goldberry.ui().execute(() -> heard(change)));
            }
            relist(devices);
            var count = choice.devices().size();
            message = count == 0
                    ? "The system lists no camera."
                    : "The system lists " + count + (count == 1 ? " camera." : " cameras.");
        }

        /// What the driver line says: the system driver a device is reached
        /// through, as SDL names it, or that the test card needs none.
        ///
        /// Shown because a camera's name can mislead: through PipeWire a USB
        /// camera is called `… (V4L2)` after the PipeWire plugin that serves
        /// it, while the driver is PipeWire.
        String driverLine() {
            if (chosen == null) {
                return "none: the test card is painted in Java";
            }
            var devices = opened;
            var driver = devices == null ? null : devices.driver().orElse(null);
            return driver == null ? "not named by the system" : driver + describeDriver(driver);
        }

        /// A word on `driver`, for the driver line.
        static String describeDriver(String driver) {
            return switch (driver) {
                case "pipewire" -> " (the session's media server; a USB camera's name ends in the plugin serving it)";
                case "v4l2" -> " (the kernel's video devices, directly)";
                case "mediafoundation" -> " (Windows' Media Foundation)";
                case "coremedia" -> " (macOS' AVFoundation)";
                default -> "";
            };
        }

        /// Takes what the picker chose, back to the mode for a call, and
        /// restarts on it when a camera runs.
        void choose(String key) {
            setState(() -> {
                chosen = choice.device(key).orElse(null);
                mode = null;
                if (camera != null) {
                    start();
                }
            });
        }

        /// Takes the mode the picker chose, and reopens in it when a camera
        /// runs.
        void pickMode(String key) {
            setState(() -> {
                mode = modeFor(info(), key).orElse(null);
                if (camera != null) {
                    start();
                }
            });
        }

        /// Takes the mirror the picker chose.
        void mirror(String key) {
            setState(() -> mirror = Mirror.named(key));
        }

        /// Opens what is chosen in the mode chosen, closing whatever was open.
        void start() {
            stop();
            var info = chosen;
            var devices = opened;
            Camera started;
            if (info == null || devices == null) {
                var format = mode != null ? mode : TEST_INFO.formats().getFirst();
                started = new SyntheticCamera(TEST_INFO, format, new CameraStatus.Active());
                ticker.start(host);
                message = "Showing the test card, painted in Java every " + TICK.toMillis() + " ms.";
            } else {
                var picked = mode;
                try {
                    started = picked != null ? devices.open(info, picked) : devices.open(info, CameraSpec.CALL);
                } catch (CameraException | IllegalArgumentException e) {
                    message = "It did not open: " + e.getMessage();
                    return;
                }
                message = "Opened " + info.name() + (picked != null ? " in the mode picked." : " for a call.");
            }
            camera = started;
            cameraStatus = started.onStatus(_ -> Goldberry.ui().execute(this::refresh));
            var measured = RateMeter.overTwoSecondsOf(clock());
            meter = measured;
            lastPicture = null;
            meterFeed = started.onPicture(() -> started.shownPicture().ifPresent(picture -> {
                lastPicture = picture;
                measured.record(PictureCopies.bytes(picture));
            }));
            metricsTicker.start(host);
        }

        /// Closes the camera, and a clip being recorded from it. Idempotent.
        void stop() {
            if (clipRecorder != null) {
                finishClip();
            }
            ticker.close();
            metricsTicker.close();
            Subscriptions.close(meterFeed);
            meterFeed = null;
            Subscriptions.close(cameraStatus);
            cameraStatus = null;
            var open = camera;
            camera = null;
            if (open != null) {
                open.close();
                message = "Stopped.";
            }
        }

        /// Starts keeping the camera's pictures, dropping any clip kept before.
        void recordClip() {
            var open = camera;
            if (open == null || clipRecorder != null) {
                return;
            }
            discardClip();
            clipRecorder = new VideoClipRecorder(open, VideoClipRecorder.LONGEST, VideoClipRecorder.BUDGET);
            clipTicker.start(host);
            clipMessage = "Recording a clip of " + open.format() + ".";
        }

        /// Stops recording the clip, and keeps what it has.
        void finishClip() {
            var recorder = clipRecorder;
            if (recorder == null) {
                return;
            }
            recorder.close();
            clipRecorder = null;
            clipTicker.close();
            var kept = recorder.clip();
            if (kept.isEmpty()) {
                clipMessage = "The clip took no picture; record again while the camera shows one.";
                return;
            }
            clip = kept;
            playback.push(kept.pictures().getFirst().picture());
            clipMessage = (recorder.full() ? "The clip is full. " : "Kept. ") + "Play it, or record another.";
        }

        /// Plays the clip from its start, or stops it where it is.
        void togglePlayback() {
            var kept = clip;
            if (kept == null) {
                return;
            }
            if (playing) {
                playing = false;
                playTicker.close();
                clipMessage = "Stopped playing.";
                return;
            }
            playhead = 0;
            playing = true;
            playback.push(kept.pictures().getFirst().picture());
            playTicker.start(host);
            clipMessage = "Playing the clip, unmirrored, as a recording is.";
        }

        /// Drops the clip and what its view shows. Idempotent.
        void discardClip() {
            var recorder = clipRecorder;
            clipRecorder = null;
            if (recorder != null) {
                recorder.close();
            }
            clipTicker.close();
            playTicker.close();
            playing = false;
            playback.clear();
            if (clip != null) {
                clip = null;
                clipMessage = "Discarded.";
            }
        }

        /// What the open-mode line says.
        String mode() {
            var open = camera;
            return open == null ? "–" : open.format().toString();
        }

        /// What the status line says.
        String status() {
            var open = camera;
            if (open == null) {
                return "stopped";
            }
            var said = switch (open.status()) {
                case CameraStatus.Asking _ -> "asking the user";
                case CameraStatus.Active _ -> "active";
                case CameraStatus.Denied _ -> "denied";
                case CameraStatus.Lost(var reason) -> "lost: " + reason;
                case CameraStatus.Closed _ -> "closed";
            };
            var dropped = open.droppedFrames();
            return dropped == 0 ? said : said + ", " + dropped + " frames dropped";
        }

        /// What the clip line says.
        String clipLine() {
            var recorder = clipRecorder;
            if (recorder != null) {
                return "recording, " + recorder.pictures() + " pictures";
            }
            var kept = clip;
            if (kept == null) {
                return "–";
            }
            var first = kept.pictures().getFirst().picture();
            return MicrophoneCard.MicrophoneState.seconds(kept.duration()) + ", "
                    + kept.pictures().size()
                    + " pictures of " + first.width() + "×" + first.height() + ", "
                    + (kept.bytes() >> 20) + " MB";
        }

        /// The mode picker's key now.
        String modeKey() {
            var picked = mode;
            if (picked == null) {
                return FOR_A_CALL;
            }
            var at = info().formats().indexOf(picked);
            return at < 0 ? FOR_A_CALL : MODE + at;
        }

        /// The mode picker's options: the mode for a call, then every mode the
        /// camera offers, in the system's order.
        List<Option> modeOptions() {
            var info = info();
            var options = new ArrayList<Option>();
            var call = info == TEST_INFO
                    ? Optional.of(TEST_INFO.formats().getFirst())
                    : CameraSpec.CALL.choose(info.formats());
            options.add(new Option(
                    FOR_A_CALL,
                    info == TEST_INFO
                            ? "Default: " + TEST_INFO.formats().getFirst()
                            : "Best for a call"
                                    + call.map(format -> ": " + format).orElse("")));
            for (var at = 0; at < info.formats().size(); at++) {
                options.add(new Option(MODE + at, info.formats().get(at).toString()));
            }
            return List.copyOf(options);
        }

        /// The mode `key` names among `info`'s, or empty for the mode for a
        /// call and a key that names none.
        static Optional<CameraFormat> modeFor(CameraInfo info, String key) {
            if (!key.startsWith(MODE)) {
                return Optional.empty();
            }
            try {
                var at = Integer.parseInt(key.substring(MODE.length()));
                return at >= 0 && at < info.formats().size()
                        ? Optional.of(info.formats().get(at))
                        : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        /// A camera's name and where it faces.
        static String describe(CameraInfo info) {
            return info.name() + ", "
                    + switch (info.position()) {
                        case FRONT -> "faces the user";
                        case BACK -> "faces away";
                        case UNKNOWN -> "facing unstated";
                    };
        }

        /// How many modes a camera offers, and how many of them are
        /// uncompressed, the rest being Motion JPEG.
        static String modesSummary(CameraInfo info) {
            var all = info.formats().size();
            var uncompressed = info.uncompressed().size();
            if (all == 0) {
                return "none";
            }
            var largest = info.formats().stream()
                    .max(Comparator.comparingLong(CameraFormat::pixels))
                    .orElseThrow();
            return all + ": " + uncompressed + " uncompressed, " + (all - uncompressed) + " Motion JPEG; up to "
                    + largest.width() + "×" + largest.height();
        }

        private void heard(CameraChange change) {
            var devices = opened;
            if (!isMounted() || devices == null) {
                return;
            }
            setState(() -> {
                relist(devices);
                message = switch (change) {
                    case CameraChange.Added(var info) -> "Plugged in: " + info.name() + ".";
                    case CameraChange.Removed(var info) -> "Taken away: " + info.name() + ".";
                };
            });
        }

        private void relist(Cameras devices) {
            choice = choice.listing(devices.list());
            if (chosen != null && !choice.devices().contains(chosen) && camera == null) {
                chosen = null;
                mode = null;
            }
        }

        private void advanceTestCard() {
            if (camera instanceof SyntheticCamera synthetic && isMounted()) {
                synthetic.advance(ticker.interval());
            }
        }

        /// Moves the playhead a tick on and shows the picture it lands in;
        /// stops on the last picture.
        private void advancePlayback() {
            var kept = clip;
            if (kept == null || !playing || !isMounted()) {
                playTicker.close();
                return;
            }
            playhead += playTicker.interval().toNanos();
            playback.push(kept.pictures().get(kept.indexAt(playhead)).picture());
            if (playhead >= kept.duration().toNanos()) {
                setState(() -> {
                    playing = false;
                    playTicker.close();
                    clipMessage = "Played to the end.";
                });
            }
        }

        /// Reads the clip being recorded: finishes one that filled.
        private void followClip() {
            if (!isMounted()) {
                return;
            }
            setState(() -> {
                var recorder = clipRecorder;
                if (recorder != null && recorder.full()) {
                    finishClip();
                }
            });
        }

        /// The window's clock, which the metrics are measured against: the
        /// system's in a window, the test's in a test.
        private Clock clock() {
            var window = host;
            return window == null ? Clock.system() : window.clock();
        }

        private void refresh() {
            if (isMounted()) {
                setState(() -> {});
            }
        }

        private static String label(CameraInfo info) {
            return switch (info.position()) {
                case FRONT -> info.name() + " (front)";
                case BACK -> info.name() + " (back)";
                case UNKNOWN -> info.name();
            };
        }
    }
}
