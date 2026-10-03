package dev.goldberry.example.ui.controls;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Buttons** screen: `button`, `badge`, `chip` and `pressable`, a card or
/// more for each.
///
/// The variants, the badges and the filter chips are `buttons.kdl`. The shapes,
/// the counter, the dismissable tags and the pressable rows are built here,
/// because each answers a question about a value or holds an icon as an object.
///
/// Read more: [Buttons, badges and chips](https://goldberry.dev/docs/components/buttons.html).
///
/// @param context what the screen is built from
public record ButtonsChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/buttons");

    private static final String FILE = "buttons.kdl";

    @Override
    public Widget build(BuildContext buildContext) {
        var document = context.documents().wall(FILE);
        var model = context.model();
        var actions = context.actions();
        return Wall.of(
                "buttons",
                "Buttons",
                "Three stadium-shaped widgets, one you press, one you read and one you choose, and a fourth with"
                        + " no shape at all that makes anything pressable.",
                CHAPTER,
                // Badges second, so no two columns end level when a card is dealt:
                // a masonry gives a card to the shortest column, and a tie is
                // broken by rounding, which differs from one display scale to the
                // next.
                List.of(
                        DocumentCards.card(document, FILE, "button-card"),
                        DocumentCards.card(document, FILE, "badge-card"),
                        ShapesCard.of(context.plus(), Swept.after(model, actions::click)),
                        new RoadCard(model, actions, context.plus()),
                        DocumentCards.card(document, FILE, "chip-card"),
                        new TagsCard(model, actions),
                        new PressableCard()));
    }
}
