package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.input.key.Key;
import dev.goldberry.widgets.form.richinput.RichInput;

/// The triggers card: four triggers, four kinds, chosen with the keyboard.
class TriggersCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the field answers @, #, : and /")
    void triggers() {
        try (var card = new CardSession(new TriggersCard())) {
            var mentions = card.widget(TriggersCard.FIELD, RichInput.class).mentions();
            assertTrue(mentions != null);
            assertEquals(Set.of("@", "#", ":", "/"), mentions.triggers());
        }
    }

    @Test
    @DisplayName("each choice goes in under its own kind: a mention, a topic, plain text, a command")
    void eachKind() {
        try (var card = new CardSession(new TriggersCard())) {
            assertEquals("Nothing chosen yet.", card.text(TriggersCard.MARKS));
            assertTrue(card.session.focus(TriggersCard.FIELD));
            choose(card, "@an");
            choose(card, "#rel");
            choose(card, ":ro");
            choose(card, "/po");
            card.session.frame();
            assertEquals("mention user:ann · topic release · command poll (atom)", card.text(TriggersCard.MARKS));
            assertEquals(
                    "@Ann Lee #release 🚀 /poll ",
                    card.widget(TriggersCard.FIELD, RichInput.class).value().text());
        }
    }

    private static void choose(CardSession card, String typed) {
        card.session.type(typed);
        card.session.frame();
        card.session.key(Key.ENTER);
        card.session.frame();
    }
}
