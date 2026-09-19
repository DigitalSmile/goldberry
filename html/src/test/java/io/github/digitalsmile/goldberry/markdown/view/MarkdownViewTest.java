package io.github.digitalsmile.goldberry.markdown.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.content.select.Word;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.markdown.Markdown;
import io.github.digitalsmile.goldberry.markdown.MarkdownSyntax;
import io.github.digitalsmile.goldberry.markdown.model.CellAlignment;
import io.github.digitalsmile.goldberry.markdown.model.Document;
import io.github.digitalsmile.goldberry.markdown.model.Heading;
import io.github.digitalsmile.goldberry.markdown.model.LineBreak;
import io.github.digitalsmile.goldberry.markdown.model.Paragraph;
import io.github.digitalsmile.goldberry.markdown.model.Table;
import io.github.digitalsmile.goldberry.markdown.model.TableCell;
import io.github.digitalsmile.goldberry.markdown.model.TableRow;
import io.github.digitalsmile.goldberry.markdown.model.Text;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// `markdown-view`, as the tree of widgets it builds.
///
/// Asserted through an [ElementTree], which is what actually mounts a
/// `Widget.Stateless`: the view's `build` is called by the element layer, so mounting
/// it is the only way to see what it produced. The assertions are about **classes**
/// rather than pixels, because the classes are the contract — `markdown.css` styles
/// them, an application restyles them, and a golden image of a document belongs in the
/// showcase where a window exists.
@DisplayName("markdown-view")
class MarkdownViewTest {

    @BeforeAll
    static void requireLibrary() {
        MarkdownViews.requireLibrary();
    }

