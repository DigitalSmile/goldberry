package io.github.digitalsmile.goldberry.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.markdown.model.Block;
import io.github.digitalsmile.goldberry.markdown.model.BulletList;
import io.github.digitalsmile.goldberry.markdown.model.CellAlignment;
import io.github.digitalsmile.goldberry.markdown.model.Code;
import io.github.digitalsmile.goldberry.markdown.model.CodeBlock;
import io.github.digitalsmile.goldberry.markdown.model.Document;
import io.github.digitalsmile.goldberry.markdown.model.Emphasis;
import io.github.digitalsmile.goldberry.markdown.model.Heading;
import io.github.digitalsmile.goldberry.markdown.model.HtmlBlock;
import io.github.digitalsmile.goldberry.markdown.model.Image;
import io.github.digitalsmile.goldberry.markdown.model.Inline;
import io.github.digitalsmile.goldberry.markdown.model.Item;
import io.github.digitalsmile.goldberry.markdown.model.LineBreak;
import io.github.digitalsmile.goldberry.markdown.model.Link;
import io.github.digitalsmile.goldberry.markdown.model.NumberedList;
import io.github.digitalsmile.goldberry.markdown.model.Paragraph;
import io.github.digitalsmile.goldberry.markdown.model.Quote;
import io.github.digitalsmile.goldberry.markdown.model.RawHtml;
import io.github.digitalsmile.goldberry.markdown.model.Strong;
import io.github.digitalsmile.goldberry.markdown.model.Struck;
import io.github.digitalsmile.goldberry.markdown.model.Table;
import io.github.digitalsmile.goldberry.markdown.model.Text;
import io.github.digitalsmile.goldberry.markdown.model.ThematicBreak;
import io.github.digitalsmile.goldberry.markdown.model.Underlined;
import io.github.digitalsmile.goldberry.markdown.model.WikiLink;

/// Markdown in, model out.
///
/// The assertions are written against whole nodes — `new Paragraph(List.of(new
/// Text("Hi")))` rather than a chain of getters — because the model is records and
/// that is the point of it: a document is a value, so a test says which value it
/// should be.
@DisplayName("parsing Markdown")
class MarkdownTest {

    @BeforeAll
    static void requireLibrary() {
        MarkdownRequirement.enforce();
    }

    private static List<Block> blocks(String markdown) {
        return Markdown.parse(markdown).blocks();
    }

    private static Block first(String markdown) {
        var blocks = blocks(markdown);
        assertFalse(blocks.isEmpty(), "nothing parsed from " + markdown);
        return blocks.getFirst();
    }

    private static List<Inline> content(String markdown) {
        return assertInstanceOf(Paragraph.class, first(markdown)).content();
    }

    private static Text text(String words) {
        return new Text(words);
    }

    @Test
    @DisplayName("returns the empty document for empty text, without going native")
    void emptyText() {
        assertSame(Document.EMPTY, Markdown.parse(""), "there is nothing to parse and nothing to load a library for");
    }

    @Test
    @DisplayName("makes a paragraph of prose")
    void paragraph() {
        assertEquals(List.of(new Paragraph(List.of(text("Just words.")))), blocks("Just words.\n"));
    }

    @Test
    @DisplayName("refuses null rather than parsing nothing")
    void nullIsRefused() {
        assertThrows(NullPointerException.class, () -> Markdown.parse(null));
        assertThrows(NullPointerException.class, () -> Markdown.parse("x", null));
    }

    @Nested
    @DisplayName("blocks")
    class Blocks {

        @Test
        @DisplayName("carry a heading's level")
        void heading() {
            assertEquals(new Heading(2, List.of(text("Title"))), first("## Title\n"));
            assertEquals(
                    6, assertInstanceOf(Heading.class, first("###### Deep\n")).level());
        }

        @Test
        @DisplayName("nest a quote's own blocks inside it")
        void quote() {
            assertEquals(
                    new Quote(List.of(new Paragraph(List.of(text("Quoted."))))),
                    first("> Quoted.\n"),
                    "a quote holds blocks, so a list inside one is a list and not a line of text");
        }

        @Test
        @DisplayName("keep a fence's language, info string and every byte of its code")
        void fencedCode() {
            var block = assertInstanceOf(CodeBlock.class, first("```java x=1\nint a;\n  int b;\n```\n"));
            assertEquals("java", block.language());
            assertEquals("java x=1", block.info());
            assertEquals("int a;\n  int b;\n", block.code(), "indentation inside a fence is content");
            assertEquals(List.of("int a;", "  int b;"), block.lines(), "and the trailing newline is not a line");
        }

        @Test
        @DisplayName("report an indented block as code with no language")
        void indentedCode() {
            var block = assertInstanceOf(CodeBlock.class, first("    int a;\n"));
            assertNull(block.language(), "nobody said what language it was");
            assertEquals("", block.info());
        }

        @Test
        @DisplayName("make a rule of a thematic break")
        void thematicBreak() {
            assertEquals(new ThematicBreak(), first("---\n"));
        }

