package dev.goldberry.example.ui.richinput;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.html.marked.MarkedHtml;
import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.Text;

/// Where a copy keeps its marks. Between two rich fields always, in the
/// toolkit's own type; out to a browser or a word processor, and back, once
/// HTML is installed; as Markdown with a format of the application's own; and
/// never into a plain field, which takes the words.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public record ClipboardCard() implements Widget.Stateless {

    /// The card's id.
    public static final String ID = "rich-clipboard-card";

    /// The field to copy from.
    public static final String SOURCE = "rich-clipboard-source";

    /// The field with nothing installed.
    public static final String OWN = "rich-clipboard-own";

    /// The field with HTML and Markdown installed.
    public static final String FORMATS = "rich-clipboard-formats";

    /// The plain field.
    public static final String PLAIN = "rich-clipboard-plain";

    /// What the field with formats installed reads and writes, in the order a
    /// paste tries them after the toolkit's own type.
    public static final List<MarkedFormat> INSTALLED = List.of(MarkedHtml.format(), new MarkdownFormat());

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Copy and paste",
            "Copy from the first field. A rich field keeps the marks; the one with HTML and Markdown installed"
                    + " also takes a browser's formatting. A plain field takes the words.",
            DocLink.to("guide/text", "selection-and-editing"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(
                caption("Copy from here"),
                new RichInput(Samples.RESTYLED, _ -> {}).maxRows(3).id(SOURCE),
                caption("A rich field: the toolkit's own type"),
                new RichInput(MarkedText.EMPTY, _ -> {})
                        .placeholder("Paste here")
                        .id(OWN),
                caption("HTML and text/markdown installed"),
                new RichInput(MarkedText.EMPTY, _ -> {})
                        .placeholder("Paste from a browser, too")
                        .clipboardFormats(INSTALLED)
                        .id(FORMATS),
                caption("A text-input: words only"),
                new TextInput().placeholder("Paste here").id(PLAIN));
    }

    private static Widget caption(String words) {
        return new Text(words, Attributes.NONE.classes("caption"));
    }
}
