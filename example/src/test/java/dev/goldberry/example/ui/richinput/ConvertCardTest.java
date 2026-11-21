package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.html.marked.MarkedHtml;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.markdown.marked.MarkedMarkdown;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;

/// The conversions card: the field written out three ways as it is typed, and
/// Markdown read back into it.
class ConvertCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the output follows each view and each keystroke")
    void views() {
        try (var card = new CardSession(new ConvertCard())) {
            assertEquals(MarkedMarkdown.write(Samples.EVERY_KIND), card.text(ConvertCard.OUTPUT));
            choose(card, "html");
            assertEquals(MarkedHtml.write(Samples.EVERY_KIND), card.text(ConvertCard.OUTPUT));
            choose(card, "entities");
            assertEquals(Entities.describe(Samples.EVERY_KIND), card.text(ConvertCard.OUTPUT));
            choose(card, "markdown");
            assertTrue(card.session.focus(ConvertCard.FIELD));
            card.session.key(Shortcut.primary(Key.A).toString());
            card.session.key(Key.BACKSPACE);
            card.session.key(Shortcut.primary(Key.B).toString());
            card.session.type("loud");
            card.session.frame();
            assertEquals("**loud**", card.text(ConvertCard.OUTPUT));
            var preview = card.widget(ConvertCard.PREVIEW, RichText.class);
            assertEquals("loud", preview.runs().getFirst().text());
            assertTrue(preview.runs().getFirst().attributes().classes().contains("bold"));
        }
    }

    @Test
    @DisplayName("the Markdown below is read into the field")
    void readsMarkdown() {
        try (var card = new CardSession(new ConvertCard())) {
            card.session.click(ConvertCard.READ);
            card.session.frame();
            var value = card.widget(ConvertCard.FIELD, RichInput.class).value();
            assertTrue(value.text().startsWith("Read this, underlined"), value.text());
            assertTrue(value.has(5, 9, Style.BOLD), "**this** is bold: " + value);
            assertTrue(
                    card.widget(ConvertCard.PREVIEW, RichText.class).runs().stream()
                            .anyMatch(Run::spoiler),
                    "||hidden|| is a spoiler in the preview");
        }
    }

    private static void choose(CardSession card, String view) {
        Objects.requireNonNull(card.widget(ConvertCard.CHOICE, Segmented.class).onChange())
                .accept(view);
        card.session.frame();
    }
}
