package io.github.digitalsmile.goldberry.markdown.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The model's own rules — the ones a parser cannot break and a caller can.
///
/// Nothing here parses. These are the invariants the records carry for the benefit of
/// whoever builds a document by hand: a test fixture, an application generating one,
/// or a future writer that goes the other way.
@DisplayName("the document model")
class ModelTest {

    private static List<Inline> words(String text) {
        return List.of(new Text(text));
    }

    @Test
    @DisplayName("refuses a heading outside CommonMark's six levels")
    void headingLevels() {
        assertThrows(IllegalArgumentException.class, () -> new Heading(0, words("x")));
        assertThrows(IllegalArgumentException.class, () -> new Heading(7, words("x")));
        assertEquals(1, new Heading(1, words("x")).level());
    }

    @Test
    @DisplayName("refuses an item that is done without being a task")
    void itemInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new Item(false, true, List.of()));
        assertTrue(new Item(true, true, List.of()).done());
    }

    @Test
    @DisplayName("copies every list it is given, so a document cannot be changed afterwards")
    void defensiveCopies() {
        var blocks = new ArrayList<Block>();
        blocks.add(new ThematicBreak());
        var document = new Document(blocks);
        blocks.clear();
        assertEquals(1, document.blocks().size(), "a document is a value");
        assertThrows(
                UnsupportedOperationException.class, () -> document.blocks().clear());
    }

    @Test
    @DisplayName("refuses null content rather than holding it")
    void nullsAreRefused() {
        assertThrows(NullPointerException.class, () -> new Paragraph(null));
        assertThrows(NullPointerException.class, () -> new Text(null));
        assertThrows(NullPointerException.class, () -> new Link(null, List.of()));
        assertThrows(NullPointerException.class, () -> new CodeBlock(null));
        assertThrows(NullPointerException.class, () -> new Image(null, null, "alt"));
        assertThrows(NullPointerException.class, () -> new WikiLink(null, List.of()));
        assertThrows(NullPointerException.class, () -> new HtmlBlock(null));
        assertThrows(NullPointerException.class, () -> new RawHtml(null));
        assertThrows(NullPointerException.class, () -> new Quote(null));
        assertThrows(NullPointerException.class, () -> new TableCell(false, null, List.of()));
    }

    @Nested
    @DisplayName("a code block")
    class CodeBlocks {

        @Test
        @DisplayName("splits into lines without inventing a last empty one")
        void lines() {
            assertEquals(List.of("a", "b"), new CodeBlock("a\nb\n").lines());
            assertEquals(List.of("a", "b"), new CodeBlock("a\nb").lines(), "an unterminated last line is a line");
            assertEquals(List.of("a", "", "b"), new CodeBlock("a\n\nb\n").lines(), "a blank line is a line");
            assertEquals(List.of(), new CodeBlock("").lines(), "and nothing is no lines at all");
        }
    }

    @Nested
    @DisplayName("a numbered list")
    class Numbered {

        @Test
        @DisplayName("numbers its items from its own start")
        void numbers() {
            var items = List.of(new Item(List.of()), new Item(List.of()));
            var list = new NumberedList(7, true, items);
            assertEquals(7, list.numberOf(0));
            assertEquals(8, list.numberOf(1));
            assertThrows(IndexOutOfBoundsException.class, () -> list.numberOf(2));
            assertThrows(IndexOutOfBoundsException.class, () -> list.numberOf(-1));
        }
    }

    @Nested
    @DisplayName("a table")
    class Tables {

        private final TableRow head = new TableRow(
                true,
                List.of(
                        new TableCell(true, CellAlignment.START, words("a")),
                        new TableCell(true, CellAlignment.END, words("b"))));
        private final TableRow body =
                new TableRow(false, List.of(new TableCell(false, CellAlignment.START, words("1"))));

        @Test
        @DisplayName("counts columns from its widest row")
        void columns() {
            assertEquals(2, new Table(List.of(head), List.of(body)).columns());
            assertEquals(0, new Table(List.of(), List.of()).columns(), "an empty table has no columns");
        }

        @Test
        @DisplayName("reads a column's alignment off whichever row has that column")
        void alignment() {
            var table = new Table(List.of(head), List.of(body));
            assertEquals(CellAlignment.START, table.alignmentOf(0));
            assertEquals(CellAlignment.END, table.alignmentOf(1));
            assertEquals(CellAlignment.DEFAULT, table.alignmentOf(5), "no such column, and no exception either");
        }

        @Test
        @DisplayName("walks head then body as its children")
        void children() {
            assertEquals(List.of(head, body), new Table(List.of(head), List.of(body)).children());
        }
    }

    @Nested
    @DisplayName("flattening inline content")
    class Flattening {

        @Test
        @DisplayName("keeps the words and drops the marks")
        void marks() {
            var content = List.of(
                    new Text("a "),
                    new Strong(List.of(new Emphasis(List.of(new Text("b"))))),
                    new Text(" c"),
                    new Code("d"));
            assertEquals("a b cd", Inlines.text(content));
        }

        @Test
        @DisplayName("turns a hard break into a newline and a soft one into a space")
        void breaks() {
            assertEquals(
                    "a\nb c",
                    Inlines.text(List.of(
                            new Text("a"), new LineBreak(true), new Text("b"), new LineBreak(false), new Text("c"))));
        }

        @Test
        @DisplayName("reads an image as its alt text and skips raw markup")
        void imagesAndMarkup() {
            assertEquals(
                    "a picture",
                    Inlines.text(List.of(new Text("a "), new Image("x.png", null, "picture"), new RawHtml("<br>"))),
                    "a tag is not a word, so a heading's anchor is better off without it");
        }

        @Test
        @DisplayName("is what a heading, a paragraph, a link and a cell each report")
        void theFourCallers() {
            assertEquals("Hi", new Heading(1, words("Hi")).text());
            assertEquals("Hi", new Paragraph(words("Hi")).text());
            assertEquals("Hi", new Link("x", words("Hi")).text());
            assertEquals("Hi", new TableCell(false, CellAlignment.DEFAULT, words("Hi")).text());
            assertEquals("Hi", new WikiLink("x", words("Hi")).text());
        }
    }
}
