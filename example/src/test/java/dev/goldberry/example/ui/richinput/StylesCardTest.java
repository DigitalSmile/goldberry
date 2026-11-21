package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.widgets.form.richinput.RichInput;

/// The styles card: one value, two looks.
class StylesCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the two fields hold one value, and only the second has the class")
    void sameValue() {
        try (var card = new CardSession(new StylesCard())) {
            var plain = card.widget(StylesCard.PLAIN, RichInput.class);
            var ink = card.widget(StylesCard.INK, RichInput.class);
            assertEquals(plain.value(), ink.value());
            assertTrue(ink.attributes().classes().contains(StylesCard.INK_CLASS));
        }
    }

    @Test
    @DisplayName("the class's rules draw the same words differently, in either theme")
    void restyled() {
        for (var theme : new Theme[] {Theme.NORD_DARK, Theme.NORD_LIGHT}) {
            try (var card = new CardSession(new StylesCard(), theme)) {
                var image = card.session.frame();
                var plain = card.rect(StylesCard.PLAIN);
                var ink = card.rect(StylesCard.INK);
                assertEquals(plain.width(), ink.width(), 0.5);
                var differing = 0;
                for (var y = 0; y < (int) plain.height(); y++) {
                    for (var x = 0; x < (int) plain.width(); x++) {
                        var a = image.argb((int) plain.left() + x, (int) plain.top() + y);
                        var b = image.argb((int) ink.left() + x, (int) ink.top() + y);
                        if (a != b) {
                            differing++;
                        }
                    }
                }
                assertTrue(differing > 200, theme + ": only " + differing + " pixels differ");
            }
        }
    }
}
