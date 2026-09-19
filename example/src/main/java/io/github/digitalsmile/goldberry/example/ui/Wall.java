package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// What every screen in the gallery **is**: a heading, a line of prose, and a
/// wall of cards.
///
/// One shape for the gallery's walls, and the reason it is a type rather than a
/// convention is that it was a convention first and four of the screens drifted
/// off it — one had its heading inside the wall, one had no prose, two disagreed
/// about whether the caption was `.caption` or `.prose`. A screen that is a
/// *value* cannot drift
/// (ADR-0222).
///
/// ## Why a masonry and not a grid
///
/// Because a card is as tall as its contents and no two of these are the same
/// height: a `split-pane` is not the height of a `statistic`, and a card of six
/// checkboxes is not the height of one paragraph. Laid out in equal rows every
/// card is as tall as the tallest in its row, so the short ones sit in acres of
/// empty surface and the wall reads as badly aligned rather than as varied. That
/// is the case `masonry` exists for
/// (ADR-0196).
///
/// ## How wide the wall is, which is two numbers because it is two modes
///
/// Not a stylesheet's business either way, because `masonry` distributes children
/// in Java rather than in CSS — the count is a constructor argument for the same
/// reason the packing is (ADR-0196). But it is a *count* only for the screens
/// that want one: a `min-column-width` is a screen saying how narrow its cards
/// may get and letting the window decide the rest (ADR-0436), and the two are
/// exclusive, so a wall carries both numbers and hands them on exactly as the
/// document wrote them. `Masonry.UNSET` is the one that was not named.
///
/// @param id             the screen's name, which is also its `#screen-<id>` and
///                       its `#<id>-wall`
/// @param columns        how many columns, or `Masonry.UNSET` for a wall that
///                       counts its own
/// @param minColumnWidth how narrow a column may get, or `Masonry.UNSET` for a
///                       wall that was given a count
/// @param cards          every card on the screen, in the order they are offered
///                       to the columns
record Wall(String id, String title, String note, int columns, int minColumnWidth, List<Widget> cards)
        implements Widget.Stateless {

    Wall {
        cards = List.copyOf(cards == null ? List.of() : cards);
    }

    /// A wall built from a document's, plus whatever Java had to add.
    ///
    /// The order matters and is the caller's: a masonry places each card under
    /// whichever column is shortest, so a card appended here lands *after* the
    /// document's rather than beside any particular one of them.
    static Wall of(String id, String title, String note, Masonry document, Widget @Nullable ... extra) {
        var cards = new java.util.ArrayList<>(document.children());
        cards.addAll(List.of(extra));
        return new Wall(id, title, note, document.columns(), document.minColumnWidth(), cards);
    }

    @Override
    public Widget build(BuildContext context) {
        return new Column(
                List.of(
                        new SectionHeader(title),
                        new Text(note, Attributes.NONE.classes("prose")),
                        new Masonry(
                                cards,
                                columns,
                                minColumnWidth,
                                Attributes.NONE.id(id + "-wall").classes("wall"))),
                Attributes.NONE.id("screen-" + id).classes("screen"));
    }
}
