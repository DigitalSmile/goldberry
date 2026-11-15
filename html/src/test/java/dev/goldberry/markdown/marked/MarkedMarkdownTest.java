package dev.goldberry.markdown.marked;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static dev.goldberry.text.mark.MarkKind.Style.CODE;
import static dev.goldberry.text.mark.MarkKind.Style.ITALIC;
import static dev.goldberry.text.mark.MarkKind.Style.SPOILER;
import static dev.goldberry.text.mark.MarkKind.Style.STRIKE;
import static dev.goldberry.text.mark.MarkKind.Style.UNDERLINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.markdown.MarkdownRequirement;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Custom;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;
import dev.goldberry.text.mark.MarkedText;

/// Marked text to Markdown and back: each kind written as the dialect writes
/// it, read back as itself, and text that looks like markup kept as text.
class MarkedMarkdownTest {

    private static final Link SITE = new Link(URI.create("https://goldberry.dev"));

    @BeforeEach
    void setUp() {
        MarkdownRequirement.enforce();
    }

    private static MarkedText marked(String text, Object... marks) {
        var list = new ArrayList<Mark>();
        for (var i = 0; i < marks.length; i += 3) {
            list.add(new Mark((int) marks[i], (int) marks[i + 1], (MarkKind) marks[i + 2]));
        }
        return new MarkedText(text, list);
    }

