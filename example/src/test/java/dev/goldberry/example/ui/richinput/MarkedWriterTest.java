package dev.goldberry.example.ui.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.text.mark.MarkedText;

/// Writing marked text a stretch at a time.
class MarkedWriterTest {

    @Test
    @DisplayName("each stretch is marked where it lands, under every kind it names")
    void stretches() {
        var text = MarkedWriter.start()
                .plain("Ship ")
                .with("it", Style.BOLD, Style.ITALIC)
                .plain(" now")
                .text();
        assertEquals("Ship it now", text.text());
        assertEquals(List.of(new Mark(5, 7, Style.BOLD), new Mark(5, 7, Style.ITALIC)), text.marks());
    }

    @Test
    @DisplayName("two stretches of one kind side by side are one mark")
    void merges() {
        var text = MarkedWriter.start()
                .with("bo", Style.BOLD)
                .with("ld", Style.BOLD)
                .text();
        assertEquals(MarkedText.of("bold").mark(0, 4, Style.BOLD), text);
    }

    @Test
    @DisplayName("nothing written is empty")
    void empty() {
        assertEquals(MarkedText.EMPTY, MarkedWriter.start().text());
    }
}
