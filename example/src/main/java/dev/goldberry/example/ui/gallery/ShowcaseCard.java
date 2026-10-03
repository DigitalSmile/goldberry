package dev.goldberry.example.ui.gallery;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Link;
import dev.goldberry.widgets.text.Text;

/// One card on a wall: a section of the guide, shown.
///
/// Every card has the same three things above whatever it demonstrates:
///
/// ```text
/// ┌───────────────────────────────────────┐
/// │ BUTTONS                        Docs ↗ │  row.card-head: the title and the link
/// │ A button runs an action when pressed. │  text.card-summary
/// │ [Default] [Primary] [Ghost] [Danger]  │  the demonstration
/// └───────────────────────────────────────┘
/// ```
///
/// A card written in a document is the same tree, by hand:
///
/// ```kdl
/// card id="button-card" class="wall-card" {
///   row class="card-head" {
///     text class="card-title" "Buttons"
///     spacer
///     link class="card-docs" href="https://goldberry.dev/docs/components/buttons.html#button" "Docs"
///   }
///   text class="card-summary" "A button runs an action when pressed."
///   button "Default"
/// }
/// ```
///
/// A test opens every screen and holds every `wall-card` to this shape, so the two
/// spellings cannot drift.
///
/// A card with nothing to click is a **reference** card: the head and the summary,
/// and the guide for the rest. It is how a chapter about building, testing or
/// releasing has a place in the gallery.
///
/// Read more: [`card`](https://goldberry.dev/docs/components/panels.html#card).
///
/// @param id      the card's id, which is also its key on the wall
/// @param title   what the card is about, short enough to fit beside the link
/// @param summary one or two sentences, held to [Summaries#require]
/// @param doc     the section of the guide the card's link opens
/// @param classes classes beside `wall-card`, for a card a stylesheet sizes
public record ShowcaseCard(String id, String title, String summary, DocLink doc, Set<String> classes) {

    /// Every card's class.
    public static final String CARD = "wall-card";

    /// A card with no demonstration.
    public static final String REFERENCE = "reference-card";

    public ShowcaseCard {
        Objects.requireNonNull(doc, "doc");
        if (id.isBlank() || title.isBlank()) {
            throw new IllegalArgumentException("a card needs an id and a title");
        }
        Summaries.require("the \"" + title + "\" card", summary);
        classes = Set.copyOf(classes);
    }

    /// A card with no classes beyond its own.
    public ShowcaseCard(String id, String title, String summary, DocLink doc) {
        this(id, title, summary, doc, Set.of());
    }

    /// This card, with more classes beside `wall-card`.
    public ShowcaseCard classed(String... more) {
        var all = new LinkedHashSet<>(classes);
        all.addAll(List.of(more));
        return new ShowcaseCard(id, title, summary, doc, all);
    }

    /// The card, holding `content` under its head and summary.
    public Card of(Widget... content) {
        return of(List.of(content));
    }

    /// The card, holding `content` under its head and summary.
    public Card of(List<? extends Widget> content) {
        var children = new ArrayList<Widget>(content.size() + 2);
        children.add(head(title, doc));
        children.add(new Text(summary, Attributes.NONE.classes("card-summary")));
        children.addAll(content);
        var all = new LinkedHashSet<String>();
        all.add(CARD);
        all.addAll(classes);
        return new Card(children, Attributes.NONE.id(id).classes(all.toArray(String[]::new)));
    }

    /// The card with nothing to click: the guide is the rest of it.
    public Card reference() {
        return classed(REFERENCE).of();
    }

    /// A card's head: its title, and the link that opens the guide at `doc`.
    public static Row head(String title, DocLink doc) {
        return new Row(
                List.of(
                        new Text(title, Attributes.NONE.classes("card-title")),
                        new Spacer(),
                        docsLink("card-docs", title, doc)),
                Attributes.NONE.classes("card-head"));
    }

    /// The link to the guide, with the address as its tooltip and a name that
    /// says where it goes, because every one of them reads "Docs".
    static Link docsLink(String className, String title, DocLink doc) {
        return Link.external("Docs", doc.url())
                .withAttributes(Attributes.NONE
                        .classes(className)
                        .tooltip(doc.url())
                        .name("Read about " + title + " in the guide"));
    }
}