    private static void roundTrips(MarkedText text) {
        var written = MarkedMarkdown.write(text);
        assertEquals(text, MarkedMarkdown.read(written), () -> "through " + written);
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("each style in its delimiters")
        void styles() {
            assertEquals(
                    "**a** *b* _c_ ~~d~~ `e` ||f||",
                    MarkedMarkdown.write(marked(
                            "a b c d e f",
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
                            SPOILER)));
        }

        @Test
        @DisplayName("a link, a mention and a custom kind as links")
        void references() {
            var tag = new Custom("hashtag", "rel ease", Behaviour.REFERENCE);
            assertEquals(
                    "[site](https://goldberry.dev) [@Ann](mention:user%3A1) [#rel](mark:hashtag/rel%20ease)",
                    MarkedMarkdown.write(
                            marked("site @Ann #rel", 0, 4, SITE, 5, 9, new Mention("user:1"), 10, 14, tag)));
        }

        @Test
        @DisplayName("whitespace at emphasis's edge goes outside its delimiters, and inside a link's")
        void whitespaceOutside() {
            assertEquals("a **b** c", MarkedMarkdown.write(marked("a b c", 1, 4, BOLD)));
            assertEquals(
                    "**see [the](https://goldberry.dev)**[ site](https://goldberry.dev)",
                    MarkedMarkdown.write(marked("see the site", 0, 7, BOLD, 4, 12, SITE)));
        }

        @Test
        @DisplayName("overlapping marks are nested, the longer outside")
        void overlap() {
            assertEquals("**bo*ld*** *it*", MarkedMarkdown.write(marked("bold it", 0, 4, BOLD, 2, 7, ITALIC)));
        }

        @Test
        @DisplayName("code with backticks in it gets a longer fence")
        void codeFence() {
            assertEquals("``a`b``", MarkedMarkdown.write(marked("a`b", 0, 3, CODE)));
            assertEquals("`` `x ``", MarkedMarkdown.write(marked("`x", 0, 2, CODE)));
        }

        @Test
        @DisplayName("text that looks like markup is escaped")
        void escapes() {
            assertEquals("2 \\* 3 \\_x\\_ \\[y\\] \\|\\|", MarkedMarkdown.write(MarkedText.of("2 * 3 _x_ [y] ||")));
            assertEquals(
                    "\\# not a heading\n\\- not a list\n1\\. not a list",
                    MarkedMarkdown.write(MarkedText.of("# not a heading\n- not a list\n1. not a list")));
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("each delimiter as its style")
        void styles() {
            assertEquals(
                    marked(
                            "a b c d e f",
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
                            SPOILER),
                    MarkedMarkdown.read("**a** *b* _c_ ~~d~~ `e` ||f||"));
        }

        @Test
        @DisplayName("a spoiler around other marks, and one across lines")
        void spoilers() {
            assertEquals(marked("a b", 0, 3, SPOILER, 2, 3, BOLD), MarkedMarkdown.read("||a **b**||"));
            assertEquals(marked("a\nb", 0, 3, SPOILER), MarkedMarkdown.read("||a\nb||"));
        }

        @Test
        @DisplayName("an escaped pipe, pipes in code and an odd edge stay pipes")
        void pipes() {
            assertEquals(MarkedText.of("a || b"), MarkedMarkdown.read("a \\|\\| b"));
            assertEquals(marked("x||y", 0, 4, CODE), MarkedMarkdown.read("`x||y`"));
            assertEquals(MarkedText.of("a || b"), MarkedMarkdown.read("a || b"));
        }

        @Test
        @DisplayName("a line break is a newline, and a blank line two")
        void lines() {
            assertEquals(MarkedText.of("a\nb\n\nc"), MarkedMarkdown.read("a\nb\n\nc"));
        }

        @Test
        @DisplayName("blocks marks cannot hold are read as their words")
        void blocks() {
            assertEquals(MarkedText.of("Title\n\n- one\n- two"), MarkedMarkdown.read("# Title\n\n- one\n- two"));
            assertEquals(marked("let x = 1", 0, 9, CODE), MarkedMarkdown.read("```\nlet x = 1\n```"));
        }

        @Test
        @DisplayName("a link to an address that is not one keeps its words plain")
        void badAddress() {
            assertEquals(MarkedText.of("bad"), MarkedMarkdown.read("[bad](mention:)"));
        }

        @Test
        @DisplayName("a mention link reads as the atom")
        void mention() {
            assertEquals(
                    marked("hi @Ann Lee", 3, 11, new Mention("user:42")),
                    MarkedMarkdown.read("hi [@Ann Lee](mention:user%3A42)"));
        }
    }

    @Nested
    @DisplayName("round trips")
    class RoundTrips {

        @Test
        @DisplayName("every kind")
        void everyKind() {
            roundTrips(marked("bold", 0, 4, BOLD));
            roundTrips(marked("italic", 0, 6, ITALIC));
            roundTrips(marked("under", 0, 5, UNDERLINE));
            roundTrips(marked("struck", 0, 6, STRIKE));
            roundTrips(marked("code()", 0, 6, CODE));
            roundTrips(marked("secret", 0, 6, SPOILER));
            roundTrips(marked("site", 0, 4, SITE));
            roundTrips(marked("@Ann Lee", 0, 8, new Mention("user:42")));
            roundTrips(marked("#rel", 0, 4, new Custom("hashtag", "rel", Behaviour.REFERENCE)));
            roundTrips(marked(":wave:", 0, 6, new Custom("emoji", "1F44B", Behaviour.ATOM)));
        }

        @Test
        @DisplayName("combinations")
        void combinations() {
            roundTrips(marked("bold and italic", 0, 15, BOLD, 9, 15, ITALIC));
            roundTrips(marked("see the site now", 0, 12, BOLD, 8, 16, SITE));
            roundTrips(marked("run x() here", 0, 12, SPOILER, 4, 7, CODE));
            roundTrips(marked("a `tick` in code", 2, 8, CODE));
            roundTrips(marked("ok **not bold** ok", 3, 15, ITALIC));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "plain words",
                    "2 * 3 = 6, a_b_c, [x](y), ~~no~~, `no`, <b>no</b>, AT&T, &amp;",
                    "# not a heading",
                    "> not a quote",
                    "- not a list\n+ nor this\n10) nor this",
                    "back\\slash and || pipes",
                    "line one\nline two\n\nnext paragraph",
                })
        @DisplayName("text that looks like markup stays text")
        void plain(String text) {
            roundTrips(MarkedText.of(text));
        }

        @Test
        @DisplayName("random marks over whole words, two hundred times: every word keeps its kinds")
        void random() {
            var random = new Random(0x601DBE77L);
            var words = List.of("ship", "it", "on", "Friday", "x", "code", "naïve", "日本", "a1");
            MarkKind[] kinds = {BOLD, ITALIC, UNDERLINE, STRIKE, CODE, SPOILER, SITE};
            for (var round = 0; round < 200; round++) {
                var text = new StringBuilder();
                var starts = new ArrayList<Integer>();
                var count = 1 + random.nextInt(7);
                for (var i = 0; i < count; i++) {
                    if (i > 0) {
                        text.append(' ');
                    }
                    starts.add(text.length());
                    text.append(words.get(random.nextInt(words.size())));
                }
                starts.add(text.length() + 1);
                var value = MarkedText.of(text.toString());
                for (var m = random.nextInt(4); m > 0; m--) {
                    var from = random.nextInt(count);
                    var to = from + 1 + random.nextInt(count - from);
                    value = value.mark(starts.get(from), starts.get(to) - 1, kinds[random.nextInt(kinds.length)]);
                }
                var original = value;
                var written = MarkedMarkdown.write(value);
                var read = MarkedMarkdown.read(written);
                assertEquals(value.text(), read.text(), () -> "through " + written);
                for (var at = 0; at < value.length(); at++) {
                    if (!Character.isWhitespace(value.text().charAt(at))) {
                        var where = at;
                        assertEquals(
                                value.kindsAt(at),
                                read.kindsAt(at),
                                () -> "char " + where + " of " + original + " through " + written);
                    }
                }
            }
        }
    }
}