    /// Every element under `root`, depth first, the root included.
    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        root.children().forEach(child -> all.addAll(walk(child)));
        return all;
    }

    private static List<Element> mount(String markdown) {
        return walk(new ElementTree(MarkdownView.of(markdown)).root());
    }

    /// Every element carrying `cssClass`.
    private static List<Element> withClass(List<Element> elements, String cssClass) {
        return elements.stream().filter(e -> e.classes().contains(cssClass)).toList();
    }

    /// The words under `elements` — each one's own subtree included, so that asking a
    /// quote or a list marker for its words answers about what is inside it.
    private static List<String> wordsOf(List<Element> elements) {
        // A set, because the callers pass overlapping things: sometimes a whole
        // mounted tree, sometimes one row of it. An element is identified by being
        // itself -- `Element` is a class and does not override `equals` -- so this
        // keeps document order and counts each word once.
        var subtrees = new LinkedHashSet<Element>();
        elements.forEach(element -> subtrees.addAll(walk(element)));
        return withClass(List.copyOf(subtrees), "md-word").stream()
                .map(element -> ((Word) element.widget()).text())
                .toList();
    }

    @Test
    @DisplayName("puts the markdown class on the column it builds, with the author's own")
    void rootClasses() {
        var root =
                new ElementTree(MarkdownView.of(Document.EMPTY).withAttributes(Attributes.NONE.classes("mine"))).root();
        var column = walk(root).stream()
                .filter(e -> e.classes().contains("markdown"))
                .findFirst()
                .orElseThrow();
        assertTrue(column.classes().contains("mine"), "an id and classes from a document have to survive the build");
    }

    @Nested
    @DisplayName("following a property")
    class Bound {

        @Test
        @DisplayName("parses whatever the property holds, on every build")
        void followsTheProperty() {
            var source = Property.of("# One");
            var tree = new ElementTree(MarkdownView.following(source));

            assertEquals(List.of("One"), wordsOf(walk(tree.root())), "the first frame shows what was there");

            source.set("# Two\n\nand more");
            // A binding marks the element for rebuild rather than rebuilding it
            // there and then, so the tree is flushed the way a frame flushes it.
            assertTrue(tree.needsBuild(), "a property a widget is bound to should have marked it dirty");
            tree.flush();

            assertEquals(
                    List.of("Two", "and", "more"),
                    wordsOf(walk(tree.root())),
                    "a preview is a binding rather than a callback: the next build is the new document");
        }

        @Test
        @DisplayName("shows its literal document until the property answers")
        void fallsBackToTheLiteral() {
            var source = Property.<String>of(null);
            var view = new MarkdownView(Markdown.parse("# Waiting"), source, MarkdownSyntax.gitHub(), Attributes.NONE);

            assertEquals(
                    List.of("Waiting"),
                    wordsOf(walk(new ElementTree(view).root())),
                    "a path nothing answers yet is what a lenient inflater produces");
        }

        @Test
        @DisplayName("reads an empty property as an empty document rather than the word null")
        void emptyIsEmpty() {
            var source = Property.of("");
            assertEquals(Document.EMPTY, MarkdownView.following(source).resolved());
        }

        @Test
        @DisplayName("parses in the dialect it was given")
        void dialect() {
            var table = "| a |\n|---|\n| 1 |\n";
            var gitHub = MarkdownView.following(Property.of(table));
            var commonMark = MarkdownView.following(Property.of(table), MarkdownSyntax.commonMark());

            assertInstanceOf(Table.class, gitHub.resolved().blocks().getFirst());
            assertInstanceOf(
                    Paragraph.class,
                    commonMark.resolved().blocks().getFirst(),
                    "plain CommonMark has no tables, so it is a paragraph of pipes");
        }

        @Test
        @DisplayName("reports the property as its binding, which is what the element subscribes to")
        void reportsItsBinding() {
            var source = Property.of("x");
            assertSame(source, MarkdownView.following(source).binding());
            assertNull(MarkdownView.of(Document.EMPTY).binding(), "an unbound view has nothing to follow");
            assertSame(source, MarkdownView.of(Document.EMPTY).bound(source).binding());
        }
    }

    @Test
    @DisplayName("keeps the document it was handed")
    void keepsItsDocument() {
        var document = new Document(List.of(new Heading(1, List.of(new Text("Hi")))));
        assertEquals(document, MarkdownView.of(document).document());
        assertThrows(NullPointerException.class, () -> MarkdownView.of((Document) null));
    }

    @Nested
    @DisplayName("builds")
    class Builds {

        @Test
        @DisplayName("a heading as a prose row at its own level")
        void heading() {
            var elements = mount("## Title\n");
            var heading = withClass(elements, "md-heading");
            assertEquals(1, heading.size());
            assertTrue(heading.getFirst().classes().contains("md-h2"), "the level is a class, so CSS sets the size");
            assertTrue(heading.getFirst().classes().contains("md-prose"), "and it wraps like any other prose");
        }

        @Test
        @DisplayName("one word per widget, so a bold word can be bold")
        void wordsAreWidgets() {
            var elements = mount("one **two** three\n");
            assertEquals(List.of("one", "two", "three"), wordsOf(elements));
            var strong = withClass(elements, "md-strong");
            assertEquals(1, strong.size(), "only the bold word carries the mark");
            assertEquals("two", wordsOf(strong).getFirst());
        }

        @Test
        @DisplayName("a word inside two marks with both classes on it")
        void nestedMarks() {
            var elements = mount("*a **b** c*\n");
            var both = withClass(withClass(elements, "md-em"), "md-strong");
            assertEquals(List.of("b"), wordsOf(both), "bold inside emphasis is one word with two classes");
            assertEquals(3, withClass(elements, "md-em").size(), "and the other two are only emphasised");
        }

        @Test
        @DisplayName("a code span as one widget, spaces and all")
        void codeSpan() {
            var elements = mount("`a  b`\n");
            var code = withClass(elements, "md-code");
            assertEquals(1, code.size(), "splitting a code span into words would correct somebody's program");
            assertEquals("a  b", wordsOf(code).getFirst());
        }

        @Test
        @DisplayName("a bullet in the gutter and the item's blocks beside it")
        void bulletList() {
            var elements = mount("- one\n- two\n");
            assertEquals(1, withClass(elements, "md-list").size());
            assertTrue(withClass(elements, "md-list").getFirst().classes().contains("tight"));
            assertEquals(2, withClass(elements, "md-item").size());
            assertEquals(List.of("•", "•"), wordsOf(withClass(elements, "md-marker")));
        }

        @Test
        @DisplayName("a numbered list's own numbers, from where it starts")
        void numberedList() {
            var elements = mount("7. seven\n8. eight\n");
            assertEquals(List.of("7.", "8."), wordsOf(withClass(elements, "md-marker")));
        }

        @Test
        @DisplayName("a task's check box as a part rather than a character")
        void taskMark() {
            var elements = mount("- [x] done\n- [ ] not\n");
            var marks = elements.stream()
                    .filter(e -> e.widget() instanceof TaskMark)
                    .toList();
            assertEquals(2, marks.size(), "U+2610 is in neither bundled face, so the box is drawn");
            assertTrue(marks.getFirst().classes().contains("done"), "the state is a class the stylesheet reads");
            assertFalse(marks.get(1).classes().contains("done"));
            assertTrue(withClass(elements, "md-marker").isEmpty(), "a task has no bullet as well as its box");
        }

        @Test
        @DisplayName("a fence as one line per line, with the language above it")
        void codeBlock() {
            var elements = mount("```java\na\n\nb\n```\n");
            assertEquals(1, withClass(elements, "md-code-block").size());
            assertEquals(List.of("java"), wordsOf(withClass(elements, "md-code-language")));
            assertEquals(
                    List.of("a", " ", "b"),
                    wordsOf(withClass(elements, "md-code-line")),
                    "a blank line is a line of the program, and an empty text measures zero high");
        }

        @Test
        @DisplayName("a quote as a column of its own blocks")
        void quote() {
            var elements = mount("> quoted\n");
            assertEquals(1, withClass(elements, "md-quote").size());
            assertEquals(List.of("quoted"), wordsOf(withClass(elements, "md-quote")));
        }

        @Test
        @DisplayName("a rule with nothing in it")
        void rule() {
            var elements = mount("---\n");
            var rule = withClass(elements, "md-rule");
            assertEquals(1, rule.size());
            assertTrue(rule.getFirst().children().isEmpty(), "a rule is a line, which is a box with a background");
        }

        @Test
        @DisplayName("a table as rows of cells, with the head marked and the alignment a class")
        void table() {
            var elements = mount("""
                    | a | b |
                    |:--|--:|
                    | 1 | 2 |
                    """);
            assertEquals(1, withClass(elements, "md-table").size());
            var rows = withClass(elements, "md-row");
            assertEquals(2, rows.size());
            assertTrue(rows.getFirst().classes().contains("head"));
            assertTrue(rows.get(1).classes().contains("body"));
            var cells = withClass(elements, "md-cell");
            assertEquals(4, cells.size());
            assertTrue(cells.getFirst().classes().contains("first"), "the leftmost cell draws no left border");
            assertTrue(cells.getFirst().classes().contains("head-cell"));
            assertEquals(2, withClass(elements, "end").size(), "the right-aligned column, head and body");
        }

        @Test
        @DisplayName("an image as its alt text when the application cannot find it")
        void image() {
            var elements = mount("![a picture](x.png)\n");
            assertEquals(List.of("a", "picture"), wordsOf(withClass(elements, "md-image")));
        }

        @Test
        @DisplayName("a link as one button, which is what a reader can press")
        void link() {
            var elements = mount("[go](http://x)\n");
            var buttons = buttonsOf(elements);

            assertEquals(1, buttons.size(), "the whole run is one widget, so it hovers and fires once");
            assertEquals("go", buttons.getFirst().label());
            assertTrue(buttons.getFirst().attributes().classes().contains("link"), "ADR-0293's variant");
            assertTrue(buttons.getFirst().attributes().classes().contains("md-link"));
        }

        @Test
        @DisplayName("a link with no destination as words, because there is nowhere to go")
        void linkWithNoDestination() {
            var elements = mount("[go]()\n");

            assertTrue(buttonsOf(elements).isEmpty());
            assertEquals(List.of("go"), wordsOf(elements));
        }

        @Test
        @DisplayName("raw markup as the text it is")
        void rawHtml() {
            var elements = mount("<div>x</div>\n");
            assertEquals(List.of("<div>x</div>\n"), wordsOf(withClass(elements, "md-raw")));
        }
    }

    /// What a break does, which is two different things (ADR-0426).
    ///
    /// The assertions are about the **shape** of the paragraph as well as its classes,
    /// because the shape is the fix: a soft break leaves the single wrapping row every
    /// golden image was blessed against, and a hard break is the one case that becomes
    /// a column.
    @Nested
    @DisplayName("a break inside a paragraph")
    class Breaks {

        /// The paragraph's own box — the one thing per paragraph that `md-prose` is on,
        /// whichever shape it took.
        private Element paragraph(List<Element> elements) {
            var prose = withClass(elements, "md-prose");
            assertEquals(1, prose.size(), "there is one box per paragraph, whatever shape it is");
            return prose.getFirst();
        }

        /// The rows a paragraph's words are laid out in, in order.
        private List<Element> lines(List<Element> elements) {
            return withClass(elements, "md-line");
        }

        @Test
        @DisplayName("a soft break reflows, so the paragraph is the one row it always was")
        void softBreakIsStillOneRow() {
            var elements = mount("one\ntwo\n");
            var prose = paragraph(elements);

            assertInstanceOf(Row.class, prose.widget(), "a newline the author's editor put in is not a line ending");
            assertTrue(prose.classes().contains("md-line"), "the paragraph is its own line");
            assertFalse(prose.classes().contains("md-lines"));
            assertEquals(1, lines(elements).size());
            assertEquals(List.of("one", "two"), wordsOf(elements));
        }

        @Test
        @DisplayName("a hard break ends the line, as a column of the lines it made")
        void hardBreakStartsANewLine() {
            var elements = mount("one  \ntwo\n");
            var prose = paragraph(elements);
            var lines = lines(elements);

            assertInstanceOf(Column.class, prose.widget(), "two lines cannot be one wrapping row");
            assertTrue(prose.classes().contains("md-lines"));
            assertFalse(prose.classes().contains("md-line"), "the paragraph is not itself a line any more");
            assertEquals(2, lines.size());
            assertEquals(List.of("one"), wordsOf(List.of(lines.getFirst())));
            assertEquals(List.of("two"), wordsOf(List.of(lines.get(1))));
        }

        @Test
        @DisplayName("a backslash break is the same break, because the model says so")
        void aBackslashIsTheSameBreak() {
            assertEquals(2, lines(mount("one\\\ntwo\n")).size());
        }

        @Test
        @DisplayName("one inside emphasis splits the line and both halves keep the mark")
        void insideEmphasis() {
            var elements = mount("*a\\\nb*\n");
            var lines = lines(elements);

            assertEquals(2, lines.size(), "the break arrives partway down the walk and means the same thing there");
            assertEquals(List.of("a"), wordsOf(List.of(lines.getFirst())));
            assertEquals(List.of("b"), wordsOf(List.of(lines.get(1))));
            assertEquals(
                    List.of("a", "b"),
                    wordsOf(withClass(elements, "md-em")),
                    "emphasis across two lines is what every renderer does");
        }

        @Test
        @DisplayName("one inside a link does not, because a link is one thing to press")
        void insideALink() {
            var elements = mount("[a\\\nb](/x)\n");
            var prose = paragraph(elements);

            assertInstanceOf(Row.class, prose.widget(), "splitting the button would make two Tab stops for one href");
            assertEquals(1, lines(elements).size());
            assertEquals("a b", buttonsOf(elements).getFirst().label(), "the break is a space in the label instead");
        }

        @Test
        @DisplayName("two in a row keep the empty line between them, which is a line an author can write")
        void twoInARow() {
            // `one`, two hard breaks, `two` -- md4c's reading of a line holding only a
            // backslash, and what a browser draws for `one<br><br>two`.
            var lines = lines(mount("one  \n\\\ntwo\n"));

            assertEquals(3, lines.size());
            assertEquals(
                    List.of(" "),
                    wordsOf(List.of(lines.get(1))),
                    "a row with nothing in it measures zero high, so the blank line is one space");
        }

        @Test
        @DisplayName("a leading one leaves the empty line where it was written")
        void leadingBreak() {
            var lines = lines(mount("\\\none\n"));

            assertEquals(2, lines.size());
            assertEquals(List.of(" "), wordsOf(List.of(lines.getFirst())));
            assertEquals(List.of("one"), wordsOf(List.of(lines.get(1))));
        }

        @Test
        @DisplayName("a trailing one is not something Markdown can write, and is drawn if a model holds one")
        void trailingBreak() {
            // md4c strips the two spaces at the end of a paragraph, so the source cannot
            // produce this -- but `html-view`'s `<p>a<br></p>` can, and the model is
            // public, so the fold is asked directly.
            assertEquals(
                    List.of(new Text("one")),
                    ((Paragraph) Markdown.parse("one  \n").blocks().getFirst()).content(),
                    "two spaces before the end of a paragraph are not a break");

            var document = new Document(List.of(new Paragraph(List.of(new Text("one"), new LineBreak(true)))));
            var lines = lines(walk(new ElementTree(MarkdownView.of(document)).root()));

            assertEquals(2, lines.size(), "a break the model holds is a line ending, wherever it is");
            assertEquals(List.of(" "), wordsOf(List.of(lines.get(1))));
        }

        @Test
        @DisplayName("a heading's classes stay on the paragraph's box, so every line inherits the size")
        void headingClassesStayOnTheBox() {
            // The Java half of the CSS split: `md-h2` sets a font size and the lines
            // have to inherit it, so it belongs on the box above them and not on each
            // one. A setext heading is the only heading whose source can hold a break;
            // the model is asked directly, which is what `prose` sees either way.
            var heading = new Heading(2, List.of(new Text("a"), new LineBreak(true), new Text("b")));
            var elements = walk(new ElementTree(MarkdownView.of(new Document(List.of(heading)))).root());
            var prose = paragraph(elements);

            assertTrue(prose.classes().contains("md-h2"), "the level is on the box the lines are inside");
            assertTrue(prose.classes().contains("md-heading"));
            assertTrue(prose.classes().contains("md-lines"));
            lines(elements)
                    .forEach(line ->
                            assertFalse(line.classes().contains("md-h2"), "a line carries no typography of its own"));
        }

        @Test
        @DisplayName("a table cell stays one row, because a cell is one line by construction")
        void cellsAreOneLine() {
            // md4c cannot put a hard break in a cell -- a table row is a single line of
            // source, and `<br>` in one is raw markup -- so this is what the fold does
            // when a model built by hand holds one: the break ends the token and no
            // more, which is what every box that is a line by construction wants.
            var cell = new TableCell(
                    false, CellAlignment.DEFAULT, List.of(new Text("a"), new LineBreak(true), new Text("b")));
            var table = new Table(List.of(), List.of(new TableRow(false, List.of(cell))));
            var elements = walk(new ElementTree(MarkdownView.of(new Document(List.of(table)))).root());

            assertEquals(1, withClass(elements, "md-cell").size());
            assertInstanceOf(
                    Row.class, withClass(elements, "md-cell").getFirst().widget());
            assertEquals(List.of("a", "b"), wordsOf(elements));
        }
    }

    /// Every `button` in `elements`, which is what a link is now.
    private static List<io.github.digitalsmile.goldberry.widgets.controls.button.Button> buttonsOf(
            List<Element> elements) {
        return elements.stream()
                .map(Element::widget)
                .filter(io.github.digitalsmile.goldberry.widgets.controls.button.Button.class::isInstance)
                .map(io.github.digitalsmile.goldberry.widgets.controls.button.Button.class::cast)
                .toList();
    }

    @Nested
    @DisplayName("what the application wires")
    class Wired {

        /// A 2x2 image, built rather than decoded: this is about the fold, not about
        /// the codec.
        private static io.github.digitalsmile.goldberry.image.Image swatch() {
            return io.github.digitalsmile.goldberry.image.Image.ofArgb(
                    2, 2, new int[] {0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233});
        }

        private static List<Element> mountView(MarkdownView view) {
            return walk(new ElementTree(view).root());
        }

        @Test
        @DisplayName("a link hands its href over, and to nobody else")
        void linksAreFollowed() {
            var followed = new ArrayList<String>();
            var view = MarkdownView.of("[go](https://example.com/a)").onLink(followed::add);

            var button = buttonsOf(mountView(view)).getFirst();
            button.onPress().run();

            assertEquals(List.of("https://example.com/a"), followed, "nothing here opens a browser");
        }

        @Test
        @DisplayName("a wiki link hands over its target, which is a different question")
        void wikiLinksAreTheirOwnHandler() {
            var opened = new ArrayList<String>();
            var followed = new ArrayList<String>();
            var view = MarkdownView.of(
                            "[[Meeting notes]] and [a link](/x)",
                            io.github.digitalsmile.goldberry.markdown.MarkdownSyntax.gitHub()
                                    .with(io.github.digitalsmile.goldberry.markdown.MarkdownExtension.WIKI_LINKS))
                    .onLink(followed::add)
                    .onWikiLink(opened::add);

            buttonsOf(mountView(view)).forEach(button -> button.onPress().run());

            assertEquals(List.of("Meeting notes"), opened, "a target names something in the application's collection");
            assertEquals(List.of("/x"), followed);
        }

        @Test
        @DisplayName("a link nobody wired is drawn and inert")
        void unwiredLinksAreInert() {
            assertNull(buttonsOf(mount("[go](/x)")).getFirst().onPress());
        }

        @Test
        @DisplayName("an image is drawn when the application can find it")
        void imagesAreDrawn() {
            var image = swatch();
            var view = MarkdownView.of("![a picture](sea.png)").images(src -> "sea.png".equals(src) ? image : null);

            var pictures = mountView(view).stream()
                    .map(Element::widget)
                    .filter(io.github.digitalsmile.goldberry.content.image.Picture.class::isInstance)
                    .toList();

            assertEquals(1, pictures.size(), "an ImageSource that answers is the whole difference");
            assertTrue(wordsOf(mountView(view)).isEmpty(), "and the alt text is not drawn beside it");
        }

        @Test
        @DisplayName("and is its alt text when the application has nothing for that src")
        void missingImagesAreAltText() {
            var view = MarkdownView.of("![a picture](gone.png)").images(src -> null);

            assertEquals(List.of("a", "picture"), wordsOf(mountView(view)));
        }

        @Test
        @DisplayName("a task box reports which task it is, in document order")
        void tasksReportTheirOrdinal() {
            var pressed = new ArrayList<Integer>();
            var view = MarkdownView.of("- [ ] one\n- ordinary\n- [x] two\n").onTask(pressed::add);
            var marks = mountView(view).stream()
                    .map(Element::widget)
                    .filter(TaskMark.class::isInstance)
                    .map(TaskMark.class::cast)
                    .toList();

            assertEquals(2, marks.size());
            marks.forEach(mark -> mark.onToggle().run());

            // Nought and one, not nought and two: the ordinal counts **tasks**, which
            // is what `Markdown.toggleTask` counts in the source (ADR-0300).
            assertEquals(List.of(0, 1), pressed);
        }

        @Test
        @DisplayName("and is a Tab stop only when it does something")
        void onlyWiredBoxesAreFocusable() {
            var wired = mountView(MarkdownView.of("- [ ] one").onTask(index -> {})).stream()
                    .map(Element::widget)
                    .filter(TaskMark.class::isInstance)
                    .map(TaskMark.class::cast)
                    .toList();
            var inert = mount("- [ ] one").stream()
                    .map(Element::widget)
                    .filter(TaskMark.class::isInstance)
                    .map(TaskMark.class::cast)
                    .toList();

            assertTrue(wired.getFirst().isFocusable());
            assertTrue(wired.getFirst().classes().contains("interactive"), "the stylesheet needs to know");
            assertFalse(inert.getFirst().isFocusable(), "a Tab stop that does nothing is a trap with extra steps");
            assertTrue(inert.getFirst().isDisabled());
        }
    }

    @Nested
    @DisplayName("from markup")
    class Inflated {

        private MarkdownView inflate(String kdl) {
            var node = KdlParser.parse(kdl).getFirst();
            return assertInstanceOf(MarkdownView.class, MarkdownView.inflate(node, List.of(), Wiring.none()));
        }

        @Test
        @DisplayName("parses the node's argument as the document")
        void argumentIsTheSource() {
            var view = inflate("markdown-view \"# Hi\"");
            assertEquals(Markdown.parse("# Hi"), view.document());
        }

        @Test
        @DisplayName("carries the node's id and classes")
        void attributes() {
            var view = inflate("markdown-view id=\"preview\" class=\"note\" \"x\"");
            assertEquals("preview", view.attributes().id());
            assertTrue(view.attributes().classes().contains("note"));
        }

        @Test
        @DisplayName("refuses children rather than dropping them")
        void childrenAreRefused() {
            var node = KdlParser.parse("markdown-view \"x\"").getFirst();
            var children = List.<io.github.digitalsmile.goldberry.widget.Widget>of(MarkdownView.of(Document.EMPTY));
            var thrown = assertThrows(
                    IllegalArgumentException.class, () -> MarkdownView.inflate(node, children, Wiring.none()));
            assertTrue(thrown.getMessage().contains("argument"), thrown.getMessage());
        }
    }
}
