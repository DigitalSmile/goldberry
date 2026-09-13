package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **HTML** screen: an editor on the left, the same page rendered on the right,
/// live.
///
/// [MarkdownScreen]'s twin, one tab along, and the comparison is the point of having
/// both. The two screens are the same arrangement over the same binding — a
/// `text-area` writing one property and a view reading it — so what differs between
/// them is what differs between the two halves of `goldberry-html`: the parser
/// (md4c in C, against a tokenizer in Java) and the vocabulary: a fixed dialect
/// against open tags, with definition lists and a `class` a stylesheet can reach
/// (ADR-0298). What they share is everything a reader does — links, images and the
/// binding under both (ADR-0300).
///
/// ## Why it is a document *and* a class, like every other screen
///
/// The interesting half is `html.kdl`, and it is three nodes: a `text-area` that
/// writes `html.source`, an `html-view` that reads it, and a `text` bound to what the
/// last pressed link handed over. Nothing in this application connects them.
///
/// ## Not scrolled, on purpose
///
/// [Screen] wraps every other screen in a viewport and deliberately not this one or
/// the Markdown screen: the preview pane owns a `scroll` of its own and §2.4 bans
/// nested same-axis scrollers.
///
/// @param panes what `html.kdl` built, inflated once by [Screen]
public record HtmlScreen(Widget panes) implements Widget.Stateless {

    private static final String NOTE = "The other half of the optional module, and the same binding one tab along:"
            + " the `text-area` writes a property and `html-view` reads it. There is no litehtml under this:"
            + " the page is parsed in Java into a tree of records and folded into the same `column`, `row` and"
            + " `text` the Markdown preview is made of, so a page follows the theme like every control round it."
            + " Press a link and the line above the preview says what this application was handed, because"
            + " following one is never the toolkit's decision — and the picture is drawn because the"
            + " application said where it is. Drag across the page to select it and Ctrl+C takes a copy."
            + " What HTML has that Markdown does not is the vocabulary: any tag, definition lists, and a"
            + " `class` a stylesheet can meet halfway.";

    @Override
    public Widget build(BuildContext context) {
        return new Column(
                List.of(new SectionHeader("HTML"), new Text(NOTE, Attributes.NONE.classes("prose")), panes),
                Attributes.NONE.id("screen-html").classes("screen"));
    }
}
