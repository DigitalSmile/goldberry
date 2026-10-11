package dev.goldberry.example.ui.lookalike.view;

import java.util.List;
import java.util.function.Consumer;

import dev.goldberry.example.ui.lookalike.library.Library;
import dev.goldberry.example.ui.lookalike.library.Times;
import dev.goldberry.example.ui.lookalike.library.Track;
import dev.goldberry.example.ui.lookalike.player.Command;
import dev.goldberry.example.ui.lookalike.player.Session;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;

/// The right column the player bar's "Now playing view" opens: the current
/// track large, a card about its artist, and what plays next.
///
/// @param library  where the tracks come from
/// @param session  what plays, and in what order
/// @param dispatch where the close button's command goes
record NowPlayingPanel(Library library, Session session, Consumer<Command> dispatch) implements Widget.Stateless {

    /// How many plays a month the artist card claims per play of a track — the
    /// invented library has play counts and no listeners.
    private static final long PLAYS_PER_LISTENER = 30;

    @Override
    public Widget build(BuildContext context) {
        var queue = library.playlist(session.queue(), session.liked());
        var head = Parts.row(
                List.of(
                        Parts.text(queue.name(), "panel-title", "one-line"),
                        new Spacer(),
                        Parts.iconButton(
                                "Close", "x", () -> dispatch.accept(new Command.NowPlayingPanel()), "icon-button")),
                "panel-head");
        var body = session.current(library)
                .map(track -> List.of(
                        CoverArt.of(queue.cover(), CoverArt.Size.HUGE),
                        Parts.text(track.title(), "panel-track", "one-line"),
                        Parts.text(track.artist(), "panel-artist", "one-line"),
                        artist(track),
                        upNext()))
                .orElseGet(() -> List.of(Parts.text("Nothing is playing", "panel-artist")));
        return new Scroll(
                List.of(Parts.column(List.of(head, Parts.column(body, "panel-body")), "panel-page")),
                ScrollAxis.VERTICAL,
                Attributes.NONE.id("stream-now-panel").classes("stream-panel", "stream-now"));
    }

    /// A card about who made the current track.
    private Widget artist(Track track) {
        var plays = library.everyTrack().stream()
                .filter(other -> other.artist().equals(track.artist()))
                .mapToLong(Track::plays)
                .sum();
        return Parts.column(
                List.of(
                        Parts.text("About the artist", "panel-card-title"),
                        Parts.text(track.artist(), "panel-card-name"),
                        Parts.text(
                                Times.count(plays / PLAYS_PER_LISTENER) + " monthly listeners", "panel-card-detail")),
                "panel-card");
    }

    /// The track `Next` would play, in the order it would play it.
    private Widget upNext() {
        var tracks = session.queued(library);
        var order = session.order(tracks.size());
        var place = order.indexOf(session.index());
        var next = place + 1 < order.size() ? tracks.get(order.get(place + 1)) : null;
        return Parts.column(
                List.of(
                        Parts.text("Next in queue", "panel-card-title"),
                        next == null
                                ? Parts.text("The end of the list", "panel-card-detail")
                                : Parts.text(next.title() + " · " + next.artist(), "panel-card-detail", "one-line")),
                "panel-card");
    }
}
