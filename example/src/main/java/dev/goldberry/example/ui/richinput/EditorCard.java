package dev.goldberry.example.ui.richinput;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// Marks without a widget: the canvas `Editor` a `rich-input` is built on,
/// which a canvas drawing its own text uses directly. The sticky note on the
/// Drawing screen is one, and takes `Ctrl+B`.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public record EditorCard() implements Widget.Stateless {

    /// The card's id.
    public static final String ID = "rich-editor-card";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Marks on a canvas",
            "The editor under a rich-input works without one: hand Editor a MarkStyles and it shapes marked words"
                    + " in their own faces and takes the formatting keys. The sticky note on Drawing takes Ctrl+B.",
            DocLink.to("guide/text", "selection-and-editing"));

    @Override
    public Widget build(BuildContext context) {
        return CARD.reference();
    }
}
