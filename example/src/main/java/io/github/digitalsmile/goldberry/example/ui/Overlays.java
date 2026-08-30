package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;

/// The **Overlays** screen: everything that opens *over* this window, and
/// everything that opens *in* it.
///
/// Two halves that belong on one screen because the question a reader has is
/// which of the two a thing should be. A `dialog` and a `toast` float: they leave
/// the layout alone and go away again. A `message` does not: it is part of the
/// column, it moves what is under it, and it stays until the application stops
/// describing it. Putting them a tab apart made that a comparison nobody could
/// make ([ADR-0222]).
///
/// ## What is markup and what is not
///
/// `overlays.kdl` has the three cards that are buttons and a menu bar — every one
/// of them a `press=` naming an action the window registered, which is exactly
/// what a document says well. [Notifications] has the three that are a *list the
/// application edits*: a banner arrives when something describes one and goes
/// when the description stops, and §8's markup is data ([ADR-0175]).
///
/// @param cards what `overlays.kdl` built, inflated once by [Screen]
public record Overlays(Masonry cards) implements Widget.Stateless {

    private static final String NOTE =
            "§7's four layers. A menu opens a platform window of its own, a HUD floats in this"
                    + " window's layer, a dialog covers it and traps the keyboard, and a toast"
                    + " stacks in a corner and leaves. A message does none of that: it is part"
                    + " of the column, and it stays.";

    @Override
    public Widget build(BuildContext context) {
        return Wall.of("overlays", "Overlays", NOTE, cards,
                Notifications.cards().toArray(Widget[]::new));
    }
}
