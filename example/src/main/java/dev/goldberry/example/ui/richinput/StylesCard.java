package dev.goldberry.example.ui.richinput;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.Text;

/// One value in two fields: the toolkit's looks, and a class of the
/// showcase's whose rules in `chapter-richinput.css` restyle each `mark`,
/// the highlighter stroke of a kind of its own included.
///
/// ```css
/// rich-input.ink > mark.bold { color: var(--gb-accent); }
/// rich-input > mark.highlight { background: …; }
/// ```
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#styling).
public record StylesCard() implements Widget.Stateless {

    /// The card's id.
    public static final String ID = "rich-styles-card";

    /// The field in the toolkit's looks.
    public static final String PLAIN = "rich-styles-plain";

    /// The field in the showcase's.
    public static final String INK = "rich-styles-ink";

    /// The class whose rules restyle the marks.
    public static final String INK_CLASS = "ink";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Marks in your own style",
            "Each set of marks is a mark node under the field with a class per kind, so a stylesheet restyles"
                    + " them: rich-input.ink > mark.bold. A kind of your own, here a highlighter, is a class too.",
            DocLink.to("components/forms", "styling"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(
                caption("The toolkit's looks"),
                new RichInput(Samples.RESTYLED, _ -> {}).maxRows(3).id(PLAIN),
                caption("rich-input.ink"),
                new RichInput(Samples.RESTYLED, _ -> {})
                        .maxRows(3)
                        .withAttributes(Attributes.NONE.id(INK).classes(INK_CLASS)));
    }

    private static Widget caption(String words) {
        return new Text(words, Attributes.NONE.classes("caption"));
    }
}
