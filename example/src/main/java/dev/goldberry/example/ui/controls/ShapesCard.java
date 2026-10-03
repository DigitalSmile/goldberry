package dev.goldberry.example.ui.controls;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.icon.Icon;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Corner;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.button.Floated;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The button's shapes and its one placement: `outlined`, which composes with
/// `danger`; `square`; the circle an icon-only button is without being told; and
/// `float`, which lifts a button out of this card into the window's corner.
///
/// Java because the icon-only button has no label to fall back on: in markup its
/// icon is a registry lookup, and here it is an object.
///
/// Read more: [`button`](https://goldberry.dev/docs/components/buttons.html#button).
final class ShapesCard {

    private ShapesCard() {}

    /// The card, every button pressing `press`.
    static Card of(Icon plus, Runnable press) {
        return new ShowcaseCard(
                        "shapes-card",
                        "Shapes, and a floating button",
                        "Outlined composes with danger, square drops the radius, and an icon-only button is a"
                                + " circle that needs a name. The + in the window's corner is a button on this card,"
                                + " floated out of it.",
                        DocLink.to("components/buttons", "button"))
                .of(
                        new Row(
                                List.of(
                                        new Button("Outlined", press).styled(Button.OUTLINED),
                                        new Button("Danger", press).styled(Button.OUTLINED, "danger"),
                                        new Button("Square", press).styled(Button.SQUARE),
                                        new Button(
                                                "",
                                                plus,
                                                press,
                                                false,
                                                Attributes.NONE
                                                        .id("circle-button")
                                                        .name("March a league"))),
                                Attributes.NONE.id("shapes-row")),
                        new Text(
                                "The floating button is in the bottom-right corner.",
                                Attributes.NONE.classes("caption")),
                        new Floated(
                                new Button(
                                        "",
                                        plus,
                                        press,
                                        false,
                                        Attributes.NONE
                                                .id("float-button")
                                                .classes("primary")
                                                .name("March a league")),
                                Corner.BOTTOM_END));
    }
}