        @Test
        @DisplayName("keep raw HTML verbatim")
        void htmlBlock() {
            assertEquals(new HtmlBlock("<div>x</div>\n"), first("<div>x</div>\n"));
        }

        @Test
        @DisplayName("drop raw HTML to text when the dialect refuses it")
        void htmlRefused() {
            var syntax = MarkdownSyntax.gitHub().with(MarkdownExtension.NO_HTML);
            var blocks = Markdown.parse("<div>x</div>\n", syntax).blocks();
            assertEquals(
                    List.of(new Paragraph(List.of(text("<div>x</div>")))),
                    blocks,
                    "not markup, so it is words — and the HTML writer will escape them");
        }
    }

    @Nested
    @DisplayName("lists")
    class Lists {

        @Test
        @DisplayName("wrap a tight item's words in a paragraph so every item has one shape")
        void tightItem() {
            var list = assertInstanceOf(BulletList.class, first("- one\n- two\n"));
            assertTrue(list.tight(), "no blank line between the items");
            assertEquals(
                    List.of(
                            new Item(List.of(new Paragraph(List.of(text("one"))))),
                            new Item(List.of(new Paragraph(List.of(text("two")))))),
                    list.items());
        }

        @Test
        @DisplayName("report a loose list as loose")
        void looseList() {
            var list = assertInstanceOf(BulletList.class, first("- one\n\n- two\n"));
            assertFalse(list.tight(), "the blank line is what makes it loose");
        }

        @Test
        @DisplayName("keep a numbered list's first number")
        void numbered() {
            var list = assertInstanceOf(NumberedList.class, first("7. seven\n8. eight\n"));
            assertEquals(7, list.start(), "a renderer that assumed 1 would renumber the document");
            assertEquals(8, list.numberOf(1));
        }

        @Test
        @DisplayName("report a task item and whether it is done")
        void tasks() {
            var list = assertInstanceOf(BulletList.class, first("- [x] done\n- [ ] not\n"));
            var done = list.items().getFirst();
            var open = list.items().get(1);
            assertTrue(done.task());
            assertTrue(done.done());
            assertTrue(open.task());
            assertFalse(open.done());
        }

        @Test
        @DisplayName("leave `[x]` as words when the dialect has no task lists")
        void tasksNeedTheExtension() {
            var list = assertInstanceOf(
                    BulletList.class,
                    Markdown.parse("- [x] done\n", MarkdownSyntax.commonMark())
                            .blocks()
                            .getFirst());
            assertFalse(list.items().getFirst().task(), "without the extension it is a literal bracket");
        }

        @Test
        @DisplayName("nest a list inside an item")
        void nested() {
            var list = assertInstanceOf(BulletList.class, first("- one\n    - deeper\n"));
            var item = list.items().getFirst();
            assertEquals(2, item.blocks().size(), "a paragraph and the list under it");
            assertInstanceOf(BulletList.class, item.blocks().get(1));
        }
    }

    @Nested
    @DisplayName("inline content")
    class Inlines {

        @Test
        @DisplayName("marks emphasis and strength")
        void marks() {
            assertEquals(List.of(text("a "), new Emphasis(List.of(text("b"))), text(" c")), content("a *b* c\n"));
            assertEquals(List.of(new Strong(List.of(text("bold")))), content("**bold**\n"));
        }

        @Test
        @DisplayName("marks strikethrough and underline when the dialect has them")
        void extensionMarks() {
            assertEquals(List.of(new Struck(List.of(text("gone")))), content("~~gone~~\n"));
            var syntax = MarkdownSyntax.of(MarkdownExtension.UNDERLINE);
            assertEquals(
                    List.of(new Underlined(List.of(text("under")))),
                    assertInstanceOf(
                                    Paragraph.class,
                                    Markdown.parse("_under_\n", syntax).blocks().getFirst())
                            .content(),
                    "with UNDERLINE on, `_` is not emphasis any more");
        }

        @Test
        @DisplayName("keeps a code span's spaces")
        void codeSpan() {
            assertEquals(List.of(new Code("a  b")), content("`a  b`\n"));
        }

        @Test
        @DisplayName("joins the pieces md4c splits a sentence into")
        void runsAreMerged() {
            assertEquals(
                    List.of(text("AT&T and Q&A")),
                    content("AT&amp;T and Q&amp;A\n"),
                    "three events in, one run out — a model that kept them apart would be "
                            + "impossible to write a test against");
        }

        @Test
        @DisplayName("resolves entities, numeric and named")
        void entities() {
            assertEquals(List.of(text("& < > ø ø")), content("&amp; &lt; &gt; &#248; &#xF8;\n"));
        }

        @Test
        @DisplayName("leaves a reference that is not an entity as the text it is")
        void unknownEntity() {
            assertEquals(List.of(text("&nope; &#; &#eleven;")), content("&nope; &#; &#eleven;\n"));
        }

