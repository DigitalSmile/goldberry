package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Navigation** screen: the three ways this toolkit moves a reader around a
/// window that is bigger than itself.
///
/// A tab strip *replaces* what is on screen, an affixed header *keeps* something
/// on screen while the rest of it travels, and a tour *walks* somebody through
/// things that are already there. They are on one screen because choosing between
/// them is a single decision and it used to be spread over three tabs
/// ([ADR-0222]).
///
/// ## Why this screen is not in a viewport
///
/// [Scrolling] owns one, and §2.4 bans nested same-axis scrollers — a screen
/// inside the gallery's `scroll` that held another one would be exactly that. The
/// wall is what makes the ban affordable: two columns of cards are half as tall
/// as one, so the screen fits without needing to scroll at all.
///
/// @param startTour the application's, because starting a tour needs a `Host` and
///                  a widget has none (ADR-0121)
public record Navigation(ShowcaseModel model, ShowcaseModel.Actions actions, Runnable startTour)
        implements Widget.Stateless {

    private static final String NOTE =
            "A strip replaces what is on screen, an affixed header keeps something on it while"
                    + " the rest travels, and a tour walks you through what is already there."
                    + " This screen is the one the gallery does not put in a viewport: the"
                    + " card below owns one, and §2.4 bans a scroller inside a scroller.";

    @Override
    public Widget build(BuildContext context) {
        return new Wall(
                "navigation", "Navigation", NOTE, 2, List.of(new TabsDemo(model, actions), new Scrolling(), tour()));
    }

    /// The tour's own card. A button and a paragraph, because everything else
    /// about a tour happens over the *other* cards on this screen — which is why
    /// starting one is the application's and not this widget's.
    private Widget tour() {
        return Notifications.card(
                "tour-card",
                "A guided walk",
                List.of(
                        new Text(
                                "A tour is a veil with a hole in it, a ring around the thing being"
                                        + " named, and a card beside it. Each stop waits for a frame before it"
                                        + " positions itself, because where a widget *is* is a fact about the"
                                        + " frame that was painted rather than one this button could compute.",
                                Attributes.NONE.classes("caption")),
                        new Button("Take the tour", startTour)
                                .withAttributes(
                                        Attributes.NONE.id("tour-button").classes("primary")),
                        new Text(
                                "Escape ends it; Enter and the arrow keys move between stops. The"
                                        + " three stops are on this screen, so nothing has to switch tabs"
                                        + " underneath you.",
                                Attributes.NONE.classes("caption"))));
    }
}
