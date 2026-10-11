package dev.goldberry.example.ui.lookalike.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.example.ui.lookalike.library.Playlist;
import dev.goldberry.example.ui.lookalike.player.Command;
import dev.goldberry.example.ui.lookalike.player.Session;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;

/// The left column: where to go, then the user's library of playlists.
///
/// A playlist row opens the playlist in the main view. The one playing says so
/// twice, in a green name and a little equaliser, which is how a player answers
/// "where is that song coming from" without a word.
///
/// @param library  what the rows list
/// @param session  which row is open and which is playing
/// @param dispatch where a pressed row's command goes
record Sidebar(Library library, Session session, Consumer<Command> dispatch) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var nav = Parts.column(
                List.of(
                        Parts.row(
                                List.of(Parts.icon("house", "nav-icon"), Parts.text("Home", "nav-label")),
                                "nav-item",
                                "active"),
                        Parts.row(
                                List.of(Parts.icon("search", "nav-icon"), Parts.text("Search", "nav-label")),
                                "nav-item")),
                "stream-panel",
                "stream-nav");

        var rows = new ArrayList<Widget>();
        library.playlists().forEach(list -> rows.add(row(list)));

        var head = Parts.row(
                List.of(
                        Parts.icon("library", "nav-icon"),
                        Parts.text("Your Library", "nav-label"),
                        new Spacer(),
                        Parts.iconButton("Create playlist", "plus", () -> {}, "icon-button")),
                "library-head");
        var filters = Parts.row(
                List.of(
                        Parts.text("Playlists", "filter-chip", "on"),
                        Parts.text("Artists", "filter-chip"),
                        Parts.text("Albums", "filter-chip")),
                "library-filters");
        var list = new Scroll(
                List.of(Parts.column(rows, "library-rows")),
                ScrollAxis.VERTICAL,
                Attributes.NONE.id("stream-library-list"));

        return Parts.column(
                        List.of(nav, Parts.column(List.of(head, filters, list), "stream-panel", "stream-library")),
                        "stream-sidebar")
                .id("stream-sidebar");
    }

    /// One playlist: its cover, its name, what it is and whose.
    private Widget row(Playlist stored) {
        var list = library.playlist(stored.id(), session.liked());
        var open = list.id().equals(session.viewing());
        var queued = list.id().equals(session.queue());
        var name = Parts.text(list.name(), Parts.classes(queued, "now", "row-title", "one-line"));
        var detail = Parts.text(
                list.kind() + " · "
                        + (list.id().equals(Library.LIKED) ? list.tracks().size() + " songs" : list.owner()),
                "row-detail",
                "one-line");
        var content = new ArrayList<Widget>(List.of(
                CoverArt.of(list.cover(), CoverArt.Size.SMALL), Parts.column(List.of(name, detail), "row-text")));
        if (queued && session.playing()) {
            content.add(Parts.icon("audio-lines", "now-icon"));
        }
        return new Pressable(
                        list.name(),
                        () -> dispatch.accept(new Command.Open(list.id())),
                        false,
                        List.of(Parts.row(content, "library-row-body")),
                        Attributes.NONE.classes(Parts.classes(open, "selected", "library-row")))
                .id("playlist-" + list.id());
    }
}
