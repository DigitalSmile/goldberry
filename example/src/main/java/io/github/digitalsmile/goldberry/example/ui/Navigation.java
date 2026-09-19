package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs.Breadcrumbs;
import io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs.Crumb;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Navigation** screen: the three ways this toolkit moves a reader around a
/// window that is bigger than itself.
///
/// A tab strip *replaces* what is on screen, an affixed header *keeps* something
/// on screen while the rest of it travels, a trail *says where you are*, and a
/// tour *walks* somebody through things that are already there. They are on one
/// screen because choosing between them is a single decision and it used to be
/// spread over three tabs ([ADR-0222]).
///
/// ## Why this screen is not in a viewport
///
/// [Scrolling] and [Console] own one each, and §2.4 bans nested same-axis
/// scrollers — a screen inside the gallery's `scroll` that held another one would
/// be exactly that. The wall is what makes the ban affordable: two columns of
/// cards are half as tall as one, so the screen fits without needing to scroll at
/// all.
///
/// @param startTour the application's, because starting a tour needs a `Host` and
///                  a widget has none (ADR-0121)
public record Navigation(ShowcaseModel model, ShowcaseModel.Actions actions, Runnable startTour)
        implements Widget.Stateless {

    private static final String NOTE =
            "A strip replaces what is on screen, an affixed header keeps something on it while"
                    + " the rest travels, a trail says where you are, and a tour walks you through"
                    + " what is already there."
                    + " This screen is the one the gallery does not put in a viewport: two"
                    + " of the cards below own one, and §2.4 bans a scroller inside a scroller.";

    @Override
    public Widget build(BuildContext context) {
        return new Wall(
                "navigation",
                "Navigation",
                NOTE,
                2,
                Masonry.UNSET,
                List.of(
                        new TabsDemo(model, actions),
                        trail(),
                        new WizardDemo(),
                        new Scrolling(),
                        new Console(),
                        tour()));
    }

    /// §6's `breadcrumbs`, driven by the model's path ([ADR-0306]).
    ///
    /// The card is here rather than in a document for the reason the road card on
    /// the Basic screen is: the crumbs are **one per element of a list that
    /// changes**, and §8's markup has no way to describe that. A static trail
    /// would have been writable in KDL and would have shown nothing — the point
    /// of this widget is what it does when the path gets too long for the row.
    ///
    /// **Every crumb gets the same handler**, which is the arrangement the widget
    /// is designed around: a trail is built from a loop, so the last one is
    /// handed a `press` it must not run, and the trail silently demotes it rather
    /// than making the common case an error.
    private Widget trail() {
        var path = model.path();
        var crumbs = new ArrayList<Widget>(path.size());
        for (var depth = 0; depth < path.size(); depth++) {
            var steps = depth + 1;
            crumbs.add(new Crumb(path.get(depth), () -> actions.goUp(steps))
                    .keyed(path.get(depth))
                    .id("crumb-" + depth));
        }
        return Notifications.card(
                "trail-card",
                "The path to here",
                List.of(
                        new Breadcrumbs(crumbs.toArray(Widget[]::new)).id("path"),
                        new Text(
                                "Click a step to go back up it. The last crumb is where you are and is"
                                        + " not a link — the trail decides that, not the document, so a"
                                        + " loop that hands every crumb the same action still ends"
                                        + " somewhere inert.",
                                Attributes.NONE.classes("caption")),
                        new Row(
                                List.of(new Button("Go deeper", actions::goDeeper).id("go-deeper")),
                                Attributes.NONE.id("trail-actions")),
                        new Text(
                                "Past four steps the middle collapses into a … that opens a menu of"
                                        + " what it hid. Nothing is elided inside a name: a truncated"
                                        + " folder is worse than a hidden one, because it still looks"
                                        + " like a name.",
                                Attributes.NONE.classes("caption"))));
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
