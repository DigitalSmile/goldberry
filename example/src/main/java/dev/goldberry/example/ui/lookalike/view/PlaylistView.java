package dev.goldberry.example.ui.lookalike.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import dev.goldberry.example.ui.lookalike.library.Album;
import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.example.ui.lookalike.library.Playlist;
import dev.goldberry.example.ui.lookalike.library.Times;
import dev.goldberry.example.ui.lookalike.player.Command;
import dev.goldberry.example.ui.lookalike.player.Session;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;

/// The middle of the window: the open playlist's header, its play button, its
/// tracks, and a shelf of albums under them.
///
/// The page is tinted with the playlist's cover, fading into the window's black
/// a third of the way down, which is the one flourish that makes a player look
/// like this one.
///
/// @param library  what the page lists
/// @param session  which playlist is open and what plays
/// @param dispatch where a press's command goes
record PlaylistView(Library library, Session session, Consumer<Command> dispatch) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var list = session.shown(library);
        var page = new ArrayList<Widget>();
        page.add(hero(list));
        page.add(actions(list));
        page.add(tracks(list));
        page.add(shelf());
        return new Scroll(
                List.of(Parts.column(page, "stream-page")),
                ScrollAxis.VERTICAL,
                Attributes.NONE
                        .id("stream-main")
                        .classes("stream-main", list.cover().tintClass()));
    }

    /// The cover, the kind, the title, the line under it, and whose it is.
    private static Widget hero(Playlist list) {
        var count = list.tracks().size() + (list.tracks().size() == 1 ? " song" : " songs");
        var facts = list.tracks().isEmpty()
                ? list.owner()
                : list.owner() + " · " + count + ", " + Times.spoken(list.length());
        return Parts.row(
                List.of(
                        CoverArt.of(list.cover(), CoverArt.Size.HUGE),
                        Parts.column(
                                List.of(
                                        Parts.text(list.kind(), "hero-kind"),
                                        Parts.text(list.name(), "hero-title", "one-line")
                                                .id("stream-title"),
                                        Parts.text(list.description(), "hero-description"),
                                        Parts.text(facts, "hero-facts")),
                                "hero-text")),
                "stream-hero");
    }

    /// The big green button, shuffle, and the rest of the row.
    ///
    /// The button plays this playlist from the top, unless it is already the
    /// queue, in which case it pauses and resumes it — the difference between
    /// "play this" and "carry on".
    private Widget actions(Playlist list) {
        var isQueue = list.id().equals(session.queue());
        var playing = isQueue && session.playing();
        Runnable press = isQueue
                ? () -> dispatch.accept(new Command.Toggle())
                : () -> dispatch.accept(new Command.Play(list.id(), 0));
        var play = Parts.iconButton(playing ? "Pause" : "Play", playing ? "pause" : "play", press, "big-play")
                .id("stream-play-list");
        return Parts.row(
                List.of(
                        play,
                        Parts.iconButton(
                                session.shuffle() ? "Disable shuffle" : "Enable shuffle",
                                "shuffle",
                                () -> dispatch.accept(new Command.Shuffle()),
                                Parts.classes(session.shuffle(), "on", "icon-button", "action-icon")),
                        Parts.iconButton("Add to Your Library", "circle-plus", () -> {}, "icon-button", "action-icon"),
                        Parts.iconButton("More options", "ellipsis", () -> {}, "icon-button", "action-icon"),
                        new Spacer(),
                        Parts.text("List", "action-label"),
                        Parts.icon("list", "action-label-icon")),
                "stream-actions");
    }

    /// The table: a header, a rule, and a row per track.
    private Widget tracks(Playlist list) {
        if (list.tracks().isEmpty()) {
            return Parts.column(
                    List.of(
                            Parts.text("Songs you like will appear here", "empty-title"),
                            Parts.text("Save songs by tapping the heart icon.", "empty-detail")),
                    "stream-empty");
        }
        var isQueue = list.id().equals(session.queue());
        var rows = new ArrayList<Widget>();
        rows.add(Parts.row(
                List.of(
                        Parts.row(
                                List.of(
                                        Parts.text("#", "track-lead"),
                                        Parts.text("Title", "track-name"),
                                        Parts.text("Album", "track-album")),
                                "track-body-row"),
                        Parts.icon("clock-3", "head-clock")),
                "track-head"));
        IntStream.range(0, list.tracks().size()).forEach(at -> {
            var track = list.tracks().get(at);
            var now = !isQueue || at != session.index()
                    ? TrackRow.Now.IDLE
                    : session.playing() ? TrackRow.Now.PLAYING : TrackRow.Now.PAUSED;
            rows.add(new TrackRow(
                    at + 1,
                    track,
                    now,
                    session.likes(track.id()),
                    () -> dispatch.accept(new Command.Play(list.id(), at)),
                    () -> dispatch.accept(new Command.Like(track.id()))));
        });
        return Parts.column(rows, "stream-tracks");
    }

    /// Albums the user might like: a row of covers with a title and a year.
    private Widget shelf() {
        var cards = library.albums().stream().map(PlaylistView::album).toList();
        return Parts.column(
                List.of(
                        Parts.row(
                                List.of(
                                        Parts.text("Albums you might like", "shelf-title"),
                                        new Spacer(),
                                        Parts.text("Show all", "shelf-more")),
                                "shelf-head"),
                        Parts.row(cards, "shelf-cards")),
                "stream-shelf");
    }

    private static Widget album(Album album) {
        return Parts.column(
                List.of(
                        CoverArt.of(album.cover(), CoverArt.Size.LARGE),
                        Parts.text(album.title(), "album-title", "one-line"),
                        Parts.text(album.year() + " · " + album.artist(), "album-detail", "one-line")),
                "album-card");
    }
}
