package dev.goldberry.example.ui.media;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.Host;
import dev.goldberry.example.media.JavaPcmDecoder;
import dev.goldberry.example.media.ShowcaseMedia;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaCapabilities;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.PlayerStatus;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.subtitle.Cue;
import dev.goldberry.media.view.MediaControls;
import dev.goldberry.media.view.VideoView;
import dev.goldberry.render.dialog.FileChoice;
import dev.goldberry.render.dialog.FileDialogSpec;
import dev.goldberry.render.dialog.FileFilter;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.panel.masonry.Masonry;

/// The **Audio** and **Video** screens: a player across the top, and a wall of
/// cards under it that drive the same `MediaPlayer` from Java and report what it
/// says.
///
/// ```text
/// ┌──────────────────────────────────────────┐
/// │ VIDEO                             Docs ↗ │  the header
/// │ ┌──────────────────────────────────────┐ │
/// │ │ media-player, from video.kdl         │ │  the player card, full width
/// │ └──────────────────────────────────────┘ │
/// │ ┌────────┐ ┌────────┐ ┌────────┐         │
/// │ │Sources │ │Control │ │Status  │  …      │  a masonry of cards
/// └──────────────────────────────────────────┘
/// ```
///
/// The player is the markup document's, naming a player the model registered;
/// everything else here is Java. The player is the application's, so the widgets
/// and the cards share it and none of them owns it.
///
/// FFmpeg may be absent, since the natives are a build of their own. Then the
/// This build card says so, and picking a source says the loader's reason.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
///
/// @param kind        which of the two screens this is
/// @param player      the screen's own player
/// @param javaDecoder the application's decoder, for the Audio screen; null for
///                    the Video screen, which has no card for it
/// @param playerPane  the player widget, inflated from the screen's document
public record MediaWall(
        MediaKind kind, MediaPlayer player, @Nullable JavaPcmDecoder javaDecoder, Widget playerPane)
        implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new MediaWallState();
    }

    /// What more than one card shares, the player's subscription, and the poll
    /// that keeps the Status card's position moving.
    static final class MediaWallState extends State<MediaWall> implements MediaDesk {

        /// How often the Status card reads the position while playing, as the
        /// player widget does.
        private static final Duration POSITION_INTERVAL = Duration.ofMillis(250);

        private @Nullable AutoCloseable subscription;
        private EventLoop.@Nullable Timer poll;
        private @Nullable Host host;
        private String chosen = "";
        private String message = "Nothing opened yet. Pick a source; it starts playing.";

        /// What the player opened last, which the Hardware card reopens.
        private @Nullable Source current;

        private String subtitleMessage = "";
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
            var open = subscription;
            subscription = null;
            if (open != null) {
                try {
                    open.close();
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
            var wall = widget();
            var kind = wall.kind();
            var status = wall.player().status();
            schedulePoll(status);
            return new Column(
                    List.of(
                            new ScreenHeader(kind.title(), kind.summary(), kind.doc()),
                            playerCard(),
                            new Masonry(
                                    cards(status),
                                    Masonry.UNSET,
                                    Wall.COLUMN_WIDTH,
                                    Attributes.NONE.id(kind.id("wall")).classes("wall"))),
                    Attributes.NONE.id("screen-" + kind.id()).classes("screen"));
        }

        /// The screen's player, across the top.
        private Widget playerCard() {
            var wall = widget();
            var card = switch (wall.kind()) {
                case AUDIO ->
                    new ShowcaseCard(
                            "audio-player-card",
                            "Audio player",
                            "Compact controls over a player: play and pause, the times, a seek bar, mute and"
                                    + " volume. Pick a source below, then click the player and try Space,"
                                    + " the arrows, M, and < and > for the speed.",
                            MediaDocs.AUDIO_PLAYER);
                case VIDEO ->
                    new ShowcaseCard(
                            "video-player-card",
                            "Media player",
                            "The picture with its controls over it; they fade while it plays and come back"
                                    + " when the pointer moves. Click the picture to pause, or focus it and"
                                    + " try , and . to step a picture and F for fullscreen.",
                            MediaDocs.MEDIA_PLAYER);
            };
            return card.classed("media-card").of(wall.playerPane());
        }

        private List<Widget> cards(PlayerStatus status) {
            var wall = widget();
            var kind = wall.kind();
            var player = wall.player();
            var dialogs = host != null && host.fileDialogs().supported();
            var cards = new ArrayList<Widget>();
            cards.add(new SourcesCard(kind, chosen, message, dialogs, this));
            switch (kind) {
                case AUDIO -> cards.add(mediaControlsCard());
                case VIDEO -> cards.add(videoViewCard());
            }
            cards.add(new ControlCard(kind, player, status, this));
            cards.add(new StatusCard(kind, status));
            cards.add(new TracksCard(kind, player, status));
            if (kind == MediaKind.VIDEO) {
                var cues = player.currentSubtitles().stream()
                        .map(Cue::text)
                        .map(text -> text.replace('\n', ' '))
                        .collect(Collectors.joining(" · "));
                cards.add(new SubtitlesCard(kind, player, status, cues, subtitleMessage, dialogs, this));
                cards.add(new HardwareCard(
                        kind,
                        player.hardwareDecoding() == HardwareDecoding.AUTO,
                        status.videoDecoder().orElse("—"),
                        this));
            }
            var decoder = wall.javaDecoder();
            if (decoder != null) {
                cards.add(new JavaDecoderCard(kind, decoder.enabled(), this));
            }
            cards.add(new CapabilitiesCard(kind, capabilities));
            return cards;
        }

        /// `media-controls` on its own, driving the player the `audio-player`
        /// above drives.
        private Widget mediaControlsCard() {
            return new ShowcaseCard(
                            "audio-media-controls",
                            "Media controls",
                            "The transport bar on its own, for a layout of your own. This one drives the same player"
                                    + " as the one above, so pressing play on either moves both.",
                            MediaDocs.MEDIA_CONTROLS)
                    .of(new MediaControls(widget().player()).withAttributes(Attributes.NONE.id("audio-controls")));
        }

        /// A bare `video-view` over a `media-controls`: the player as an
        /// application lays it out itself.
        private Widget videoViewCard() {
            var player = widget().player();
            return new ShowcaseCard(
                            "video-view-card",
                            "Video view",
                            "A player's pictures and nothing else, for an application that draws its own controls."
                                    + " This one shows the player above, filling its box, with a media-controls bar"
                                    + " under it.",
                            MediaDocs.VIDEO_VIEW)
                    .of(new Column(
                            List.of(
                                    new VideoView(player, Fit.COVER).withAttributes(Attributes.NONE.id("video-bare")),
                                    new MediaControls(player)),
                            Attributes.NONE.id("video-bare-stage")));
        }

        // ---------------------------------------------------------------- actions

        @Override
        public void open(String key) {
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

        @Override
        public void openFile() {
            var window = host;
            if (window == null) {
                return;
            }
            var kind = widget().kind();
            window.fileDialog(
                    FileDialogSpec.openFile()
                            .filters(
                                    FileFilter.of(
                                            kind.filterName(), kind.extensions().toArray(String[]::new)),
                                    FileFilter.everything("All files")),
                    choice -> {
                        if (choice instanceof FileChoice.Chosen(var paths, var _) && !paths.isEmpty()) {
                            var path = paths.getFirst();
                            setState(() -> {
                                chosen = "";
                                message = "From disk: " + path.getFileName();
                            });
                            play(Source.of(path));
                        }
                    });
        }

        @Override
        public void closeSource() {
            widget().player().pause();
            setState(() -> {
                chosen = "";
                message = "Paused. Pick a source to open another.";
            });
        }

        @Override
        public void setSpeed(float speed) {
            try {
                widget().player().setRate(speed);
            } catch (IllegalStateException e) {
                var why = Objects.requireNonNull(e.getMessage(), "setRate says why it refused the rate");
                setState(() -> message = why);
            }
        }

        @Override
        public void step(int pictures) {
            if (!widget().player().step(pictures)) {
                setState(() -> message = "No picture to step from yet.");
            }
        }

        @Override
        public void loadSubtitles(ShowcaseMedia.SubtitleFile file) {
            loadSubtitles(file.source(), file.title());
        }

        /// Reads `file` off the UI thread, as `loadSubtitles` asks, and says how it
        /// went on the Subtitles card.
        private void loadSubtitles(Source file, String name) {
            var player = widget().player();
            Thread.ofVirtual().name("showcase-subtitles").start(() -> {
                String said;
                try {
                    player.loadSubtitles(file);
                    said = "Showing " + name + ".";
                } catch (IOException | IllegalStateException e) {
                    said = "Could not load " + name + ": " + e.getMessage();
                }
                var result = said;
                Goldberry.ui().execute(() -> {
                    if (isMounted()) {
                        setState(() -> subtitleMessage = result);
                    }
                });
            });
        }

        @Override
        public void openSubtitleFile() {
            var window = host;
            if (window == null) {
                return;
            }
            window.fileDialog(
                    FileDialogSpec.openFile()
                            .filters(FileFilter.of("Subtitles", "srt", "vtt"), FileFilter.everything("All files")),
                    choice -> {
                        if (choice instanceof FileChoice.Chosen(var paths, var _) && !paths.isEmpty()) {
                            var path = paths.getFirst();
                            loadSubtitles(Source.of(path), String.valueOf(path.getFileName()));
                        }
                    });
        }

        /// Switches hardware decoding, and reopens what plays where it was, since
        /// the player applies it from the next source opened.
        @Override
        public void setHardware(boolean on) {
            var player = widget().player();
            player.setHardwareDecoding(on ? HardwareDecoding.AUTO : HardwareDecoding.OFF);
            var source = current;
            var status = player.status();
            if (source == null || !status.state().hasMedia()) {
                setState(() -> {});
                return;
            }
            var paused = status.state() == PlaybackState.PAUSED;
            play(source);
            player.seek(status.position());
            if (paused) {
                player.pause();
            }
            setState(() -> message = on ? "Reopened on the video engine." : "Reopened in software.");
        }

        @Override
        public void setJavaDecoder(boolean on) {
            var decoder = widget().javaDecoder();
            if (decoder != null) {
                setState(() -> decoder.enabled(on));
            }
        }

        private void play(Source source) {
            current = source;
            subtitleMessage = "";
            try {
                widget().player().open(source);
            } catch (MediaException e) {
                setState(() -> message = e.error().message());
            }
        }

        // ---------------------------------------------------------------- plumbing

        private void refresh() {
            if (isMounted()) {
                setState(() -> {});
            }
        }

        private void schedulePoll(PlayerStatus status) {
            var moving = status.state() == PlaybackState.PLAYING || status.state() == PlaybackState.BUFFERING;
            if (!moving || poll != null || host == null) {
                return;
            }
            poll = host.after(POSITION_INTERVAL, () -> {
                poll = null;
                refresh();
            });
        }
    }
}
