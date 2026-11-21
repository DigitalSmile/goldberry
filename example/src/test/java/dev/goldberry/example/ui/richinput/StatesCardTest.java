package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.input.key.Key;
import dev.goldberry.widgets.form.richinput.RichInput;

/// The states card: two switches over one field.
class StatesCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("read-only refuses typing and keeps the text; switched back, the field edits again")
    void readOnly() {
        try (var card = new CardSession(new StatesCard())) {
            assertTrue(card.session.focus(StatesCard.FIELD));
            card.session.key(Key.END);
            card.session.type(" typed");
            card.session.click("rich-states-read-only");
            card.session.frame();
            var field = card.widget(StatesCard.FIELD, RichInput.class);
            assertTrue(field.readOnly());
            assertTrue(field.value().text().endsWith(" typed"), "the text typed before stays");
            assertTrue(card.session.focus(StatesCard.FIELD));
            card.session.key(Key.END);
            card.session.type(" refused");
            card.session.click("rich-states-read-only");
            card.session.frame();
            field = card.widget(StatesCard.FIELD, RichInput.class);
            assertFalse(field.readOnly());
            assertFalse(field.value().text().contains("refused"), "nothing typed while read-only");
        }
    }

    @Test
    @DisplayName("disabled takes the field out of the focus order")
    void disabled() {
        try (var card = new CardSession(new StatesCard())) {
            card.session.click("rich-states-disabled");
            card.session.frame();
            assertTrue(card.widget(StatesCard.FIELD, RichInput.class).disabled());
            assertFalse(card.session.focus(StatesCard.FIELD), "a disabled field takes no focus");
        }
    }
}
