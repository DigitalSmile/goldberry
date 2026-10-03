package dev.goldberry.example.ui.content;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;

/// The **Markdown** screen: an editor on the left, the same document rendered on
/// the right, live.
///
/// The panes are `markdown.kdl`, and the point of it is that they are two nodes: a
/// `text-area` that writes `md.source` and a `markdown-view` that reads it. Nothing
/// in Java connects them. A live preview is a binding.
///
/// The screen fills its tab rather than sitting in the gallery's viewport: the
/// preview owns a `scroll` of its own, and a `split-pane` needs a height to divide.
///
/// Read more: [`markdown-view`](https://goldberry.dev/docs/components/content.html#markdown-view).
///
/// @param context what the screen is built from
public record MarkdownChapter(GalleryContext context) implements Widget.Stateless {

    /// The section this screen mirrors.
    static final DocLink CHAPTER = DocLink.to("components/content", "markdown-view");

    /// What the header says the screen is for.
    static final String SUMMARY = "A Markdown document drawn as ordinary widgets under the theme. Type on the left"
            + " and the right follows, because both sides are one property. Press a link or tick a box, and drag"
            + " across the text to select it.";

    @Override
    public Widget build(BuildContext buildContext) {
        return new Column(
                List.of(
                        new ScreenHeader("Markdown", SUMMARY, CHAPTER),
                        context.documents().document("markdown.kdl")),
                Attributes.NONE.id("screen-markdown").classes("screen", "fills"));
    }
}
