package dev.goldberry.example.ui.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.panel.masonry.Masonry;
import dev.goldberry.widgets.text.Text;

/// The **Guide** screen: a card for every chapter of the guide that is read
/// rather than tried, grouped as the book groups them.
///
/// Each part of the book is a caption over a wall of reference cards, so the
/// screen reads like the book's table of contents with a sentence under each
/// entry.
///
/// Read more: [Goldberry](https://goldberry.dev/docs/introduction.html).
///
/// @param context what the screen is built from
public record GuideChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("introduction");

    @Override
    public Widget build(BuildContext buildContext) {
        var children = new ArrayList<Widget>();
        children.add(new ScreenHeader(
                "Guide",
                "The chapters to read rather than try: what Goldberry is, how to install it, what a frame costs,"
                        + " how to test and ship an application, and how to contribute.",
                CHAPTER));
        for (var part : GuideParts.PARTS) {
            children.add(part(part));
        }
        return new Column(children, Attributes.NONE.id("screen-guide").classes("screen"));
    }

    /// One part of the book: its caption, and a wall of its chapters.
    private static Widget part(GuideParts.Part part) {
        var slug = part.title().toLowerCase(Locale.ROOT).replace(' ', '-');
        var cards = part.chapters().stream()
                .map(chapter -> (Widget)
                        new ShowcaseCard(chapter.id(), chapter.title(), chapter.summary(), DocLink.page(chapter.page()))
                                .reference())
                .toList();
        return new Column(
                List.of(
                        new Text(part.title(), Attributes.NONE.classes("guide-part-title")),
                        new Masonry(
                                cards,
                                Masonry.UNSET,
                                Wall.COLUMN_WIDTH,
                                Attributes.NONE.id("guide-" + slug + "-wall").classes("wall"))),
                Attributes.NONE.id("guide-" + slug).classes("guide-part"));
    }
}
