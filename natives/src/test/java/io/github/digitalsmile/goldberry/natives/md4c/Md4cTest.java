package io.github.digitalsmile.goldberry.natives.md4c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.md4c.enums.BlockType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.CellAlign;
import io.github.digitalsmile.goldberry.natives.md4c.enums.MarkdownFlag;
import io.github.digitalsmile.goldberry.natives.md4c.enums.SpanType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.TextType;

/// The Markdown binding, against the library this build produced.
///
/// Everything here goes through the real `goldberry_md_parse`, because the thing
/// worth testing is the seam: the encoder in `goldberry_shim.c` and
/// `MarkdownStream` are two halves of one wire format, and only running both says
/// they agree. A hand-written buffer would test the decoder against itself.
@DisplayName("md4c through libgoldberry")
class Md4cTest {

    private static final Set<MarkdownFlag> GITHUB = Set.of(
            MarkdownFlag.TABLES,
            MarkdownFlag.STRIKETHROUGH,
            MarkdownFlag.TASK_LISTS,
            MarkdownFlag.PERMISSIVE_URL_AUTOLINKS);

    @BeforeAll
    static void requireLibrary() {
        NativeLibraryRequirement.enforce();
    }

    private static List<MarkdownEvent> parse(String markdown) {
        return Md4c.get().parse(markdown, GITHUB);
    }

