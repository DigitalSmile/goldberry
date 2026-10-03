package dev.goldberry.example.ui.content;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;

/// The **HTML** screen: an editor on the left, the same page rendered on the right,
/// live.
///
/// [MarkdownChapter]'s twin, one tab along, over the same binding: `html.kdl` is a
/// `text-area` writing `html.source`, an `html-view` reading it, and a `text`
/// bound to what the last pressed link handed over. What differs between the two
/// screens is what differs between the two views: the parser, and the vocabulary.
///
/// Read more: [`html-view`](https://goldberry.dev/docs/components/content.html#html-view).
///
/// @param context what the screen is built from
public record HtmlChapter(GalleryContext context) implements Widget.Stateless {

    /// The section this screen mirrors.
    static final DocLink CHAPTER = DocLink.to("components/content", "html-view");

    /// What the header says the screen is for.
    static final String SUMMARY = "An HTML page parsed in Java and drawn with the same widgets as Markdown, so it"
            + " follows the theme. Type on the left and the page follows. Press a link, and the line above the"
            + " page says what the application was handed.";

    @Override
    public Widget build(BuildContext buildContext) {
        return new Column(
                List.of(
                        new ScreenHeader("HTML", SUMMARY, CHAPTER),
                        context.documents().document("html.kdl")),
                Attributes.NONE.id("screen-html").classes("screen", "fills"));
    }
}
