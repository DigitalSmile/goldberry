package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.form.richinput.FormatButton;
import dev.goldberry.widgets.form.richinput.RichInput;

/// The bar card: the stock bar, one of the application's own, or none.
class BarCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("each choice gives the field its buttons, and the caption says which")
    void choices() {
        try (var card = new CardSession(new BarCard())) {
            assertEquals(
                    FormatButton.defaults(),
                    card.widget(BarCard.FIELD, RichInput.class).formatting());
            choose(card, BarCard.SHORT);
            assertEquals(
                    BarCard.SHORT_BAR,
                    card.widget(BarCard.FIELD, RichInput.class).formatting());
            assertEquals("4 buttons: Strong, Emphasis, Monospace, Add a link", card.text("rich-bar-says"));
            choose(card, BarCard.NONE);
            assertEquals(List.of(), card.widget(BarCard.FIELD, RichInput.class).formatting());
            assertEquals("No bar: the keys and the text menu still format.", card.text("rich-bar-says"));
        }
    }

    private static void choose(CardSession card, String value) {
        var choice = card.widget(BarCard.CHOICE, Segmented.class);
        Objects.requireNonNull(choice.onChange()).accept(value);
        card.session.frame();
    }
}
