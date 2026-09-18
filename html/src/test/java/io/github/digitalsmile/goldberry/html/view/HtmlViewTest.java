package io.github.digitalsmile.goldberry.html.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.content.select.Word;
import io.github.digitalsmile.goldberry.html.Html;
import io.github.digitalsmile.goldberry.html.model.HtmlDocument;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// `html-view`, as the tree of widgets it builds.
///
/// The twin of `MarkdownViewTest`, and the same method: mount through an
/// [ElementTree], because a `Widget.Stateless` is built by the element layer and
/// mounting is the only way to see what it produced. The assertions are about
/// **classes**, because the classes are the contract — `html.css` styles them and an
/// application restyles them — plus the one thing this view has that the other does
/// not, which is a `button` where an anchor was (ADR-0293, ADR-0298).
@DisplayName("html-view")
class HtmlViewTest {

    /// Every element under `root`, depth first, the root included.
    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        root.children().forEach(child -> all.addAll(walk(child)));
        return all;
    }

    private static List<Element> mount(String html) {
        return walk(new ElementTree(HtmlView.of(html)).root());
    }

    private static List<Element> withClass(List<Element> elements, String cssClass) {
        return elements.stream().filter(e -> e.classes().contains(cssClass)).toList();
    }

    /// The words under `elements` — each one's own subtree included, so that asking a
    /// quote or a cell for its words answers about what is inside it.
    private static List<String> wordsOf(List<Element> elements) {
        var subtrees = new LinkedHashSet<Element>();
        elements.forEach(element -> subtrees.addAll(walk(element)));
        return withClass(List.copyOf(subtrees), "html-word").stream()
                .map(element -> ((Word) element.widget()).text())
                .toList();
    }

    private static List<Button> buttonsOf(List<Element> elements) {
        return elements.stream()
                .map(Element::widget)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    @Test
    @DisplayName("puts the html class on the column it builds, with the author's own")
    void rootClasses() {
        var view = HtmlView.of(HtmlDocument.EMPTY)
                .withAttributes(Attributes.NONE.classes("mine").id("page"));
        var column = walk(new ElementTree(view).root()).stream()
                .filter(e -> e.classes().contains("html"))
                .findFirst()
                .orElseThrow();

        assertTrue(column.classes().contains("mine"), "an id and classes from a document have to survive the build");
    }

    @Test
    @DisplayName("keeps the document it was handed, and refuses none at all")
    void keepsItsDocument() {
        var document = Html.parse("<p>x</p>");

        assertEquals(document, HtmlView.of(document).document());
        assertThrows(NullPointerException.class, () -> HtmlView.of((HtmlDocument) null));
    }

    @Nested
    @DisplayName("builds")
    class Builds {

        @Test
        @DisplayName("a paragraph as a wrapping row of words, one widget each")
        void paragraph() {
            var elements = mount("<p>one two three</p>");

            assertEquals(1, withClass(elements, "html-p").size());
            assertTrue(withClass(elements, "html-p").getFirst().classes().contains("html-prose"));
            assertEquals(List.of("one", "two", "three"), wordsOf(elements));
        }

        @Test
        @DisplayName("a heading with its level as a class, so the stylesheet sets the size")
        void heading() {
            var elements = mount("<h2>Title</h2>");
            var heading = withClass(elements, "html-h2");

            assertEquals(1, heading.size());
            assertTrue(heading.getFirst().classes().contains("html-prose"), "and it wraps like any other prose");
        }

        @Test
        @DisplayName("an inline mark as a class on the words it covers and no others")
        void inlineMarks() {
            var elements = mount("<p>one <strong>two</strong> three</p>");
            var strong = withClass(elements, "html-strong");

            assertEquals(1, strong.size(), "only the bold word carries the mark");
            assertEquals(List.of("two"), wordsOf(strong));
        }

        @Test
        @DisplayName("a word inside two marks with both classes on it")
        void nestedMarks() {
            var elements = mount("<p><em>a <strong>b</strong> c</em></p>");

            assertEquals(List.of("b"), wordsOf(withClass(withClass(elements, "html-em"), "html-strong")));
            assertEquals(3, withClass(elements, "html-em").size(), "and the other two are only emphasised");
        }

        @Test
        @DisplayName("a code span as one widget, spaces and all")
        void codeSpan() {
            var elements = mount("<p><code>a  b</code></p>");
            var code = withClass(elements, "html-code");

            assertEquals(1, code.size(), "splitting a code span into words would correct somebody's program");
            assertEquals("a  b", wordsOf(code).getFirst());
        }

        @Test
        @DisplayName("a bullet in the gutter and the item's blocks beside it")
        void bulletList() {
            var elements = mount("<ul><li>one</li><li>two</li></ul>");

            assertEquals(1, withClass(elements, "html-list").size());
            assertTrue(withClass(elements, "html-list").getFirst().classes().contains("bulleted"));
            assertEquals(2, withClass(elements, "html-item").size());
            assertEquals(List.of("•", "•"), wordsOf(withClass(elements, "html-marker")));
        }

        @Test
        @DisplayName("a numbered list from where the author said it starts")
        void numberedList() {
            var elements = mount("<ol start=\"7\"><li>seven</li><li>eight</li></ol>");

            assertEquals(List.of("7.", "8."), wordsOf(withClass(elements, "html-marker")));
            assertTrue(withClass(elements, "html-list").getFirst().classes().contains("numbered"));
        }

        @Test
        @DisplayName("a start nobody can read as 1 rather than as a failure")
        void unreadableStart() {
            assertEquals(
                    List.of("1."), wordsOf(withClass(mount("<ol start=\"first\"><li>x</li></ol>"), "html-marker")));
        }

        @Test
        @DisplayName("a definition list's terms and definitions, the terms with no mark")
        void definitionList() {
            var elements = mount("<dl><dt>Term</dt><dd>Meaning</dd></dl>");

            assertEquals(List.of("Term"), wordsOf(withClass(elements, "html-term")));
            assertEquals(List.of("Meaning"), wordsOf(withClass(elements, "html-item")));
        }

        @Test
        @DisplayName("a pre as one line per line, the author's spacing kept")
        void preformatted() {
            var elements = mount("<pre><code>a\n\n  b</code></pre>");

            assertEquals(1, withClass(elements, "html-pre").size());
            assertEquals(
                    List.of("a", " ", "  b"),
                    wordsOf(withClass(elements, "html-code-line")),
                    "a blank line is a line of the program, and an empty text measures zero high");
        }

        @Test
        @DisplayName("a quote as a bar and a column of its own blocks")
        void quote() {
            var elements = mount("<blockquote><p>quoted</p></blockquote>");

            assertEquals(1, withClass(elements, "html-quote").size());
            assertEquals(1, withClass(elements, "html-quote-bar").size(), "§10 has no border-left to draw it with");
            assertEquals(List.of("quoted"), wordsOf(withClass(elements, "html-quote")));
        }

        @Test
        @DisplayName("a rule with nothing in it")
        void rule() {
            var rule = withClass(mount("<hr>"), "html-rule");

            assertEquals(1, rule.size());
            assertTrue(rule.getFirst().children().isEmpty(), "a rule is a line, which is a box with a background");
        }

        @Test
        @DisplayName("a table as rows of cells, with a head marked and the sections flattened")
        void table() {
            var elements = mount("""
                    <table>
                      <thead><tr><th>a</th><th align="right">b</th></tr></thead>
                      <tbody><tr><td>1</td><td>2</td></tr></tbody>
                    </table>
                    """);

            assertEquals(1, withClass(elements, "html-table").size(), "a thead is not a box a flex column needs");
            var rows = withClass(elements, "html-row");
            assertEquals(2, rows.size());
            assertTrue(rows.getFirst().classes().contains("head"));
            assertTrue(rows.get(1).classes().contains("body"));
            assertEquals(4, withClass(elements, "html-cell").size());
            assertTrue(withClass(elements, "html-cell").getFirst().classes().contains("head-cell"));
            assertEquals(1, withClass(elements, "end").size(), "align= is what a hand-written table uses");
        }

        @Test
        @DisplayName("a row with no table round it as a row, cells and all")
        void rowWithNoTable() {
            // The bug this caught: a stray `tr` was folded by asking for its **rows**,
            // which match `tr`, a section and a `caption` — so a row answered with none
            // of them and every cell in it was dropped. Nothing in this model is
            // dropped for being in the wrong place (`Element`), and a browser keeps the
            // text too.
            var elements = mount("<tr><td>a</td><td>b</td></tr>");

            assertEquals(1, withClass(elements, "html-row").size());
            assertEquals(2, withClass(elements, "html-cell").size());
            assertEquals(List.of("a", "b"), wordsOf(elements));
        }

        @Test
        @DisplayName("a caption above its rows")
        void caption() {
            var elements = mount("<table><caption>Faces</caption><tr><td>a</td></tr></table>");

            assertEquals(List.of("Faces"), wordsOf(withClass(elements, "html-caption")));
        }

        @Test
        @DisplayName("an image as its alt text when the application cannot find it")
        void image() {
            var elements = mount("<p><img src=\"x.png\" alt=\"a picture\"></p>");

            assertEquals(List.of("a", "picture"), wordsOf(withClass(elements, "html-img")));
        }

        @Test
        @DisplayName("an image with no alt as nothing, rather than as its file name")
        void imageWithNoAlt() {
            assertEquals(List.of(), wordsOf(mount("<p><img src=\"x.png\"></p>")));
        }

        @Test
        @DisplayName("inline content between two blocks as a paragraph nobody wrote")
        void implicitParagraph() {
            var elements = mount("<div>loose words<p>and a paragraph</p></div>");

            assertEquals(2, withClass(elements, "html-prose").size());
            assertEquals(List.of("loose", "words", "and", "a", "paragraph"), wordsOf(elements));
        }

        @Test
        @DisplayName("whitespace between two blocks as nothing at all")
        void whitespaceBetweenBlocks() {
            var elements = mount("<p>one</p>\n\n<p>two</p>");

            assertEquals(2, withClass(elements, "html-prose").size(), "a newline between tags is not a paragraph");
            assertEquals(List.of("one", "two"), wordsOf(elements));
        }

        @Test
        @DisplayName("an unknown tag as a block that stacks, styled by its own name")
        void unknownTag() {
            var elements = mount("<my-callout><p>inside</p></my-callout>");

            assertEquals(1, withClass(elements, "html-my-callout").size());
            assertTrue(
                    withClass(elements, "html-my-callout").getFirst().classes().contains("html-block"));
            assertEquals(List.of("inside"), wordsOf(elements));
        }

        @Test
        @DisplayName("a document's own class in the html namespace, so it cannot collide")
        void authorClasses() {
            var elements = mount("<div class=\"callout wide\">x</div>");

            assertEquals(1, withClass(elements, "html-callout").size());
            assertTrue(withClass(elements, "html-callout").getFirst().classes().contains("html-wide"));
            assertTrue(withClass(elements, "callout").isEmpty(), "an application's own .callout rule must not match");
        }

        @Test
        @DisplayName("a document's id as nothing, because an id is unique in a tree")
        void authorIds() {
            var elements = mount("<div id=\"intro\">x</div>");

            assertTrue(elements.stream().noneMatch(e -> "intro".equals(e.id())));
        }

        @Test
        @DisplayName("a script, a style and a head as nothing")
        void skipped() {
            var elements = mount("""
                    <head><title>T</title><style>p{}</style></head>
                    <p>body</p>
                    <script>var x = 1</script>
                    """);

            assertEquals(List.of("body"), wordsOf(elements));
        }

        @Test
        @DisplayName("a comment as nothing")
        void comment() {
            assertEquals(List.of("x"), wordsOf(mount("<!-- note --><p>x</p>")));
        }
    }

    @Nested
    @DisplayName("a link")
    class Links {

        @Test
        @DisplayName("is a button, which is the thing markdown-view cannot do")
        void isAButton() {
            var elements = mount("<p>Read <a href=\"/help\">the help</a> first.</p>");
            var buttons = buttonsOf(elements);

            assertEquals(1, buttons.size());
            assertEquals("the help", buttons.getFirst().label(), "the whole run is one widget, so it hovers as one");
        }

        @Test
        @DisplayName("carries ADR-0293's variant and its own tag class")
        void classes() {
            var button = buttonsOf(mount("<p><a href=\"/x\">go</a></p>")).getFirst();

            assertTrue(button.attributes().classes().contains("link"), "the fifth button variant, for a sentence");
            assertTrue(button.attributes().classes().contains("html-a"));
        }

        @Test
        @DisplayName("hands its href to the application and to nobody else")
        void callsBack() {
            var followed = new ArrayList<String>();
            var view = HtmlView.of("<p><a href=\"/help#top\">go</a></p>").onLink(followed::add);

            var button = buttonsOf(walk(new ElementTree(view).root())).getFirst();
            button.onPress().run();

            assertEquals(List.of("/help#top"), followed, "nothing here resolves a path or opens a browser");
        }

        @Test
        @DisplayName("is drawn and inert when nobody wired the view")
        void inertWithNoHandler() {
            var button = buttonsOf(mount("<p><a href=\"/x\">go</a></p>")).getFirst();

            assertNull(button.onPress(), "a page with no handler still shows its links");
        }

        @Test
        @DisplayName("with no href is words, because there is nowhere to go")
        void anchorWithNoHref() {
            var elements = mount("<p><a name=\"top\">Top</a></p>");

            assertTrue(buttonsOf(elements).isEmpty());
            assertEquals(List.of("Top"), wordsOf(elements));
        }

        @Test
        @DisplayName("with no text is not a button either, because a button needs something to read out")
        void anchorWithNoText() {
            // §13: a button with neither a label nor an icon throws. An anchor round an
            // image is the case that would have found it.
            var elements = mount("<p><a href=\"/x\"><img src=\"y.png\" alt=\"a chart\"></a></p>");

            assertTrue(buttonsOf(elements).isEmpty());
            assertEquals(List.of("a", "chart"), wordsOf(elements));
        }

        @Test
        @DisplayName("keeps the words round it where they were")
        void wordsAround() {
            var elements = mount("<p>Read <a href=\"/help\">the help</a> first.</p>");

            assertEquals(List.of("Read", "first."), wordsOf(elements), "the link's own label is the button's");
        }
    }

    @Nested
    @DisplayName("images")
    class Images {

        /// A 2x2 image, built rather than decoded: this is about the fold, not about
        /// the codec.
        private static io.github.digitalsmile.goldberry.image.Image swatch() {
            return io.github.digitalsmile.goldberry.image.Image.ofArgb(
                    2, 2, new int[] {0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233});
        }

        private static List<io.github.digitalsmile.goldberry.content.image.Picture> picturesOf(List<Element> elements) {
            return elements.stream()
                    .map(Element::widget)
                    .filter(io.github.digitalsmile.goldberry.content.image.Picture.class::isInstance)
                    .map(io.github.digitalsmile.goldberry.content.image.Picture.class::cast)
                    .toList();
        }

        @Test
        @DisplayName("are drawn when the application says where they are")
        void drawnWhenResolved() {
            var image = swatch();
            var view = HtmlView.of("<p><img src=\"sea.png\" alt=\"the Sea\"></p>")
                    .images(src -> "sea.png".equals(src) ? image : null);

            var pictures = picturesOf(walk(new ElementTree(view).root()));

            assertEquals(1, pictures.size());
            assertEquals("the Sea", pictures.getFirst().alt(), "the alt text is the accessible name");
        }

        @Test
        @DisplayName("and are their alt text when it does not")
        void altTextWhenUnresolved() {
            var view = HtmlView.of("<p><img src=\"gone.png\" alt=\"a picture\"></p>")
                    .images(src -> null);
            var elements = walk(new ElementTree(view).root());

            assertTrue(picturesOf(elements).isEmpty());
            assertEquals(List.of("a", "picture"), wordsOf(elements));
        }

        @Test
        @DisplayName("stay in the line they were written in, because an img is inline")
        void inlineWithTheWords() {
            var image = swatch();
            var view = HtmlView.of("<p>before <img src=\"x.png\" alt=\"\"> after</p>")
                    .images(src -> image);
            var elements = walk(new ElementTree(view).root());

            assertEquals(1, withClass(elements, "html-prose").size(), "one paragraph, not three blocks");
            assertEquals(List.of("before", "after"), wordsOf(elements));
            assertEquals(1, picturesOf(elements).size());
        }

        @Test
        @DisplayName("keep the view's handler through every with- method")
        void keptThroughRebuilds() {
            var view = HtmlView.of(HtmlDocument.EMPTY).images(src -> null);

            assertSame(
                    view.images(), view.withAttributes(Attributes.NONE.id("x")).images());
            assertSame(view.images(), view.bound(Property.of("y")).images());
            assertThrows(NullPointerException.class, () -> view.images(null));
        }
    }

    @Nested
    @DisplayName("following a property")
    class Bound {

        @Test
        @DisplayName("parses whatever the property holds, on every build")
        void followsTheProperty() {
            var source = Property.of("<h1>One</h1>");
            var tree = new ElementTree(HtmlView.following(source));

            assertEquals(List.of("One"), wordsOf(walk(tree.root())), "the first frame shows what was there");

            source.set("<h1>Two</h1><p>and more</p>");
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
            var view = new HtmlView(Html.parse("<p>Waiting</p>"), source, null, null, Attributes.NONE);

            assertEquals(List.of("Waiting"), wordsOf(walk(new ElementTree(view).root())));
        }

        @Test
        @DisplayName("reads an empty property as an empty document rather than the word null")
        void emptyIsEmpty() {
            assertSame(HtmlDocument.EMPTY, HtmlView.following(Property.of("")).resolved());
        }

        @Test
        @DisplayName("reports the property as its binding, which is what the element subscribes to")
        void reportsItsBinding() {
            var source = Property.of("x");

            assertSame(source, HtmlView.following(source).binding());
            assertNull(HtmlView.of(HtmlDocument.EMPTY).binding(), "an unbound view has nothing to follow");
            assertSame(source, HtmlView.of(HtmlDocument.EMPTY).bound(source).binding());
        }

        @Test
        @DisplayName("keeps its link handler through every with- method")
        void keepsTheHandler() {
            var view = HtmlView.of(HtmlDocument.EMPTY).onLink(href -> {});

            assertSame(
                    view.onLink(), view.withAttributes(Attributes.NONE.id("x")).onLink());
            assertSame(view.onLink(), view.bound(Property.of("y")).onLink());
            assertThrows(NullPointerException.class, () -> view.onLink(null));
        }
    }

    @Nested
    @DisplayName("from markup")
    class Inflated {

        private HtmlView inflate(String kdl) {
            var node = KdlParser.parse(kdl).getFirst();
            return assertInstanceOf(HtmlView.class, HtmlView.inflate(node, List.of(), Wiring.none()));
        }

        @Test
        @DisplayName("parses the node's argument")
        void argument() {
            var view = inflate("html-view \"<p>Hello</p>\"");

            assertEquals(List.of("Hello"), wordsOf(walk(new ElementTree(view).root())));
        }

        @Test
        @DisplayName("an id and classes land on the column, like every other widget")
        void attributes() {
            var view = inflate("html-view id=\"page\" class=\"tall\"");

            assertEquals("page", view.attributes().id());
            assertTrue(view.attributes().classes().contains("tall"));
        }

        @Test
        @DisplayName("an empty node is an empty document rather than a failure")
        void noArgument() {
            assertSame(HtmlDocument.EMPTY, inflate("html-view").document());
        }

        @Test
        @DisplayName("children are refused, because a page has content of its own")
        void childrenRefused() {
            var node = KdlParser.parse("html-view { text \"x\" }").getFirst();
            var child = List.<io.github.digitalsmile.goldberry.widget.Widget>of(
                    new io.github.digitalsmile.goldberry.widgets.text.Text("x"));

            var failure =
                    assertThrows(IllegalArgumentException.class, () -> HtmlView.inflate(node, child, Wiring.none()));
            assertTrue(failure.getMessage().contains("html-view"), failure.getMessage());
        }

        @Test
        @DisplayName("bind= is the property, and the argument is what shows until it answers")
        void bound() {
            var source = Property.of("<p>bound</p>");
            var wiring = new Wiring(
                    io.github.digitalsmile.goldberry.bind.registry.ActionRegistry.none(),
                    io.github.digitalsmile.goldberry.widgets.Icons.none(),
                    io.github.digitalsmile.goldberry.bind.registry.BindingRegistry.strict()
                            .bind("doc.source", source));
            var node = KdlParser.parse("html-view bind=\"doc.source\" \"<p>literal</p>\"")
                    .getFirst();

            var view = assertInstanceOf(HtmlView.class, HtmlView.inflate(node, List.of(), wiring));

            assertSame(source, view.binding());
            assertEquals(List.of("bound"), wordsOf(walk(new ElementTree(view).root())));
        }

        @Test
        @DisplayName("link= names a valued action, so a help pane navigates with no Java")
        void linkAction() {
            var followed = new ArrayList<String>();
            var actions = io.github.digitalsmile.goldberry.bind.registry.ActionRegistry.strict();
            actions.bind("doc.open", (java.util.function.Consumer<String>) followed::add);
            var wiring = new Wiring(
                    actions,
                    io.github.digitalsmile.goldberry.widgets.Icons.none(),
                    io.github.digitalsmile.goldberry.bind.registry.BindingRegistry.none());
            var node = KdlParser.parse("html-view link=\"doc.open\" \"<a href=/x>go</a>\"")
                    .getFirst();

            var view = assertInstanceOf(HtmlView.class, HtmlView.inflate(node, List.of(), wiring));
            buttonsOf(walk(new ElementTree(view).root())).getFirst().onPress().run();

            assertEquals(List.of("/x"), followed);
        }

        @Test
        @DisplayName("and a node with no link= builds a view whose links are inert")
        void noLinkAction() {
            assertNull(inflate("html-view \"<a href=/x>go</a>\"").onLink());
        }
    }
}
