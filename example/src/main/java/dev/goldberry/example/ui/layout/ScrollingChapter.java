package dev.goldberry.example.ui.layout;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.panel.masonry.Masonry;

/// The **Scrolling** screen: `scroll` and `affix`, a card for each viewport.
///
/// It fills its tab rather than sitting in the gallery's viewport, because each of
/// its cards owns a viewport and a scroller inside a scroller on the same axis is
/// banned. Each viewport is given a fixed height by `chapter-layout.css`, so the
/// wall fits a window.
///
/// Read more: [Scroll](https://goldberry.dev/docs/layout/scroll.html).
///
/// @param context what the screen is built from
public record ScrollingChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("layout/scroll");

    @Override
    public Widget build(BuildContext buildContext) {
        return new Column(
                List.of(
                        new ScreenHeader(
                                "Scrolling",
                                "A scroll is a viewport onto something larger than itself, moved by the wheel,"
                                        + " the keyboard, its bars or Java. An affix pins a child to its edge.",
                                CHAPTER),
                        new Masonry(
                                List.of(new ScrollingCard(), new ViewportCard(context.actions()), new ConsoleCard()),
                                Masonry.UNSET,
                                Wall.COLUMN_WIDTH,
                                Attributes.NONE.id("scrolling-wall").classes("wall"))),
                Attributes.NONE.id("screen-scrolling").classes("screen", "fills"));
    }
}
