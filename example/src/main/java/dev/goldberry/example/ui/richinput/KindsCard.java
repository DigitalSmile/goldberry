package dev.goldberry.example.ui.richinput;

import java.util.List;
import java.util.stream.Collectors;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.Text;

/// Every kind of mark in one read-only field: the six styles, a link, a
/// mention, a kind of the application's own, two kinds together, and words in
/// Cyrillic and Greek. Read-only still selects and copies, so the marks can be
/// carried to the composer.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public record KindsCard() implements Widget.Stateless {

    /// The card's id.
    public static final String ID = "rich-kinds-card";

    /// The field's id.
    public static final String FIELD = "rich-kinds";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Every kind of mark",
            "Six styles, a link, a mention and a topic of the application's own, alone and together, in three"
                    + " scripts. The field is read-only: select and copy, then paste into the composer.",
            DocLink.to("guide/text", "selection-and-editing"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(
                new RichInput(Samples.EVERY_KIND, _ -> {})
                        .readOnly(true)
                        .rows(4)
                        .maxRows(6)
                        .id(FIELD),
                new Text(
                        "Classes: " + classes(),
                        Attributes.NONE.id("rich-kinds-classes").classes("caption")));
    }

    /// The classes the sample's marks are styled by, in the order they first
    /// appear.
    static String classes() {
        return Samples.EVERY_KIND.marks().stream()
                .map(Mark::kind)
                .map(MarkKind::className)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    /// The kinds the sample holds, for a test.
    static List<MarkKind> kinds() {
        return Samples.EVERY_KIND.marks().stream().map(Mark::kind).distinct().toList();
    }
}
