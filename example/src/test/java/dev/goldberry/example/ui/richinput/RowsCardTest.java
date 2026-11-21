package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.widgets.form.richinput.RichInput;

/// The rows card: one line, growing, and fixed.
class RowsCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("each field is given the rows it shows")
    void options() {
        try (var card = new CardSession(new RowsCard())) {
            var one = card.widget(RowsCard.ONE, RichInput.class);
            assertEquals(1, one.rows());
            assertEquals(1, one.maxRows());
            var growing = card.widget(RowsCard.GROWING, RichInput.class);
            assertEquals(1, growing.rows());
            assertEquals(4, growing.maxRows());
            var fixed = card.widget(RowsCard.FIXED, RichInput.class);
            assertEquals(3, fixed.rows());
            assertEquals(3, fixed.maxRows());
        }
    }

    @Test
    @DisplayName("three rows stand taller than one, and a long text grows its field")
    void heights() {
        try (var card = new CardSession(new RowsCard())) {
            var one = card.rect(RowsCard.ONE).height();
            assertTrue(card.rect(RowsCard.FIXED).height() > one * 2, "three rows against one");
            assertTrue(card.rect(RowsCard.GROWING).height() > one, "the long sample wraps and grows its field");
            assertTrue(card.session.focus(RowsCard.ONE));
            card.session.type("a line long enough to run past the field's own width and keep going for a while");
            card.session.frame();
            assertEquals(one, card.rect(RowsCard.ONE).height(), 0.5, "one line stays one line");
        }
    }
}
