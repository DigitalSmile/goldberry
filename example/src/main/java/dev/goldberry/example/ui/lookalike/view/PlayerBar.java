package dev.goldberry.example.ui.lookalike.view;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.example.ui.lookalike.library.Times;
import dev.goldberry.example.ui.lookalike.library.Track;
import dev.goldberry.example.ui.lookalike.player.Command;
import dev.goldberry.example.ui.lookalike.player.Repeat;
import dev.goldberry.example.ui.lookalike.player.Session;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.slider.Slider;
import dev.goldberry.widgets.text.Text;

/// The strip along the bottom: what is playing at the left, the transport and
/// the seek bar in the middle, the volume at the right.
///
/// The two sliders are the toolkit's own `slider`, recoloured by the
/// stylesheet: white on grey, turning green under the pointer.
///
/// @param library  where the current track comes from
/// @param session  what plays, where, and how loud
/// @param dispatch where a control's command goes
record PlayerBar(Library library, Session session, Consumer<Command> dispatch) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var track = session.current(library);
        return Parts.row(
                        List.of(
                                track.map(this::nowPlaying).orElseGet(() -> Parts.row(List.of(), "bar-now")),
                                transport(track.map(Track::length).orElse(Duration.ZERO)),
                                extras()),
                        "stream-bar")
                .id("stream-bar");
    }

    private Widget nowPlaying(Track track) {
        var liked = session.likes(track.id());
        var cover = library.playlist(session.queue(), session.liked()).cover();
        return Parts.row(
                List.of(
                        CoverArt.of(cover, CoverArt.Size.SMALL),
                        Parts.column(
                                List.of(
                                        Parts.text(track.title(), "bar-title", "one-line")
                                                .id("stream-now-title"),
                                        Parts.text(track.artist(), "bar-artist", "one-line")),
                                "bar-now-text"),
                        Parts.iconButton(
                                liked ? "Remove from Liked Songs" : "Save to Liked Songs",
                                "heart",
                                () -> dispatch.accept(new Command.Like(track.id())),
                                Parts.classes(liked, "liked", "icon-button", "bar-heart"))),
                "bar-now");
    }

    private Widget transport(Duration length) {
        var playing = session.playing();
        var repeat = session.repeat();
        var buttons = Parts.row(
                List.of(
                        Parts.iconButton(
                                session.shuffle() ? "Disable shuffle" : "Enable shuffle",
                                "shuffle",
                                () -> dispatch.accept(new Command.Shuffle()),
                                Parts.classes(session.shuffle(), "on", "icon-button", "bar-icon")),
                        Parts.iconButton(
                                "Previous",
                                "skip-back",
                                () -> dispatch.accept(new Command.Previous()),
                                "icon-button",
                                "bar-icon"),
                        Parts.iconButton(
                                        playing ? "Pause" : "Play",
                                        playing ? "pause" : "play",
                                        () -> dispatch.accept(new Command.Toggle()),
                                        "bar-play")
                                .id("stream-toggle"),
                        Parts.iconButton(
                                        "Next",
                                        "skip-forward",
                                        () -> dispatch.accept(new Command.Next()),
                                        "icon-button",
                                        "bar-icon")
                                .id("stream-next"),
                        Parts.iconButton(
                                switch (repeat) {
                                    case OFF -> "Enable repeat";
                                    case ALL -> "Enable repeat one";
                                    case ONE -> "Disable repeat";
                                },
                                repeat == Repeat.ONE ? "repeat-1" : "repeat",
                                () -> dispatch.accept(new Command.CycleRepeat()),
                                Parts.classes(repeat != Repeat.OFF, "on", "icon-button", "bar-icon"))),
                "bar-buttons");
        var seek = new Slider(
                        0,
                        1,
                        session.progress(library),
                        0,
                        null,
                        fraction -> dispatch.accept(new Command.Seek(fraction)),
                        length.isZero(),
                        Attributes.NONE.classes("bar-seek").name("Change progress"))
                .id("stream-seek");
        var position =
                new Text(Times.clock(session.position()), Attributes.NONE.classes("bar-time")).id("stream-position");
        return Parts.column(
                List.of(
                        buttons,
                        Parts.row(
                                List.of(position, seek, Parts.text(Times.clock(length), "bar-time")), "bar-progress")),
                "bar-transport");
    }

    private Widget extras() {
        var volume = session.audible();
        var speaker = volume == 0 ? "volume-x" : volume < 0.5 ? "volume-1" : "volume-2";
        return Parts.row(
                List.of(
                        Parts.iconButton(
                                        "Now playing view",
                                        "square-play",
                                        () -> dispatch.accept(new Command.NowPlayingPanel()),
                                        Parts.classes(session.panel(), "on", "icon-button", "bar-icon"))
                                .id("stream-panel-toggle"),
                        Parts.iconButton("Lyrics", "mic-vocal", () -> {}, "icon-button", "bar-icon"),
                        Parts.iconButton("Queue", "list-music", () -> {}, "icon-button", "bar-icon"),
                        Parts.iconButton(
                                        session.muted() ? "Unmute" : "Mute",
                                        speaker,
                                        () -> dispatch.accept(new Command.Mute()),
                                        "icon-button",
                                        "bar-icon")
                                .id("stream-mute"),
                        new Slider(
                                        0,
                                        1,
                                        volume,
                                        0,
                                        null,
                                        level -> dispatch.accept(new Command.Volume(level)),
                                        false,
                                        Attributes.NONE.classes("bar-volume").name("Volume"))
                                .id("stream-volume")),
                "bar-extras");
    }
}