    /// The text of every [MarkdownEvent.Text] event, joined.
    private static String words(List<MarkdownEvent> events) {
        var out = new StringBuilder();
        for (var event : events) {
            if (event instanceof MarkdownEvent.Text(var type, var text) && type != TextType.CODE) {
                out.append(text);
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("wraps every document in a DOC pair, empty ones included")
    void documentIsAlwaysThere() {
        var events = parse("");
        assertEquals(
                List.of(
                        new MarkdownEvent.EnterBlock(BlockType.DOC, BlockDetail.NONE),
                        new MarkdownEvent.LeaveBlock(BlockType.DOC)),
                events,
                "an empty document is still a document");
    }

    @Test
    @DisplayName("nests every enter against its leave")
    void streamIsWellNested() {
        var events = parse("""
                # Title

                Text with *emphasis*, `code` and a [link](http://example.com).

                > quoted

                - one
                - two

                1. first
                2. second

                | a | b |
                |:--|--:|
                | 1 | 2 |
                """);
        var open = new ArrayDeque<Object>();
        for (var event : events) {
            switch (event) {
                case MarkdownEvent.EnterBlock(var type, var _) -> open.push(type);
                case MarkdownEvent.EnterSpan(var type, var _) -> open.push(type);
                case MarkdownEvent.LeaveBlock(var type) -> assertEquals(type, open.pop(), "block closed out of order");
                case MarkdownEvent.LeaveSpan(var type) -> assertEquals(type, open.pop(), "span closed out of order");
                case MarkdownEvent.Text(var _, var _) -> {}
            }
        }
        assertTrue(open.isEmpty(), "the stream left " + open + " open");
    }

    @Nested
    @DisplayName("a block's detail")
    class Details {

        @Test
        @DisplayName("carries a heading's level")
        void headingLevel() {
            var events = parse("### Third\n");
            var heading = events.stream()
                    .filter(e -> e instanceof MarkdownEvent.EnterBlock(var type, var _) && type == BlockType.H)
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    new BlockDetail.Heading(3),
                    ((MarkdownEvent.EnterBlock) heading).detail(),
                    "### is level three, and the number comes from the C side's own struct read");
        }

        @Test
        @DisplayName("says a list is tight and which bullet it used")
        void bulletList() {
            var detail = detailOf(parse("+ one\n+ two\n"), BlockType.UL);
            assertEquals(new BlockDetail.BulletList(true, '+'), detail);
        }

        @Test
        @DisplayName("says where a numbered list starts and what delimits it")
        void numberedList() {
            var detail = detailOf(parse("7) seven\n8) eight\n"), BlockType.OL);
            assertEquals(new BlockDetail.NumberedList(7, true, ')'), detail);
        }

        @Test
        @DisplayName("reports a task item, its mark and where the mark is")
        void taskItem() {
            var detail = detailOf(parse("- [x] done\n"), BlockType.LI);
            var item = assertInstanceOf(BlockDetail.Item.class, detail);
            assertTrue(item.task(), "a task list item");
            assertTrue(item.checked(), "x means done");
            assertEquals('x', item.taskMark());
            assertEquals(3, item.taskMarkOffset(), "the x is the fourth byte of `- [x] done`");
        }

        @Test
        @DisplayName("separates a fence's info string from its first word")
        void codeFence() {
            var detail = detailOf(parse("```java extra\nint x;\n```\n"), BlockType.CODE);
            var code = assertInstanceOf(BlockDetail.Code.class, detail);
            assertEquals("java extra", code.info().plain());
            assertEquals("java", code.language().plain(), "the language is the first word, which md4c splits out");
            assertEquals('`', code.fence());
            assertTrue(code.fenced());
        }

        @Test
        @DisplayName("reports an indented code block as fenced by nothing")
        void indentedCode() {
            var detail = detailOf(parse("    int x;\n"), BlockType.CODE);
            var code = assertInstanceOf(BlockDetail.Code.class, detail);
            assertFalse(code.fenced(), "an indented block has no fence character");
            assertFalse(code.info().isPresent(), "and no info string either");
        }

        @Test
        @DisplayName("counts a table's rows and columns, and aligns its cells")
        void table() {
            var events = parse("""
                    | a | b |
                    |:--|--:|
                    | 1 | 2 |
                    | 3 | 4 |
                    """);
            assertEquals(new BlockDetail.Table(2, 1, 2), detailOf(events, BlockType.TABLE));
            var heads = events.stream()
                    .filter(e -> e instanceof MarkdownEvent.EnterBlock(var type, var _) && type == BlockType.TH)
                    .map(e -> ((MarkdownEvent.EnterBlock) e).detail())
                    .toList();
            assertEquals(
                    List.of(new BlockDetail.Cell(CellAlign.LEFT), new BlockDetail.Cell(CellAlign.RIGHT)),
                    heads,
                    ":-- is left and --: is right");
        }

        private BlockDetail detailOf(List<MarkdownEvent> events, BlockType wanted) {
            for (var event : events) {
                if (event instanceof MarkdownEvent.EnterBlock(var type, var detail) && type == wanted) {
                    return detail;
                }
            }
            throw new AssertionError("no " + wanted + " block in " + events);
        }
    }

    @Nested
    @DisplayName("an attribute")
    class Attributes {

        @Test
        @DisplayName("arrives in parts, so an entity inside a URL is still an entity")
        void hrefKeepsItsParts() {
            var events = parse("[a](http://x/?p=1&amp;q=2)\n");
            var link = events.stream()
                    .filter(e -> e instanceof MarkdownEvent.EnterSpan(var type, var _) && type == SpanType.A)
                    .map(e -> (SpanDetail.Link) ((MarkdownEvent.EnterSpan) e).detail())
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    List.of(
                            new MarkdownAttribute.Part(TextType.NORMAL, "http://x/?p=1"),
                            new MarkdownAttribute.Part(TextType.ENTITY, "&amp;"),
                            new MarkdownAttribute.Part(TextType.NORMAL, "q=2")),
                    link.href().parts(),
                    "flattening this first is how a renderer emits &amp;amp;");
            assertEquals("http://x/?p=1&amp;q=2", link.href().plain(), "and plain() puts it back together");
            assertFalse(link.title().isPresent(), "no title was written");
            assertFalse(link.autolink(), "brackets, not a bare URL");
        }

        @Test
        @DisplayName("says when a link was a bare URL")
        void autolink() {
            var events = parse("see http://example.com now\n");
            var link = events.stream()
                    .filter(e -> e instanceof MarkdownEvent.EnterSpan(var type, var _) && type == SpanType.A)
                    .map(e -> (SpanDetail.Link) ((MarkdownEvent.EnterSpan) e).detail())
                    .findFirst()
                    .orElseThrow();
            assertTrue(link.autolink(), "permissive URL autolinks are on in this dialect");
            assertEquals("http://example.com", link.href().plain());
        }

        @Test
        @DisplayName("is absent rather than empty when nobody wrote one")
        void absentAttribute() {
            var events = parse("![alt](pic.png)\n");
            var image = events.stream()
                    .filter(e -> e instanceof MarkdownEvent.EnterSpan(var type, var _) && type == SpanType.IMG)
                    .map(e -> (SpanDetail.Image) ((MarkdownEvent.EnterSpan) e).detail())
                    .findFirst()
                    .orElseThrow();
            assertEquals("pic.png", image.src().plain());
            assertEquals(MarkdownAttribute.NONE, image.title());
            assertEquals("alt", words(events), "an image's alt text arrives as the text inside the span");
        }
    }

    @Nested
    @DisplayName("text runs")
    class Runs {

        @Test
        @DisplayName("mark an entity rather than resolving it")
        void entityIsMarked() {
            var events = parse("AT&amp;T\n");
            assertTrue(
                    events.contains(new MarkdownEvent.Text(TextType.ENTITY, "&amp;")),
                    "md4c hands the reference over as written; who resolves it depends on the output");
        }

        @Test
        @DisplayName("tell a hard break from a soft one")
        void breaks() {
            var hard = parse("one  \ntwo\n");
            var soft = parse("one\ntwo\n");
            // Both carry the newline they stand for, which is md4c's own choice: a
            // renderer that ignores the type still emits something sensible.
            assertTrue(hard.contains(new MarkdownEvent.Text(TextType.BR, "\n")), "two trailing spaces is a hard break");
            assertTrue(soft.contains(new MarkdownEvent.Text(TextType.SOFTBR, "\n")), "a bare newline is a soft one");
        }

        @Test
        @DisplayName("keep code verbatim, newlines and all")
        void codeIsVerbatim() {
            var events = parse("```\na\n  b\n```\n");
            var code = new StringBuilder();
            for (var event : events) {
                if (event instanceof MarkdownEvent.Text(var type, var text) && type == TextType.CODE) {
                    code.append(text);
                }
            }
            assertEquals("a\n  b\n", code.toString(), "indentation inside a fence is content");
        }

        @Test
        @DisplayName("carry text outside the BMP unbroken")
        void astralText() {
            var events = parse("a 🌿 b\n");
            assertEquals("a 🌿 b", words(events), "UTF-8 on the wire, surrogate pair back in Java");
        }
    }

    @Nested
    @DisplayName("the dialect")
    class Dialect {

        @Test
        @DisplayName("decides whether a table is a table")
        void tablesAreOptional() {
            var document = "| a |\n|---|\n| 1 |\n";
            assertTrue(
                    Md4c.get().parse(document, Set.of(MarkdownFlag.TABLES)).stream()
                            .anyMatch(e ->
                                    e instanceof MarkdownEvent.EnterBlock(var type, var _) && type == BlockType.TABLE),
                    "with the flag");
            assertFalse(
                    Md4c.get().parse(document, Set.of()).stream()
                            .anyMatch(e ->
                                    e instanceof MarkdownEvent.EnterBlock(var type, var _) && type == BlockType.TABLE),
                    "and without it, a paragraph full of pipes");
        }

        @Test
        @DisplayName("refuses a null document rather than parsing nothing")
        void nullIsRefused() {
            assertThrows(NullPointerException.class, () -> Md4c.get().parse(null, Set.of()));
            assertThrows(NullPointerException.class, () -> Md4c.get().parse("x", null));
        }
    }

    /// The spans md4c 0.6.0 renumbered by inserting `MD_SPAN_INS` in front of
    /// `MD_SPAN_DEL`.
    ///
    /// The layout verifier already holds each value to the compiler; this holds
    /// the *wire* to it. A span decoded one ordinal off is not an error — `~~x~~`
    /// arrives as LaTeX — so every one of them is parsed through the real library
    /// and must come back as itself.
    @Nested
    @DisplayName("a span past md4c's MD_SPAN_INS")
    class ShiftedSpans {

        /// One span, the flag that enables it, and a document containing it.
        record Case(SpanType expected, MarkdownFlag flag, String markdown) {
            @Override
            public String toString() {
                return expected.name();
            }
        }

        static List<Case> shifted() {
            return List.of(
                    new Case(SpanType.DEL, MarkdownFlag.STRIKETHROUGH, "a ~~b~~ c\n"),
                    new Case(SpanType.LATEXMATH, MarkdownFlag.LATEX_MATH, "a $b$ c\n"),
                    new Case(SpanType.LATEXMATH_DISPLAY, MarkdownFlag.LATEX_MATH, "a $$b$$ c\n"),
                    new Case(SpanType.WIKILINK, MarkdownFlag.WIKI_LINKS, "a [[b]] c\n"),
                    new Case(SpanType.U, MarkdownFlag.UNDERLINE, "a _b_ c\n"));
        }

        @ParameterizedTest
        @MethodSource("shifted")
        @DisplayName("decodes as itself")
        void decodesAsItself(Case c) {
            var spans = Md4c.get().parse(c.markdown(), Set.of(c.flag())).stream()
                    .<SpanType>mapMulti((e, sink) -> {
                        if (e instanceof MarkdownEvent.EnterSpan(var type, var _)) {
                            sink.accept(type);
                        }
                    })
                    .toList();
            assertEquals(List.of(c.expected()), spans);
        }
    }

    @Nested
    @DisplayName("the entity table")
    class Entities {

        @Test
        @DisplayName("resolves a one-codepoint reference")
        void single() {
            assertEquals(1, Md4c.get().entity("&amp;").length);
            assertEquals('&', Md4c.get().entity("&amp;")[0]);
            assertEquals(0x00A0, Md4c.get().entity("&nbsp;")[0]);
        }

        @Test
        @DisplayName("resolves the handful that need two")
        void pair() {
            var codepoints = Md4c.get().entity("&NotEqualTilde;");
            assertEquals(2, codepoints.length, "this one is a character plus a combining mark");
            assertNotEquals(0, codepoints[1]);
        }

        @Test
        @DisplayName("says nothing for a name that is not one")
        void unknown() {
            assertEquals(0, Md4c.get().entity("&definitelynotanentity;").length);
            assertEquals(0, Md4c.get().entity("").length);
        }
    }
}
