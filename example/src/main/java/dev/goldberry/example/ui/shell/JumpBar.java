package dev.goldberry.example.ui.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.goldberry.example.ui.search.IndexedCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.text.Text;

/// One line of the showing screen's cards, by title: **On this page**.
///
/// ```text
/// On this page  (Buttons, every variant) (Badges) (Shapes) (Chips) …
/// ```
///
/// A press scrolls the card to the top and rings it. The line scrolls sideways
/// when there are more cards than fit, so it never takes more than one line
/// from the screen under it.
///
/// Read more: [Scroll](https://goldberry.dev/docs/layout/scroll.html).
///
/// @param cards the screen's cards, in the order it builds them
/// @param jump  what a press asks for: the card's id
public record JumpBar(List<IndexedCard> cards, Consumer<String> jump) implements Widget.Stateless {

    /// The bar's id.
    public static final String ID = "jump-bar";

    /// The fewest cards worth a bar: one card is the whole screen.
    public static final int FEWEST = 2;

    public JumpBar {
        cards = List.copyOf(cards);
    }

    /// The id of the chip for the card `id`.
    public static String chipId(String id) {
        return "jump-" + id;
    }

    @Override
    public Widget build(BuildContext context) {
        var chips = new ArrayList<Widget>(cards.size() + 1);
        chips.add(new Text("On this page", Attributes.NONE.classes("jump-label")));
        for (var card : cards) {
            chips.add(new Pressable(
                    "Go to " + card.title(),
                    () -> jump.accept(card.id()),
                    false,
                    List.of(new Text(card.title())),
                    Attributes.NONE.id(chipId(card.id())).classes("jump-chip").tooltip(card.summary())));
        }
        return new Scroll(
                List.of(new Row(chips, Attributes.NONE.classes("jump-row"))),
                ScrollAxis.HORIZONTAL,
                Attributes.NONE.id(ID).classes("jump-bar").name("On this page"));
    }
}
