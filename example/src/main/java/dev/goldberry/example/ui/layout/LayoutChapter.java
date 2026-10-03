package dev.goldberry.example.ui.layout;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Layout** screen: a card for each layout widget and for each section of
/// sizing with CSS, every one of them a tree and a stylesheet.
///
/// The cards are a document, `layout.kdl`, because none of them holds a value:
/// what each one shows is what its rules in `chapter-layout.css` do to it.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html).
///
/// @param context what the screen is built from
public record LayoutChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("layout/index");

    /// The document the cards are written in.
    static final String DOCUMENT = "layout.kdl";

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "layout",
                "Layout",
                "Every box is placed by flexbox. A row or a column says which way its children go,"
                        + " and the stylesheet says the rest: the gaps, the padding, the alignment and the sizes.",
                CHAPTER,
                context.documents().wall(DOCUMENT),
                List.of());
    }
}
