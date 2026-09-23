package io.github.digitalsmile.goldberry.example.ui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.media.MediaCapabilities;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.PlaybackState;
import io.github.digitalsmile.goldberry.media.PlayerStatus;
import io.github.digitalsmile.goldberry.media.Track;
import io.github.digitalsmile.goldberry.media.codec.TrackParams;
import io.github.digitalsmile.goldberry.media.io.Source;
import io.github.digitalsmile.goldberry.media.view.MediaTime;
import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogSpec;
import io.github.digitalsmile.goldberry.render.dialog.FileFilter;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.controls.select.Select;
import io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Audio** and **Video** screens: `goldberry-media`'s widgets, and everything
/// around them that an application can reach. One screen written twice over by
/// its [Kind], because the two show the same engine from two sides and the cards
/// that drive and report on it are the same.
///
/// Each screen has a player of its own, from a markup document naming a
/// `MediaPlayer` the model registered: `audio.kdl`'s `audio-player` and
/// `video.kdl`'s `media-player`. The cards under it are the Java side: picking a
/// source (bundled clips, a file from disk, and the failures), driving the player
/// from code, reading its status and the tracks the probe found, and asking what
/// this build decodes. The Audio screen adds a live stream and a decoder this
/// application wrote itself.
///
/// FFmpeg may be absent: the natives are a separate build (`docs/media-plan.md`).
/// Then the Capabilities card says why, and picking a source says the same.
///
/// @param kind        which of the two screens this is
/// @param player      the screen's own player
/// @param javaDecoder the application's decoder and its switch, for the Audio
///                    screen; null for the Video screen, which has no card for it
/// @param playerPane  the player widget, inflated from the screen's document
public record MediaScreen(
        Kind kind, MediaPlayer player, @Nullable JavaPcmDecoder javaDecoder, Widget playerPane)
        implements Widget.Stateful {

    /// Which screen: what it is called, what it says, and what it offers to play.
    ///
    /// @param id         the screen's name in the gallery, and the prefix of every
    ///                   id on it
    /// @param title      its heading
    /// @param note       the paragraph under the heading
    /// @param filterName what its file dialog calls the files it offers
    public enum Kind {
        AUDIO(
                "audio",
                "Audio",
                "Audio from `goldberry-media`: FFmpeg's demuxers and royalty-free decoders, driven from Java."
                        + " Every byte reaches FFmpeg through a Java stream, so the bundled clips, the live stream"
                        + " and a file from disk are the same code path, and the engine's threads, clock and"
                        + " seeking are Java too. The player above is one markup node, `audio-player`, naming a"
                        + " MediaPlayer; click it for the keys (Space, the arrows, M). The cards drive that same"
                        + " player from code, show what it reports, and include a decoder written in this"
                        + " application. Pick an error case too: bytes that are not media, and a file that is not"
                        + " there.",
                "Audio"),
        VIDEO(
                "video",
                "Video",
                "Video from `goldberry-media`: VP8, VP9 and AV1, decoded by FFmpeg and dav1d, converted to the"
                        + " toolkit's pixels as they are decoded, and timed by the audio clock, or by a"
                        + " free-running clock when there is no sound. The player above is one markup node,"
                        + " `media-player`: the picture with its controls over it, which fade while it plays and"
                        + " come back when the pointer moves. Click the picture to pause, or click it and use the"
                        + " keys. Drag the seek bar: it shows keyframes while held and the exact picture on"
                        + " release. Pick the H.264 file too: it opens, lists its tracks, and says which codecs"
                        + " it could not play.",
                "Video");

        private final String id;
        private final String title;
        private final String note;
        private final String filterName;

        Kind(String id, String title, String note, String filterName) {
            this.id = id;
            this.title = title;
            this.note = note;
            this.filterName = filterName;
        }

        /// The screen's name in the gallery, and the prefix of every id on it.
        public String id() {
            return id;
        }

        /// The paragraph under the heading.
        public String note() {
            return note;
        }

        /// The bundled sources the picker lists.
        public List<ShowcaseMedia.Sample> samples() {
            return switch (this) {
                case AUDIO -> ShowcaseMedia.AUDIO_SAMPLES;
                case VIDEO -> ShowcaseMedia.VIDEO_SAMPLES;
            };
        }

        /// The extensions the file dialog offers.
        String[] extensions() {
            return switch (this) {
                case AUDIO -> new String[] {"opus", "ogg", "oga", "mp3", "flac", "wav", "mka", "m4a", "webm"};
                case VIDEO -> new String[] {"webm", "mkv", "mp4", "mov"};
            };
        }
    }

    @Override
    public State<?> createState() {
        return new MediaState();
    }

    static final class MediaState extends State<MediaScreen> {

        /// How often the Status card reads the position while playing, as the
        /// player widget does.
        private static final Duration POSITION_INTERVAL = Duration.ofMillis(250);

        private @Nullable AutoCloseable subscription;
        private EventLoop.@Nullable Timer poll;
        private @Nullable Host host;
        private String chosen = "";
        private String message = "Nothing opened yet. Pick a source; it starts playing.";
        private @Nullable MediaCapabilities capabilities;

        @Override
        protected void initState() {
            subscription = widget().player().onStatus(_ -> Goldberry.ui().execute(this::refresh));
            try {
                capabilities = MediaCapabilities.current();
            } catch (MediaException e) {
                // Said by the card and, with the loader's reason, when a source is
                // picked. The reason names a path, which a screenshot must not.
                capabilities = null;
            }
        }

        @Override
        protected void dispose() {
            var current = subscription;
            subscription = null;
            if (current != null) {
                try {
                    current.close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            if (poll != null) {
                poll.cancel();
                poll = null;
            }
            super.dispose();
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var status = widget().player().status();
            schedulePoll(status);
            var kind = widget().kind();
            var cards = new ArrayList<Widget>(
                    List.of(sources(), control(status), statusCard(status), tracks(status), capabilitiesCard()));
            var decoder = widget().javaDecoder();
            if (decoder != null) {
                cards.add(javaDecoderCard(decoder));
            }
            return new Column(
                    List.of(
                            new SectionHeader(kind.title),
                            new Text(kind.note(), Attributes.NONE.classes("prose")),
                            new Card(
                                    List.of(widget().playerPane()),
                                    Attributes.NONE.id(id("player-card")).classes("wall-card", "media-card")),
                            new Masonry(
                                    cards,
                                    2,
                                    Masonry.UNSET,
                                    Attributes.NONE.id(id("wall")).classes("wall"))),
                    Attributes.NONE.id("screen-" + kind.id()).classes("screen"));
        }

        // ---------------------------------------------------------------- cards

        private Widget sources() {
            var options = new ArrayList<Option>();
            for (var sample : widget().kind().samples()) {
                options.add(new Option(sample.key(), sample.title()));
            }
            var dialogs = host != null && host.fileDialogs().supported();
            return card(
                    id("sources"),
                    "Sources",
                    new Select(chosen, this::open, options.toArray(Option[]::new))
                            .placeholder("Choose a source…")
                            .id(id("source")),
                    new Row(
                            List.of(
                                    new Button("Open a file…", this::openFile)
                                            .disabled(!dialogs)
                                            .id(id("open-file")),
                                    new Button("Close", this::closeSource).id(id("close"))),
                            Attributes.NONE.classes("media-actions")),
                    caption(message).id(id("source-note")));
        }

        private Widget control(PlayerStatus status) {
            var player = widget().player();
            var seekable = status.seekable();
            var duration = status.duration().orElse(Duration.ZERO);
            return card(
                    id("control"),
                    "Driven from Java",
                    new Row(
                            List.of(
                                    new Button("Play", player::play).id(id("java-play")),
                                    new Button("Pause", player::pause).id(id("java-pause")),
                                    new Button("From the top", () -> player.seek(Duration.ZERO))
                                            .disabled(!seekable)
                                            .id(id("java-restart"))),
                            Attributes.NONE.classes("media-actions")),
                    new Row(
                            List.of(
                                    seekTo("25%", duration, 0.25, seekable),
                                    seekTo("50%", duration, 0.5, seekable),
                                    seekTo("75%", duration, 0.75, seekable)),
                            Attributes.NONE.classes("media-actions")),
                    new Row(
                            List.of(
                                    new Button(
                                                    status.muted() ? "Unmute" : "Mute",
                                                    () -> player.setMuted(
                                                            !player.status().muted()))
                                            .id(id("java-mute")),
                                    new Button("Volume 25%", () -> player.setVolume(0.25f)),
                                    new Button("50%", () -> player.setVolume(0.5f)),
                                    new Button("100%", () -> player.setVolume(1f))),
                            Attributes.NONE.classes("media-actions")),
                    caption("The same MediaPlayer the widget above drives. Every call returns at once: the"
                            + " engine runs on its own threads and reports back as a status."));
        }

        private Widget statusCard(PlayerStatus status) {
            var position = MediaTime.format(status.position())
                    + status.duration().map(d -> " of " + MediaTime.format(d)).orElse("");
            var lines = new ArrayList<Widget>();
            lines.add(line("State", status.state().name().toLowerCase(Locale.ROOT)));
            lines.add(line("Position", position));
            lines.add(line("Audio", status.audioDecoder().orElse("—")));
            lines.add(line("Video", status.videoDecoder().orElse("—")));
            lines.add(line("Seekable", status.state().hasMedia() ? (status.seekable() ? "yes" : "no: live") : "—"));
            lines.add(line("Volume", Math.round(status.volume() * 100) + "%" + (status.muted() ? ", muted" : "")));
            status.error().ifPresent(error -> lines.add(line("Error", error.message())));
            lines.add(caption("One immutable PlayerStatus: pushed when the state changes, and read four times a"
                    + " second for the position while playing."));
            return card(id("status"), "Status", lines.toArray(Widget[]::new));
        }

        private Widget tracks(PlayerStatus status) {
            var lines = new ArrayList<Widget>();
            status.info()
                    .ifPresentOrElse(
                            info -> {
                                for (var track : info.tracks()) {
                                    lines.add(new Text(describe(track), Attributes.NONE.classes("mono")));
                                }
                                if (info.tracks().isEmpty()) {
                                    lines.add(caption("The container holds no tracks."));
                                }
                            },
                            () -> lines.add(caption("Nothing open. The probe runs when a source opens, and lists every"
                                    + " track whether or not it can be played.")));
            return card(id("tracks"), "Tracks", lines.toArray(Widget[]::new));
        }

        private Widget capabilitiesCard() {
            if (capabilities == null) {
                return card(
                        id("capabilities"),
                        "This build",
                        new Text("FFmpeg is not loaded, so nothing can play.", Attributes.NONE.classes("media-error")),
                        caption("The natives are their own build: ./gradlew :media:ffmpegBuild, then run the"
                                + " showcase again. Picking a source shows the loader's own reason, which"
                                + " names what it looked for and where."));
            }
            var found = capabilities;
            return card(
                    id("capabilities"),
                    "This build",
                    line(
                            "Decodes",
                            String.join(", ", found.decoders().stream().sorted().toList())),
                    line(
                            "Demuxes",
                            String.join(", ", found.demuxers().stream().sorted().toList())),
                    line(
                            "Providers",
                            found.providers().isEmpty()
                                    ? "none on the class path (this screen's Java decoder is passed in directly)"
                                    : String.join(", ", found.providers())),
                    caption("Read from the loaded libraries, not from a list: this is what MediaCapabilities"
                            + " answers."));
        }

        private Widget javaDecoderCard(JavaPcmDecoder decoder) {
            return card(
                    id("java-decoder"),
                    "A decoder written in Java",
                    new Toggle("Decode PCM in Java", decoder.enabled(), on -> setState(() -> decoder.enabled(on)))
                            .id(id("java-toggle")),
                    new Button("Play the chime", () -> open("java")).id(id("java-chime")),
                    caption("A DecoderProvider is asked before FFmpeg's decoders: the SPI an application uses to"
                            + " bring a codec Goldberry does not ship. This one claims 16-bit PCM and copies it"
                            + " to the engine as frames; resampling, the clock and seeking are the engine's."
                            + " Switch it off and the same WAV plays through FFmpeg. The Status card names the"
                            + " decoder either way."));
        }

        // ---------------------------------------------------------------- actions

        private void open(String key) {
            var sample = widget().kind().samples().stream()
                    .filter(s -> s.key().equals(key))
                    .findFirst();
            if (sample.isEmpty()) {
                return;
            }
            setState(() -> {
                chosen = key;
                message = sample.get().note();
            });
            play(sample.get().source());
        }

        private void openFile() {
            var window = host;
            if (window == null) {
                return;
            }
            window.fileDialog(
                    FileDialogSpec.openFile()
                            .filters(
                                    FileFilter.of(
                                            widget().kind().filterName,
                                            widget().kind().extensions()),
                                    FileFilter.everything("All files")),
                    choice -> {
                        if (choice instanceof FileChoice.Chosen(var paths, var ignored) && !paths.isEmpty()) {
                            var path = paths.getFirst();
                            setState(() -> {
                                chosen = "";
                                message = "From disk: " + path.getFileName();
                            });
                            play(Source.of(path));
                        }
                    });
        }

        private void closeSource() {
            widget().player().pause();
            setState(() -> {
                chosen = "";
                message = "Paused. Pick a source to open another.";
            });
        }

        private void play(Source source) {
            try {
                widget().player().open(source);
            } catch (MediaException e) {
                setState(() -> message = e.error().message());
            }
        }

        private Widget seekTo(String label, Duration duration, double fraction, boolean seekable) {
            return new Button(
                            label,
                            () -> widget().player().seek(Duration.ofNanos(Math.round(duration.toNanos() * fraction))))
                    .disabled(!seekable);
        }

        // ---------------------------------------------------------------- plumbing

        /// `name`, prefixed with this screen's: `audio-sources`, `video-sources`.
        private String id(String name) {
            return widget().kind().id() + "-" + name;
        }

        private void refresh() {
            if (isMounted()) {
                setState(() -> {});
            }
        }

        private void schedulePoll(PlayerStatus status) {
            if (status.state() != PlaybackState.PLAYING || poll != null || host == null) {
                return;
            }
            poll = host.after(POSITION_INTERVAL, () -> {
                poll = null;
                refresh();
            });
        }

        private static String describe(Track track) {
            var details =
                    switch (track.params()) {
                        case TrackParams.Audio audio -> audio.sampleRate() + " Hz, " + audio.channels() + " ch";
                        case TrackParams.Video video -> video.width() + "×" + video.height();
                        case TrackParams.Subtitle _ -> "subtitles";
                        case TrackParams.Other other -> other.type().name().toLowerCase(Locale.ROOT);
                    };
            var flags = new ArrayList<String>();
            if (track.isDefault()) {
                flags.add("default");
            }
            if (track.attachedPicture()) {
                flags.add("cover art");
            }
            return "#" + track.index() + "  " + track.type().name().toLowerCase(Locale.ROOT) + "  "
                    + track.codecName() + "  " + details
                    + (flags.isEmpty() ? "" : "  (" + flags.stream().collect(Collectors.joining(", ")) + ")");
        }

        private static Widget card(String id, String title, Widget... body) {
            var children = new ArrayList<Widget>();
            children.add(new Text(title, Attributes.NONE.classes("card-title")));
            children.addAll(List.of(body));
            return new Card(children, Attributes.NONE.id(id).classes("wall-card"));
        }

        private static Widget line(String label, String value) {
            return new Row(
                    List.of(
                            new Text(label, Attributes.NONE.classes("caption", "media-label")),
                            new Text(value, Attributes.NONE.classes("media-value"))),
                    Attributes.NONE.classes("media-line"));
        }

        private static Text caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
        }
    }
}
