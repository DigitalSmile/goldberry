package dev.goldberry.example.ui.controls;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Choices** screen: `checkbox`, `toggle`, `radio`, `radio-group`,
/// `segmented` and the four kinds of `select`.
///
/// The controls bound to the application's values are `choices.kdl`. The group
/// with a caption among its options and the selects whose value is the card's own
/// are built here.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html).
///
/// @param context what the screen is built from
public record ChoicesChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/choices");

    private static final String FILE = "choices.kdl";

    @Override
    public Widget build(BuildContext buildContext) {
        var document = context.documents().wall(FILE);
        var cards = new ArrayList<Widget>(List.of(
                DocumentCards.card(document, FILE, "switch-card"),
                DocumentCards.card(document, FILE, "choices-toggle"),
                DocumentCards.card(document, FILE, "choices-radio"),
                new RadioGroupCard(),
                DocumentCards.card(document, FILE, "theme-card")));
        cards.addAll(Choosers.cards());
        return Wall.of(
                "choices",
                "Choices",
                "A tick, a switch, one of several, and one or more from a list. Every control here is controlled:"
                        + " it asks for a change, the application sets the value, and the control follows it.",
                CHAPTER,
                cards);
    }
}
