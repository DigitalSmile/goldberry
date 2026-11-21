package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.form.textinput.TextInput;

/// The clipboard card. A headless session has no clipboard, so what is checked
/// is what each field installs; the copy and the paste themselves are
/// `RichInputClipboardTest`'s and `RichInputClipboardFormatTest`'s.
class ClipboardCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("one field installs HTML and Markdown, the others the toolkit's own type alone")
    void formats() {
        try (var card = new CardSession(new ClipboardCard())) {
            assertEquals(
                    List.of(),
                    card.widget(ClipboardCard.SOURCE, RichInput.class).clipboardFormats());
            assertEquals(
                    List.of(), card.widget(ClipboardCard.OWN, RichInput.class).clipboardFormats());
            assertEquals(
                    List.of("text/html", "text/markdown"),
                    card.widget(ClipboardCard.FORMATS, RichInput.class).clipboardFormats().stream()
                            .map(MarkedFormat::mime)
                            .toList());
            assertEquals("", card.widget(ClipboardCard.PLAIN, TextInput.class).value());
        }
    }
}
