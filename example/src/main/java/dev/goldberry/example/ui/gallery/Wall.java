package dev.goldberry.example.ui.gallery;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.panel.masonry.Masonry;

/// What most screens in the gallery **are**: a [ScreenHeader], then a wall of
/// [ShowcaseCard]s, one per section of the chapter the screen mirrors.
///
/// One shape for every wall, and a type rather than a convention because it was a
/// convention first and the screens drifted off it. A screen that is a *value*
/// cannot drift.
///
/// ## Why a masonry and not a grid
///
/// A card is as tall as its contents and no two are the same height. Laid out in
/// equal rows every card is as tall as the tallest in its row, and the short ones
/// sit in empty surface. That is the case `masonry` exists for.
///
/// ## How wide the wall is
///
/// Two numbers, because it is two modes: a count of columns, or the narrowest a
/// column may get with the window deciding the count. The two are exclusive, and
/// `Masonry.UNSET` is the one that was not named.
///
/// Read more: [Masonry](https://goldberry.dev/docs/layout/masonry.html).
///
/// @param id             the screen's name, which is also its `#screen-<id>` and
///                       its `#<id>-wall`
/// @param title          the screen's heading
/// @param summary        what the screen is for, held to [Summaries#require]
/// @param doc            the chapter the screen mirrors
/// @param columns        how many columns, or `Masonry.UNSET` for a wall that
///                       counts its own
/// @param minColumnWidth how narrow a column may get, or `Masonry.UNSET` for a
///                       wall that was given a count
/// @param cards          every card on the screen, in the order they are offered
///                       to the columns
public record Wall(
        String id, String title, String summary, DocLink doc, int columns, int minColumnWidth, List<Widget> cards)
        implements Widget.Stateless {

    /// The width a wall's columns may narrow to when a screen does not say.
    public static final int COLUMN_WIDTH = 440;

    /// Written out so that the parameters taking null for a default can say so.
    public Wall(
            String id,
            String title,
            String summary,
            DocLink doc,
            int columns,
            int minColumnWidth,
            @Nullable List<Widget> cards) {
        Summaries.require("the " + title + " screen", summary);
        cards = List.copyOf(cards == null ? List.of() : cards);
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.doc = doc;
        this.columns = columns;
        this.minColumnWidth = minColumnWidth;
        this.cards = cards;
    }

    /// A wall that counts its own columns, none narrower than [#COLUMN_WIDTH].
    public static Wall of(String id, String title, String summary, DocLink doc, List<? extends Widget> cards) {
        return new Wall(id, title, summary, doc, Masonry.UNSET, COLUMN_WIDTH, List.copyOf(cards));
    }

    /// The width a wall of wide cards may narrow to: a table whose columns are
    /// fixed, or a strip of steps that does not wrap.
    public static final int WIDE_COLUMN_WIDTH = 560;

    /// A wall of cards that need the width, counting its own columns: one
    /// beside the sidebar in a window at its restored size, two in a wide one.
    ///
    /// Better than a fixed count of two, which was right for a window the
    /// gallery had to itself and cut the cards' rows once a sidebar took a
    /// quarter of it.
    public static Wall wide(String id, String title, String summary, DocLink doc, List<? extends Widget> cards) {
        return new Wall(id, title, summary, doc, Masonry.UNSET, WIDE_COLUMN_WIDTH, List.copyOf(cards));
    }

    /// A wall built from a document's cards, plus whatever Java had to add.
    ///
    /// The order matters and is the caller's: a masonry places each card under
    /// whichever column is shortest, so a card appended here lands *after* the
    /// document's rather than beside any particular one of them. The document's
    /// column settings are kept.
    public static Wall of(
            String id, String title, String summary, DocLink doc, Masonry document, List<? extends Widget> extra) {
        var cards = new ArrayList<Widget>(document.children());
        cards.addAll(extra);
        return new Wall(id, title, summary, doc, document.columns(), document.minColumnWidth(), cards);
    }

    /// Whether `card` belongs in the wide wall: a [WideCard], or a card that
    /// carries [ShowcaseCard#WIDE].
    public static boolean isWide(Widget card) {
        return switch (card) {
            case WideCard _ -> true;
            case Card built -> built.attributes().classes().contains(ShowcaseCard.WIDE);
            default -> false;
        };
    }

    /// The header, the wall, and — when any card asked for one — a second wall
    /// of wide columns under it for the cards a reader types into.
    ///
    /// Under rather than among: a masonry's columns are one width, and a text
    /// area in a column sized for a row of buttons is a text area a sentence
    /// wide. The wide wall has as many columns as fit at [#WIDE_COLUMN_WIDTH] —
    /// one beside the sidebar at the window's restored size, two on a wide
    /// screen.
    @Override
    public Widget build(BuildContext context) {
        var regular = new ArrayList<Widget>(cards.size());
        var wide = new ArrayList<Widget>();
        for (var card : cards) {
            (isWide(card) ? wide : regular).add(card);
        }
        var children = new ArrayList<Widget>(3);
        children.add(new ScreenHeader(title, summary, doc));
        children.add(new Masonry(
                regular,
                columns,
                minColumnWidth,
                Attributes.NONE.id(id + "-wall").classes("wall")));
        if (!wide.isEmpty()) {
            children.add(new Masonry(
                    wide,
                    Masonry.UNSET,
                    WIDE_COLUMN_WIDTH,
                    Attributes.NONE.id(id + "-wide-wall").classes("wall", "wide-wall")));
        }
        return new Column(children, Attributes.NONE.id("screen-" + id).classes("screen"));
    }
}
