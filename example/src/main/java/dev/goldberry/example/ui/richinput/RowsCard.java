package dev.goldberry.example.ui.richinput;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.Text;

/// How tall a field is: one line that scrolls sideways into more, one that
/// grows to four lines and then scrolls, and one fixed at three.
///
/// Read more: [`text-area`](https://goldberry.dev/docs/components/forms.html#text-area).
public record RowsCard() implements Widget.Stateless {

    /// The card's id.
    public static final String ID = "rich-rows-card";

    /// The one-line field.
    public static final String ONE = "rich-rows-one";

    /// The growing field.
    public static final String GROWING = "rich-rows-growing";

    /// The fixed field.
    public static final String FIXED = "rich-rows-fixed";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Growing, then scrolling",
            "A field starts rows lines tall and grows with its text to max-rows, then scrolls, as a text-area"
                    + " does. Type into each one.",
            DocLink.to("components/forms", "text-area"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(
                caption("rows=1 max-rows=1"),
                new RichInput(MarkedText.EMPTY, _ -> {})
                        .placeholder("One line, always")
                        .rows(1)
                        .maxRows(1)
                        .id(ONE),
                caption("rows=1 max-rows=4"),
                new RichInput(Samples.LONG, _ -> {})
                        .placeholder("Grows to four lines")
                        .rows(1)
                        .maxRows(4)
                        .id(GROWING),
                caption("rows=3 max-rows=3"),
                new RichInput(MarkedText.EMPTY, _ -> {})
                        .placeholder("Three lines from the start")
                        .rows(3)
                        .maxRows(3)
                        .id(FIXED));
    }

    private static Widget caption(String words) {
        return new Text(words, Attributes.NONE.classes("caption", "mono"));
    }
}
