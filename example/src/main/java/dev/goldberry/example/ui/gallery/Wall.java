package dev.goldberry.example.ui.gallery;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
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
    public static final int COLUMN_WIDTH = 360;

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

    /// A wall of exactly `columns` columns, for cards that need the width: a table
    /// whose columns are fixed, or a strip of steps that does not wrap.
    public static Wall inColumns(
            String id, String title, String summary, DocLink doc, int columns, List<? extends Widget> cards) {
        return new Wall(id, title, summary, doc, columns, Masonry.UNSET, List.copyOf(cards));
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

    @Override
    public Widget build(BuildContext context) {
        return new Column(
                List.of(
                        new ScreenHeader(title, summary, doc),
                        new Masonry(
                                cards,
                                columns,
                                minColumnWidth,
                                Attributes.NONE.id(id + "-wall").classes("wall"))),
                Attributes.NONE.id("screen-" + id).classes("screen"));
    }
}
