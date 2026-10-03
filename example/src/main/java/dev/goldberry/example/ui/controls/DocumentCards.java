package dev.goldberry.example.ui.controls;

import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.panel.masonry.Masonry;

/// The cards a document wrote, taken one at a time so a screen can put them in
/// the guide's order among the cards it builds in Java.
final class DocumentCards {

    private DocumentCards() {}

    /// The card called `id` in `document`, refused by name when the document has
    /// none, so a renamed card is a failure that says which.
    ///
    /// @param file the document's name, for the message
    static Widget card(Masonry document, String file, String id) {
        return document.children().stream()
                .filter(child -> child instanceof Card card
                        && id.equals(card.attributes().id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(file + " has no card with id " + id));
    }
}
