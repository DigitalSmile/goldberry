package dev.goldberry.html.marked;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.CODE;
import static dev.goldberry.text.mark.MarkKind.Style.ITALIC;
import static dev.goldberry.text.mark.MarkKind.Style.SPOILER;
import static dev.goldberry.text.mark.MarkKind.Style.STRIKE;
import static dev.goldberry.text.mark.MarkKind.Style.UNDERLINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.markdown.MarkdownRequirement;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Custom;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;
import dev.goldberry.text.mark.MarkedText;

/// Marked text to HTML and back, and HTML pasted from elsewhere.
class MarkedHtmlTest {

    private static final Link SITE = new Link(URI.create("https://goldberry.dev/a?b=1&c=2"));

    @BeforeEach
    void setUp() {
        // Entities are resolved from libgoldberry's table, as md4c is.
        MarkdownRequirement.enforce();
    }

    private static MarkedText marked(String text, Object... marks) {
        var list = new ArrayList<Mark>();
        for (var i = 0; i < marks.length; i += 3) {
            list.add(new Mark((int) marks[i], (int) marks[i + 1], (MarkKind) marks[i + 2]));
        }
        return new MarkedText(text, list);
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("each kind as its element, escaped, a newline a br")
        void kinds() {
            assertEquals(
                    "<strong>a</strong> <em>b</em> <u>c</u> <s>d</s> <code>e</code> <span class=\"spoiler\">f</span>"
                            + "<br><a href=\"https://goldberry.dev/a?b=1&amp;c=2\">x &lt;y&gt;</a>",
                    MarkedHtml.write(marked(
                            "a b c d e f\nx <y>",
                            0,
                            1,
                            BOLD,
                            2,
                            3,
                            ITALIC,
                            4,
                            5,
                            UNDERLINE,
                            6,
                            7,
                            STRIKE,
                            8,
                            9,
                            CODE,
                            10,
                            11,
                            SPOILER,
                            12,
                            17,
                            SITE)));
        }

        @Test
        @DisplayName("overlapping marks nest properly")
        void nests() {
            assertEquals(
                    "<strong>bo<em>ld</em></strong><em> it</em>",
                    MarkedHtml.write(marked("bold it", 0, 4, BOLD, 2, 7, ITALIC)));
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("the formatting elements as marks")
        void elements() {
            assertEquals(
                    marked(
                            "a b c d e f g",
                            0,
                            1,
                            BOLD,
                            2,
                            3,
                            ITALIC,
                            4,
                            5,
                            UNDERLINE,
                            6,
                            7,
                            STRIKE,
                            8,
                            9,
                            CODE,
                            10,
                            11,
                            SPOILER,
                            12,
                            13,
                            SITE),
                    MarkedHtml.read(
                            "<b>a</b> <i>b</i> <u>c</u> <del>d</del> <kbd>e</kbd> "
                                    + "<span class=\"tg-spoiler\">f</span> <a href=\"https://goldberry.dev/a?b=1&amp;c=2\">g</a>"));
        }

        @Test
        @DisplayName("a selection copied from a browser: the fragment's words and formatting, nothing else")
        void browserSelection() {
            // What Chromium puts on the clipboard for "Ship it **today**, see
            // the docs." selected in a page.
            var captured = "<html><body>\n<!--StartFragment--><p style=\"color: rgb(32, 33, 36); font-family: Roboto,"
                    + " sans-serif; font-size: 16px;\">Ship it <strong>today</strong>, see <a href=\"https://"
                    + "goldberry.dev/docs\" style=\"color: rgb(26, 115, 232);\">the docs</a>.</p>\n<p style=\""
                    + "color: rgb(32, 33, 36);\">Second   paragraph\n with <code>code()</code>.</p><!--EndFragment-->\n"
                    + "</body>\n</html>";
            var read = MarkedHtml.read(captured);
            assertEquals("Ship it today, see the docs.\nSecond paragraph with code().", read.text());
            assertEquals(
                    marked(
                            read.text(),
                            8,
                            13,
                            BOLD,
                            19,
                            27,
                            new Link(URI.create("https://goldberry.dev/docs")),
                            51,
                            57,
                            CODE),
                    read);
        }

        @Test
        @DisplayName("a document editor's paste: the wrapping bold that says it is not, and styles that say what is")
        void documentEditor() {
            var captured = "<meta charset=\"utf-8\"><b style=\"font-weight:normal;\" id=\"docs-internal-guid-1\">"
                    + "<span style=\"font-weight:700;font-style:normal\">Bold</span><span style=\"font-weight:400\">"
                    + " and </span><span style=\"font-style:italic;text-decoration:underline\">both</span>"
                    + "<span style=\"font-family:'Roboto Mono',monospace\"> x()</span></b>";
            assertEquals(
                    marked("Bold and both x()", 0, 4, BOLD, 9, 13, ITALIC, 9, 13, UNDERLINE, 14, 17, CODE),
                    MarkedHtml.read(captured));
        }

        @Test
        @DisplayName("unknown elements are their words on lines of their own, and scripts, styles and heads nothing")
        void unknown() {
            assertEquals(
                    MarkedText.of("Title\nwords\nhere"),
                    MarkedHtml.read("<head><title>no</title><style>p{}</style></head><my-callout><h1>Title</h1>"
                            + "<x-tag>words</x-tag> <blink>here</blink></my-callout><script>alert(1)</script>"));
        }

        @Test
        @DisplayName("br breaks a line, list items start with a dash, pre keeps its spacing as code")
        void structure() {
            assertEquals(MarkedText.of("a\nb"), MarkedHtml.read("a<br>b"));
            assertEquals(MarkedText.of("- one\n- two"), MarkedHtml.read("<ul><li>one</li><li>two</li></ul>"));
            assertEquals(marked("if x:\n  y", 0, 9, CODE), MarkedHtml.read("<pre>if x:\n  y\n</pre>"));
        }

        @Test
        @DisplayName("a mark's whitespace falls outside it")
        void trimmed() {
            assertEquals(marked("a b c", 2, 3, BOLD), MarkedHtml.read("a<b> b </b>c"));
        }
    }

    @Test
    @DisplayName("every kind round-trips through HTML")
    void roundTrip() {
        var text = marked(
                "bold it u s c() secret site @Ann #rel\nnext <&> line",
                0,
                7,
                BOLD,
                5,
                7,
                ITALIC,
                8,
                9,
                UNDERLINE,
                10,
                11,
                STRIKE,
                12,
                15,
                CODE,
                16,
                22,
                SPOILER,
                23,
                27,
                SITE,
                28,
                32,
                new Mention("user:1"),
                33,
                37,
                new Custom("hashtag", "rel", Behaviour.REFERENCE));
        assertEquals(text, MarkedHtml.read(MarkedHtml.write(text)));
    }

    @Nested
    @DisplayName("the clipboard format")
    class Format {

        @Test
        @DisplayName("is text/html, written as UTF-8 behind a meta, read back")
        void roundTrip() {
            var format = MarkedHtml.format();
            assertEquals("text/html", format.mime());
            var text = marked("naïve bold", 6, 10, BOLD);
            var bytes = format.write(text);
            assertTrue(new String(bytes, StandardCharsets.UTF_8).startsWith(HtmlFormat.PREFIX));
            assertEquals(text, format.read(bytes).orElseThrow());
        }

        @Test
        @DisplayName("reads UTF-16 with a byte-order mark, and skips the Windows header")
        void encodings() {
            var format = MarkedHtml.format();
            var utf16 = new byte[] {(byte) 0xFF, (byte) 0xFE};
            var body = "<b>x</b>".getBytes(StandardCharsets.UTF_16LE);
            var both = new byte[utf16.length + body.length];
            System.arraycopy(utf16, 0, both, 0, 2);
            System.arraycopy(body, 0, both, 2, body.length);
            assertEquals(marked("x", 0, 1, BOLD), format.read(both).orElseThrow());

            var windows =
                    "Version:0.9\r\nStartHTML:0000000105\r\nEndHTML:0000000150\r\n<html><body><i>y</i></body></html>";
            assertEquals(
                    marked("y", 0, 1, ITALIC),
                    format.read(windows.getBytes(StandardCharsets.UTF_8)).orElseThrow());
        }

        @Test
        @DisplayName("reads a page with no words as nothing")
        void empty() {
            assertTrue(MarkedHtml.format()
                    .read("<br><p> </p>".getBytes(StandardCharsets.UTF_8))
                    .isEmpty());
        }
    }
}
