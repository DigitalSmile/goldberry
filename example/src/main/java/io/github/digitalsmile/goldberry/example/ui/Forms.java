package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;

/// The **Forms** screen: §4's fields in every state they have, and §3's three
/// choosers that hold more than a name.
///
/// The two halves belong on one screen because they answer one question — how a
/// value gets *into* an application — and because the difference between them is
/// the interesting part: a `text-input` holds its own text, caret and undo stack
/// and needs no wiring at all, while a `multiple`, an `autocomplete` and a `tree`
/// each need a channel only Java has ([ADR-0222]).
///
/// @param cards what `forms.kdl` built, inflated once by [Screen]
public record Forms(Masonry cards) implements Widget.Stateless {

    private static final String NOTE = "§4's fields and §3's choosers. Nine of these cards are forms.kdl — a field"
            + " holds its own text, caret and undo stack, so a wall of them needs no"
            + " wiring. The last four cannot be: a set is toggled, a filter is applied,"
            + " a branch is fetched and a class set is computed, and all four are the"
            + " application's.";

    @Override
    public Widget build(BuildContext context) {
        // The choosers, then the text-property card. Last because it is the tallest
        // and a masonry places by column height: a tall card at the front leaves a
        // long tail of empty column behind it.
        var java = new java.util.ArrayList<>(Choosers.cards());
        java.add(new TextStylingCard());
        return Wall.of("forms", "Forms", NOTE, cards, java.toArray(Widget[]::new));
    }
}
