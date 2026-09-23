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

/// The **Media** screen: `goldberry-media`'s `audio-player`, and everything around
/// it that an application can reach.
///
/// The player itself comes from `media.kdl`, a markup document naming a
/// `MediaPlayer` the model registered. The cards under it are the Java side:
/// picking a source (bundled clips, a live stream, a file from disk, and the
/// failures), driving the player from code, reading its status and the tracks
/// the probe found, asking what this build decodes, and a decoder this
/// application wrote itself.
///
/// FFmpeg may be absent: the natives are a separate build (`docs/media-plan.md`).
/// Then the Capabilities card says why, and picking a source says the same.
public record MediaScreen(MediaPlayer player, JavaPcmDecoder javaDecoder, Widget playerPane)
        implements Widget.Stateful {

    static final String NOTE = "Audio from `goldberry-media`: FFmpeg's demuxers and royalty-free decoders,"
            + " driven from Java. Every byte reaches FFmpeg through a Java stream, so the bundled clips, the"
            + " live stream and a file from disk are the same code path, and the engine's threads, clock and"
            + " seeking are Java too. The player above is one markup node, `audio-player`, naming a"
            + " MediaPlayer. The cards drive that same player from code, show what it reports, and include a"
            + " decoder written in this application. Pick an error case too: an unsupported codec opens,"
            + " lists its tracks, and says which codec it could not play.";

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
            var cards = List.<Widget>of(
                    sources(),
                    control(status),
                    statusCard(status),
                    tracks(status),
                    capabilitiesCard(),
                    javaDecoderCard());
            return new Column(
                    List.of(
                            new SectionHeader("Media"),
                            new Text(NOTE, Attributes.NONE.classes("prose")),
                            new Card(
                                    List.of(widget().playerPane()),
                                    Attributes.NONE.id("media-player-card").classes("wall-card")),
                            new Masonry(
                                    cards,
                                    2,
                                    Masonry.UNSET,
                                    Attributes.NONE.id("media-wall").classes("wall"))),
                    Attributes.NONE.id("screen-media").classes("screen"));
        }

        // ---------------------------------------------------------------- cards

        private Widget sources() {
            var options = new ArrayList<Option>();
            for (var sample : ShowcaseMedia.SAMPLES) {
                options.add(new Option(sample.key(), sample.title()));
            }
            var dialogs = host != null && host.fileDialogs().supported();
            return card(
                    "media-sources",
                    "Sources",
                    new Select(chosen, this::open, options.toArray(Option[]::new))
                            .placeholder("Choose a source…")
                            .id("media-source"),
                    new Row(
                            List.of(
                                    new Button("Open a file…", this::openFile)
                                            .disabled(!dialogs)
                                            .id("media-open-file"),
                                    new Button("Close", this::closeSource).id("media-close")),
                            Attributes.NONE.classes("media-actions")),
                    caption(message).id("media-source-note"));
        }

        private Widget control(PlayerStatus status) {
            var player = widget().player();
            var seekable = status.seekable();
            var duration = status.duration().orElse(Duration.ZERO);
            return card(
                    "media-control",
                    "Driven from Java",
                    new Row(
                            List.of(
                                    new Button("Play", player::play).id("media-java-play"),
                                    new Button("Pause", player::pause).id("media-java-pause"),
                                    new Button("From the top", () -> player.seek(Duration.ZERO))
                                            .disabled(!seekable)
                                            .id("media-java-restart")),
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
                                            .id("media-java-mute"),
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
            lines.add(line("Decoder", status.audioDecoder().orElse("none yet")));
            lines.add(line("Seekable", status.state().hasMedia() ? (status.seekable() ? "yes" : "no: live") : "—"));
            lines.add(line("Volume", Math.round(status.volume() * 100) + "%" + (status.muted() ? ", muted" : "")));
            status.error().ifPresent(error -> lines.add(line("Error", error.message())));
            lines.add(caption("One immutable PlayerStatus: pushed when the state changes, and read four times a"
                    + " second for the position while playing."));
            return card("media-status", "Status", lines.toArray(Widget[]::new));
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
            return card("media-tracks", "Tracks", lines.toArray(Widget[]::new));
        }

        private Widget capabilitiesCard() {
            if (capabilities == null) {
                return card(
                        "media-capabilities",
                        "This build",
                        new Text("FFmpeg is not loaded, so nothing can play.", Attributes.NONE.classes("media-error")),
                        caption("The natives are their own build: ./gradlew :media:ffmpegBuild, then run the"
                                + " showcase again. Picking a source shows the loader's own reason, which"
                                + " names what it looked for and where."));
            }
            var found = capabilities;
            return card(
                    "media-capabilities",
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

        private Widget javaDecoderCard() {
            var decoder = widget().javaDecoder();
            return card(
                    "media-java-decoder",
                    "A decoder written in Java",
                    new Toggle("Decode PCM in Java", decoder.enabled(), on -> setState(() -> decoder.enabled(on)))
                            .id("media-java-toggle"),
                    new Button("Play the chime", () -> open("java")).id("media-java-chime"),
                    caption("A DecoderProvider is asked before FFmpeg's decoders: the SPI an application uses to"
                            + " bring a codec Goldberry does not ship. This one claims 16-bit PCM and copies it"
                            + " to the engine as frames; resampling, the clock and seeking are the engine's."
                            + " Switch it off and the same WAV plays through FFmpeg. The Status card names the"
                            + " decoder either way."));
        }

        // ---------------------------------------------------------------- actions

        private void open(String key) {
            var sample = ShowcaseMedia.SAMPLES.stream()
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
                                            "Audio", "opus", "ogg", "oga", "mp3", "flac", "wav", "webm", "mkv", "mka",
                                            "mp4", "m4a"),
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
