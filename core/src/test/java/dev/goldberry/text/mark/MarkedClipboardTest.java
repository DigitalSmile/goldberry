package dev.goldberry.text.mark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Custom;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;
import dev.goldberry.text.mark.MarkKind.Style;

/// The toolkit's clipboard type: every kind survives the trip, and bytes it
/// did not write are read as nothing.
class MarkedClipboardTest {

    @Test
    @DisplayName("every kind, and text with newlines and spaces, round-trips")
    void roundTrip() {
        var text = MarkedText.of("bold and code\nsee @Ann #tag link")
                .mark(0, 4, Style.BOLD)
                .mark(9, 13, Style.CODE)
                .mark(9, 13, Style.SPOILER)
                .mark(18, 22, new Mention("user one/1"))
                .mark(23, 27, new Custom("hashtag", "tag with spaces", Behaviour.REFERENCE))
                .mark(28, 32, new Link(URI.create("https://goldberry.dev/a?b=c d".replace(" ", "%20"))));
        assertEquals(text, MarkedClipboard.decode(MarkedClipboard.encode(text)).orElseThrow());
    }

    @Test
    @DisplayName("a custom kind's behaviour and an empty value survive")
    void custom() {
        var kind = new Custom("emoji", "", new Behaviour(false, true, true));
        var text = MarkedText.of("x").mark(0, 1, kind);
        assertEquals(
                List.of(new Mark(0, 1, kind)),
                MarkedClipboard.decode(MarkedClipboard.encode(text))
                        .orElseThrow()
                        .marks());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "hello",
                "goldberry-marked 1\n",
                "goldberry-marked 1\n1\n0 9 bold\nshort",
                "goldberry-marked 1\n1\n0 1 sparkly\nx",
                "goldberry-marked 1\n-1\nx",
                "goldberry-marked 2\n0\nx",
            })
    @DisplayName("bytes the toolkit did not write are read as nothing")
    void malformed(String bytes) {
        assertTrue(
                MarkedClipboard.decode(bytes.getBytes(StandardCharsets.UTF_8)).isEmpty());
    }
}
