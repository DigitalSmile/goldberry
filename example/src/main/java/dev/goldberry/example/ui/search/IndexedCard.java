package dev.goldberry.example.ui.search;

import java.util.Objects;

/// One card as the search index holds it: where it is, what it is called and
/// what it says.
///
/// A screen's own header is held the same way with a blank [#id], so the index
/// carries every screen's summary too.
///
/// @param screen  the gallery screen the card is on
/// @param id      the card's id, which the window scrolls to; blank for the
///                screen's header
/// @param title   the card's title, or the screen's
/// @param summary the card's summary, or the screen's
public record IndexedCard(String screen, String id, String title, String summary) {

    public IndexedCard {
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
    }

    /// Whether this is the screen's header rather than a card on it.
    public boolean isHeader() {
        return id.isEmpty();
    }
}
