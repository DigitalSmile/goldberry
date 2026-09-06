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
            + " wiring. The last three cannot be: a set is toggled, a filter is applied"
            + " and a branch is fetched, and all three are the application's.";

    @Override
    public Widget build(BuildContext context) {
        return Wall.of("forms", "Forms", NOTE, cards, Choosers.cards().toArray(Widget[]::new));
    }
}
