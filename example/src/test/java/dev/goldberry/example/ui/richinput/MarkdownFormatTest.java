package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.MarkKind.Style;

/// The showcase's own clipboard format: marked text as `text/markdown`.
class MarkdownFormatTest {

    private final MarkdownFormat format = new MarkdownFormat();

    @Test
    @DisplayName("is offered as text/markdown")
    void mime() {
        assertEquals("text/markdown", format.mime());
    }

    @Test
    @DisplayName("writes Markdown and reads it back")
    void roundTrip() {
        var text =
                MarkedWriter.start().plain("Ship it ").with("today", Style.BOLD).text();
        var bytes = format.write(text);
        assertEquals("Ship it **today**", new String(bytes, StandardCharsets.UTF_8));
        assertEquals(Optional.of(text), format.read(bytes));
    }
}
