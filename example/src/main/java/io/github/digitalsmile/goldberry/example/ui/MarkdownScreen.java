package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Markdown** screen: an editor on the left, the same document rendered on
/// the right, live.
///
/// The screen that is about an **optional module**. `goldberry-html` is not a
/// dependency of the toolkit — an application opts into it, adds its stylesheet
/// beside `Controls.stylesheets(theme)`, and gets a `markdown-view` node it never
/// registered (ADR-0190, ADR-0294, ADR-0295).
///
/// ## Why it is a document *and* a class, like every other screen
///
/// The interesting half is `markdown.kdl`, and it is two nodes: a `text-area` that
/// writes `md.source` and a `markdown-view` that reads it. Nothing in this
/// application connects them — the preview's element is subscribed to the property,
/// a keystroke marks it for rebuild, and the frame after that is the parsed
/// document. **A live preview is a binding** (ADR-0296).
///
/// What is here in Java is the frame round it: the heading and the paragraph under
/// it, which is what every screen in the gallery has and what a `split-pane` has
/// nowhere to put.
///
/// ## Not scrolled, on purpose
///
/// [Screen] wraps every other screen in a viewport and deliberately not this one:
/// the preview pane owns a `scroll` of its own and §2.4 bans nested same-axis
/// scrollers — the same exception the Navigation screen is. A `split-pane` also
/// needs a height to divide, and a viewport gives its content as much as it asks
/// for.
///
/// @param panes what `markdown.kdl` built, inflated once by [Screen]
public record MarkdownScreen(Widget panes) implements Widget.Stateless {

    private static final String NOTE = "An editor and a preview of one property — the `text-area` writes it and"
            + " `markdown-view` reads it with `bind=`, so nothing in the showcase connects the two. The sample on"
            + " the left is every construct the parser reports: headings, marks, links, images, both kinds of"
            + " list, tasks, quotations, fences, a table, entities and raw HTML. Markdown is parsed by md4c"
            + " inside libgoldberry and drawn as ordinary widgets — a column of rows of `text`, under the same"
            + " cascade as every control in this window. The preview is not a picture of a document: press a"
            + " link, tick a box, and the line above it says what this application was handed — a destination"
            + " or an ordinal, which is all the toolkit knows. Drag across the text to select it, and"
            + " Ctrl+C takes a copy with the spaces and the line breaks the document implies.";

    @Override
    public Widget build(BuildContext context) {
        return new Column(
                List.of(new SectionHeader("Markdown"), new Text(NOTE, Attributes.NONE.classes("prose")), panes),
                Attributes.NONE.id("screen-markdown").classes("screen"));
    }
}
