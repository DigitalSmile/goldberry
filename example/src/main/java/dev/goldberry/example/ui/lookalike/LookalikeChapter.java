package dev.goldberry.example.ui.lookalike;

import java.time.Duration;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.example.ui.lookalike.library.SampleLibrary;
import dev.goldberry.example.ui.lookalike.player.Session;
import dev.goldberry.example.ui.lookalike.view.StreamingApp;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;

/// The **Lookalike** screen: a music streaming app, built from the same widgets
/// as every other screen and dressed by one stylesheet to look like a product
/// people use every day.
///
/// It mirrors no chapter of its own. Its header links the styling chapter,
/// because what it shows is how far a sheet takes the stock widgets: nothing
/// on it is a widget written for it except the records that arrange them.
///
/// It fills its tab, because the player owns two viewports — the library list
/// and the playlist page — and a scroller inside the gallery's would be a
/// scroller inside a scroller.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
///
/// @param context what the screen is built from; the player needs none of it
public record LookalikeChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen links.
    static final DocLink CHAPTER = DocLink.page("guide/styling");

    /// The playlist the player opens on.
    static final String OPENING_PLAYLIST = "night-drive";

    /// The track the player opens on, paused part-way through: a bar with a
    /// half-played song says "player" at a glance, and a playing one would make
    /// a different picture every second.
    static final int OPENING_TRACK = 2;

    /// How far into it.
    static final Duration OPENING_POSITION = Duration.ofSeconds(76);

    /// The session the screen opens on.
    static Session opening() {
        return Session.start(OPENING_PLAYLIST, OPENING_TRACK, OPENING_POSITION, SampleLibrary.initiallyLiked());
    }

    @Override
    public Widget build(BuildContext buildContext) {
        var library = SampleLibrary.create();
        return new Column(
                List.of(
                        new ScreenHeader(
                                "Lookalike",
                                "A music streaming app made of the stock rows, pressables, icons and sliders and"
                                        + " dressed by one stylesheet. Open a playlist, play a track, like it, shuffle,"
                                        + " seek and open the now-playing panel.",
                                CHAPTER),
                        new StreamingApp(library, opening())),
                Attributes.NONE.id("screen-lookalike").classes("screen", "fills"));
    }
}
