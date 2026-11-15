package dev.goldberry.text.mark;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.render.clipboard.Clipboard;
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

    /// A format that writes the text upper-cased under `text/shout`, reads it
    /// back bold, and fails on `boom`.
    private static final MarkedFormat SHOUT = new MarkedFormat() {
        @Override
        public String mime() {
            return "text/shout";
        }

        @Override
        public byte[] write(MarkedText text) {
            return text.text().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Optional<MarkedText> read(byte[] bytes) {
            var text = new String(bytes, StandardCharsets.UTF_8);
            if (text.equals("boom")) {
                throw new IllegalStateException("a format that throws");
            }
            return Optional.of(MarkedText.of(text).mark(0, text.length(), Style.BOLD));
        }
    };

    @Test
    @DisplayName("a copy with marks offers each installed format beside the toolkit's own and the text")
    void copyOffersFormats() {
        var board = new MapClipboard();
        var text = MarkedText.of("hi").mark(0, 2, Style.ITALIC);
        assertTrue(MarkedClipboard.copy(board, text, List.of(SHOUT)));
        assertArrayEquals("HI".getBytes(StandardCharsets.UTF_8), board.read("text/shout"));
        assertTrue(board.has(MarkedClipboard.MIME));
        assertEquals("hi", board.text());
    }

    @Test
    @DisplayName("a plain copy offers only the text")
    void plainCopy() {
        var board = new MapClipboard();
        MarkedClipboard.copy(board, MarkedText.of("hi"), List.of(SHOUT));
        assertFalse(board.has("text/shout"));
    }

    @Test
    @DisplayName("a paste prefers the toolkit's own type, then a format, then the text")
    void pasteOrder() {
        var board = new MapClipboard();
        board.write(Map.of("text/shout", "loud".getBytes(StandardCharsets.UTF_8)));
        board.text("quiet");
        assertEquals(
                MarkedText.of("loud").mark(0, 4, Style.BOLD),
                MarkedClipboard.paste(board, List.of(SHOUT)).orElseThrow());
        assertEquals(MarkedText.of("quiet"), MarkedClipboard.paste(board).orElseThrow(), "with no format installed");

        var ours = MarkedText.of("ours").mark(0, 4, Style.CODE);
        board.write(Map.of(
                MarkedClipboard.MIME,
                MarkedClipboard.encode(ours),
                "text/shout",
                "x".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ours, MarkedClipboard.paste(board, List.of(SHOUT)).orElseThrow());
    }

    @Test
    @DisplayName("a format that throws is passed over for the text")
    void formatThatThrows() {
        var board = new MapClipboard();
        board.write(Map.of(
                "text/shout",
                "boom".getBytes(StandardCharsets.UTF_8),
                "text/plain;charset=utf-8",
                "words".getBytes(StandardCharsets.UTF_8)));
        board.text("words");
        assertEquals(
                MarkedText.of("words"),
                MarkedClipboard.paste(board, List.of(SHOUT)).orElseThrow());
    }

    /// A clipboard of bytes by type, with the text apart, as a platform's is.
    private static final class MapClipboard implements Clipboard {

        private final Map<String, byte[]> offered = new LinkedHashMap<>();

        private String text = "";

        @Override
        public boolean hasText() {
            return !text.isEmpty();
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public boolean text(String value) {
            text = value;
            return true;
        }

        @Override
        public boolean has(String mime) {
            return offered.containsKey(mime);
        }

        @Override
        public byte[] read(String mime) {
            return offered.getOrDefault(mime, new byte[0]).clone();
        }

        @Override
        public boolean write(Map<String, byte[]> byMime) {
            offered.clear();
            offered.putAll(byMime);
            var plain = byMime.get("text/plain");
            if (plain != null) {
                text = new String(plain, StandardCharsets.UTF_8);
            }
            return true;
        }
    }
}