        @Test
        @DisplayName("replaces a reference no character answers to")
        void impossibleEntity() {
            // NUL, past the last codepoint, and half a surrogate pair: three things a
            // document can ask for and no string can hold. CommonMark says U+FFFD,
            // which is also the only answer that keeps a NUL out of a widget's text.
            assertEquals(List.of(text("�")), content("&#0;\n"));
            assertEquals(List.of(text("�")), content("&#x110000;\n"));
            assertEquals(List.of(text("�")), content("&#xD800;\n"));
            // Seven hex digits is not a reference at all, and md4c says so before this
            // module sees it -- so it stays as the text somebody typed.
            assertEquals(List.of(text("&#xFFFFFFF;")), content("&#xFFFFFFF;\n"));
        }

        @Test
        @DisplayName("keeps a link's href, title and words, entities resolved")
        void link() {
            assertEquals(
                    List.of(new Link("http://x/?a=1&b=2", "A title", false, List.of(text("go")))),
                    content("[go](http://x/?a=1&amp;b=2 \"A title\")\n"),
                    "a URL with an unresolved entity in it is a URL nobody can follow");
        }

        @Test
        @DisplayName("says when a link was a bare URL")
        void autolink() {
            var link = assertInstanceOf(
                    Link.class, content("see http://example.com\n").get(1));
            assertTrue(link.autolink());
            assertEquals("http://example.com", link.href());
            assertEquals("http://example.com", link.text(), "a bare URL is its own words");
        }

        @Test
        @DisplayName("flattens an image's alt text, marks and all")
        void image() {
            assertEquals(
                    List.of(new Image("pic.png", "T", "bold alt")),
                    content("![**bold** alt](pic.png \"T\")\n"),
                    "what an alt text is has no marks in it");
        }

        @Test
        @DisplayName("keeps a wiki link's target separate from a URL")
        void wikiLink() {
            var syntax = MarkdownSyntax.of(MarkdownExtension.WIKI_LINKS);
            var paragraph = assertInstanceOf(
                    Paragraph.class,
                    Markdown.parse("[[Meeting notes|notes]]\n", syntax).blocks().getFirst());
            assertEquals(List.of(new WikiLink("Meeting notes", List.of(text("notes")))), paragraph.content());
        }

        @Test
        @DisplayName("tells a hard break from a soft one")
        void breaks() {
            assertEquals(
                    List.of(text("one"), new LineBreak(true), text("two")),
                    content("one  \ntwo\n"),
                    "two trailing spaces is a break the author asked for");
            assertEquals(List.of(text("one"), new LineBreak(false), text("two")), content("one\ntwo\n"));
        }

        @Test
        @DisplayName("makes every newline hard when the dialect says so")
        void hardBreaks() {
            var syntax = MarkdownSyntax.gitHub().with(MarkdownExtension.HARD_LINE_BREAKS);
            var paragraph = assertInstanceOf(
                    Paragraph.class,
                    Markdown.parse("one\ntwo\n", syntax).blocks().getFirst());
            assertEquals(List.of(text("one"), new LineBreak(true), text("two")), paragraph.content());
        }

        @Test
        @DisplayName("keeps inline markup as a node of its own")
        void rawHtml() {
            assertEquals(
                    List.of(text("press "), new RawHtml("<kbd>"), text("K"), new RawHtml("</kbd>")),
                    content("press <kbd>K</kbd>\n"));
        }
    }

    @Nested
    @DisplayName("tables")
    class Tables {

        private Table table() {
            return assertInstanceOf(Table.class, first("""
                            | a | b | c |
                            |:--|:-:|--:|
                            | 1 | 2 | 3 |
                            """));
        }

        @Test
        @DisplayName("keep the head and the body apart")
        void halves() {
            var table = table();
            assertEquals(1, table.head().size());
            assertEquals(1, table.body().size());
            assertTrue(table.head().getFirst().header());
            assertFalse(table.body().getFirst().header());
        }

        @Test
        @DisplayName("carry each column's alignment")
        void alignment() {
            var table = table();
            assertEquals(3, table.columns());
            assertEquals(CellAlignment.START, table.alignmentOf(0));
            assertEquals(CellAlignment.CENTER, table.alignmentOf(1));
            assertEquals(CellAlignment.END, table.alignmentOf(2));
            assertEquals(CellAlignment.DEFAULT, table.alignmentOf(9), "no such column");
        }

        @Test
        @DisplayName("hold the cells' words")
        void cells() {
            assertEquals(
                    List.of("a", "b", "c"),
                    table().head().getFirst().cells().stream()
                            .map(cell -> cell.text())
                            .toList());
        }

        @Test
        @DisplayName("are not a table at all without the extension")
        void needTheExtension() {
            var blocks = Markdown.parse("| a |\n|---|\n| 1 |\n", MarkdownSyntax.commonMark())
                    .blocks();
            assertInstanceOf(Paragraph.class, blocks.getFirst(), "a paragraph full of pipes, which is CommonMark");
        }
    }
}
