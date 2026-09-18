package io.github.digitalsmile.goldberry.content.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.content.ImageSource;
import io.github.digitalsmile.goldberry.content.image.Picture;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownStyles;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownView;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;

/// What a keystroke costs a rendered document, as a **count** — `docs/gaps.md` G45
/// and [ADR-0389].
///
/// [io.github.digitalsmile.goldberry.markdown.view.MarkdownFrameBenchmark] has the
/// milliseconds and asserts none of them, for the reason every benchmark in this
/// repository gives: a threshold that passes alone and fails under a parallel build
/// teaches nobody anything. What holds on any machine is how many blocks were built,
/// how many paragraphs were shaped, and whether the element under an untouched
/// paragraph is the one that was there before — so that is what this asserts.
///
/// In this package rather than beside the view because the memo belongs to
/// [SelectableDocument]'s state, and the state is what a mounted document is.
@DisplayName("a keystroke rebuilds one block")
class BlockReuseTest {

    /// Twelve paragraphs, each a different one — so a block matched to the wrong
    /// place would show up as text in the wrong order rather than as a passing test.
    private static final int BLOCKS = 12;

    private Fonts fonts;
    private RenderTree render;
    private TestFrames.Target target;
    private WidgetRenderer renderer;
    private ElementTree tree;
    private PointerRouter router;
    private Property<String> source;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(MarkdownStyles.stylesheet());
        renderer = new WidgetRenderer(sheets, fonts);
        render = RenderTree.create();
        target = TestFrames.of(420, 600, 1.0f, 0);
        source = Property.of(note(BLOCKS, -1));
        mount(MarkdownView.following(source).id("note"));
    }

    /// A tree holding `view`, wired to a router and taken through one frame.
    ///
    /// The router is here because two of the invalidation paths below are only visible
    /// to a **reader**: what a task box reports and which handler a link calls are
    /// answered by pressing them.
    private void mount(Widget view) {
        tree = new ElementTree(view);
        router = new PointerRouter();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, 420, 600));
        frame();
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
        if (render != null) {
            render.close();
        }
        if (fonts != null) {
            fonts.close();
        }
    }

    /// A note of `blocks` paragraphs, with `typed` extra characters in the middle one.
    ///
    /// @param typed -1 for the note as it was opened, 0 for a space typed into the
    ///        middle paragraph, and 1 or more for that many letters
    private static String note(int blocks, int typed) {
        var text = new StringBuilder();
        for (var index = 0; index < blocks; index++) {
            var word = index == BLOCKS / 2 && typed >= 0
                    ? (typed == 0 ? "para graph" : "paragraph" + "x".repeat(typed))
                    : "paragraph";
            text.append("Block ").append(index).append(" is a ").append(word).append(" of its own.\n\n");
        }
        return text.toString();
    }

    /// One frame of the loop a window runs, including the capture a press is routed
    /// through.
    private void frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
    }

    private SelectableDocument.DocumentState state() {
        var found = new ArrayList<SelectableDocument.SelectionHost>();
        collect(tree.root(), found);
        assertFalse(found.isEmpty(), "a markdown-view should build a selection host");
        return found.getFirst().state();
    }

    private static void collect(Element element, List<SelectableDocument.SelectionHost> found) {
        if (element.widget() instanceof SelectableDocument.SelectionHost host) {
            found.add(host);
        }
        element.children().forEach(child -> collect(child, found));
    }

    /// The elements of the document's top-level blocks, in order.
    ///
    /// The fold's column holds the selection overlay first and then one child per
    /// block, so dropping the head is what leaves the blocks.
    private List<Element> blockElements() {
        var host = find(tree.root());
        assertNotNull(host, "a markdown-view should build a selection host");
        var column = host.children().getFirst();
        return column.children().subList(1, column.children().size());
    }

    private static @org.jspecify.annotations.Nullable Element find(Element element) {
        if (element.widget() instanceof SelectableDocument.SelectionHost) {
            return element;
        }
        for (var child : element.children()) {
            var found = find(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// Every word of the document, in order — what a copy would take.
    private List<String> words() {
        var found = new ArrayList<String>();
        collectWords(tree.root(), found);
        return found;
    }

    private static void collectWords(Element element, List<String> found) {
        if (element.widget() instanceof Word word && word.child() == null) {
            found.add(word.text());
        }
        element.children().forEach(child -> collectWords(child, found));
    }

    @Test
    @DisplayName("a letter typed into one paragraph builds one block and keeps the rest")
    void oneLetter() {
        var before = blockElements();
        var widgets = before.stream().map(Element::widget).toList();

        source.set(note(BLOCKS, 3));
        frame();

        assertEquals(1, state().memo().built(), "one block changed, so one should have been built");
        assertEquals(BLOCKS - 1, state().memo().kept(), "the rest should have been handed back");

        var after = blockElements();
        assertEquals(BLOCKS, after.size());
        for (var index = 0; index < BLOCKS; index++) {
            assertSame(before.get(index), after.get(index), "block " + index + " should keep its element");
            if (index == BLOCKS / 2) {
                continue;
            }
            // **The same instance, not an equal one.** `Element.update` stops at an
            // identical description and never walks below it, which is the whole of
            // what this buys (ADR-0315, ADR-0389).
            assertSame(widgets.get(index), after.get(index).widget(), "block " + index + " should keep its widget");
        }
    }

    @Test
    @DisplayName("a space typed into one paragraph does not renumber the note")
    void aSpace() {
        // The case that made the old fold expensive. Every word carried its position
        // in the *document*, so splitting one word in two renamed every word below it
        // and the element tree matched each to its neighbour's node -- which
        // re-measured and re-laid-out everything under the cursor.
        var before = blockElements();
        var widgets = before.stream().map(Element::widget).toList();

        source.set(note(BLOCKS, 0));
        frame();

        assertEquals(1, state().memo().built());
        assertEquals(BLOCKS - 1, state().memo().kept());

        var after = blockElements();
        for (var index = 0; index < BLOCKS; index++) {
            if (index == BLOCKS / 2) {
                continue;
            }
            assertSame(widgets.get(index), after.get(index).widget(), "block " + index + " should keep its widget");
        }
        // And the split really happened, so this is not a test of a document that did
        // not change.
        var words = words();
        assertTrue(words.contains("para"), "the word should have been split: " + words);
        assertTrue(words.contains("graph"), "the word should have been split: " + words);
    }

    @Test
    @DisplayName("a kept block shapes no paragraphs")
    void nothingIsShapedTwice() {
        var paragraphs = renderer.paragraphs();
        assertNotNull(paragraphs, "the renderer should have shaped something by now");

        source.set(note(BLOCKS, 3));
        var before = paragraphs.misses();
        frame();
        var shaped = paragraphs.misses() - before;

        // The edited paragraph is seven words and one of them is new. Nothing else in
        // the note may be shaped: a miss is 56 microseconds of HarfBuzz (ADR-0037),
        // and a note that shaped every word on every keystroke is the frame G45 was
        // about.
        assertTrue(shaped <= 2, "a keystroke should shape the word that changed and no more, not " + shaped);
    }

    @Test
    @DisplayName("a block that moved is built again, and the ones above it are not")
    void aBlockRemoved() {
        var before = blockElements();
        var widgets = before.stream().map(Element::widget).toList();

        // The first paragraph goes. Every block below it is one place further up, so
        // its words report into a different block's entries and it has to be built --
        // which is the right answer and not a missed opportunity.
        source.set(note(BLOCKS, -1).substring(note(BLOCKS, -1).indexOf("Block 1")));
        frame();

        assertEquals(0, state().memo().kept(), "nothing survives a block being removed from the top");
        assertEquals(BLOCKS - 1, state().memo().built());
        assertEquals(BLOCKS - 1, blockElements().size());

        // And back again: the note is what it was, so it is built once more and then
        // settles. Nothing here may be handed a widget from before the removal.
        source.set(note(BLOCKS, -1));
        frame();
        assertEquals(BLOCKS, blockElements().size());
        assertNotSame(
                widgets.getFirst(), blockElements().getFirst().widget(), "a widget from before must not come back");

        source.set(note(BLOCKS, 1));
        frame();
        assertEquals(BLOCKS - 1, state().memo().kept(), "and then it settles");
    }

    @Test
    @DisplayName("what a reused document copies is still the document")
    void theTextSurvives() {
        // Three documents in a row, each reusing most of the one before it. What a
        // selection copies is the geometry's own record of where the words and the
        // block boundaries are, so this is the assertion that says the entries a
        // memoized block kept are still the right entries.
        source.set(note(BLOCKS, 0));
        frame();
        source.set(note(BLOCKS, 2));
        frame();

        assertEquals(expected(BLOCKS, 2), state().text());
    }

    @Test
    @DisplayName("a picture landing after a kept block is this build's picture, not the memo's")
    void aPictureLandsAfterAKeptBlock() {
        var sea = swatch(0xFF112233);
        var sky = swatch(0xFF445566);
        ImageSource nothing = src -> null;
        ImageSource found = src -> sea;
        source.set(note(BLOCKS, -1) + "![a picture](sea.png)\n");
        mount(MarkdownView.following(source).images(nothing).id("note"));

        assertTrue(pictures().isEmpty(), "an application with nothing for that src draws the alt text");

        // A source that answers. A block that drew alt text is one the fold will not
        // vouch for (`BlockMemo.Fold.keep`), so the picture arrives -- and the note is
        // built again, because a different source is a different document and this is
        // where it says so.
        tree.update(MarkdownView.following(source).images(found).id("note"));
        frame();

        assertEquals(1, pictures().size());
        assertSame(sea, pictures().getFirst().image());
        assertEquals(0, state().memo().kept(), "a view given a different source rebuilds, once");

        // And then it settles: the same source again keeps every block of the note,
        // which is what makes the identity in the signature affordable.
        tree.update(MarkdownView.following(source).images(found).id("note"));
        frame();

        assertEquals(BLOCKS + 1, state().memo().kept(), "the same source on the next build is the same document");

        // The bug this caught: the signature was four presence bits, so a **different**
        // source looked like the same document, the memo handed back the block it had,
        // and the note kept drawing the old picture for ever.
        tree.update(MarkdownView.following(source).images(src -> sky).id("note"));
        frame();

        assertEquals(1, pictures().size());
        assertSame(sky, pictures().getFirst().image(), "a swapped ImageSource is a swapped picture");
    }

    @Test
    @DisplayName("a task box built after a kept one goes on counting where that block left off")
    void tasksAreResumedAfterAKeptBlock() {
        // Two lists with a paragraph between them, so that each task is a top-level
        // block of its own and the edit is in the middle of them. `tasksSeen` is what
        // the fold carries across a block it skipped: a box whose ordinal reset would
        // toggle the wrong line of the source (ADR-0300).
        var pressed = new ArrayList<Integer>();
        source.set("- [ ] one\n\nBetween them.\n\n- [ ] two\n");
        mount(MarkdownView.following(source).onTask(pressed::add).id("note"));

        source.set("- [ ] one\n\nBetween them, edited.\n\n- [ ] two\n");
        frame();

        assertEquals(1, state().memo().built(), "the paragraph changed and nothing else");
        assertEquals(2, state().memo().kept());
        pressTaskBoxes();
        assertEquals(List.of(0, 1), pressed, "the second box is the second task, however many blocks were skipped");
    }

    @Test
    @DisplayName("a memoised link calls the handler the view has now")
    void aKeptLinkCallsTheNewHandler() {
        // The indirection ADR-0389 put in: an application that writes `onLink(this::open)`
        // in its own build hands the view a new object every frame, and a button built
        // three keystrokes ago would otherwise call the first one it ever saw.
        var first = new ArrayList<String>();
        var second = new ArrayList<String>();
        source.set(note(BLOCKS, -1) + "[go](https://example.com/a)\n");
        mount(MarkdownView.following(source).onLink(first::add).id("note"));

        tree.update(MarkdownView.following(source).onLink(second::add).id("note"));
        frame();

        assertEquals(BLOCKS + 1, state().memo().kept(), "a handler that is a different object is the same note");
        buttons().getFirst().onPress().run();

        assertEquals(List.of(), first, "the object the button was built with is a keystroke old");
        assertEquals(List.of("https://example.com/a"), second);
    }

    /// A 2x2 image, built rather than decoded: this is about the memo, not the codec.
    private static Image swatch(int argb) {
        return Image.ofArgb(2, 2, new int[] {argb, argb, argb, argb});
    }

    private List<Picture> pictures() {
        return widgets(Picture.class);
    }

    private List<Button> buttons() {
        return widgets(Button.class);
    }

    private <W> List<W> widgets(Class<W> kind) {
        var found = new ArrayList<Element>();
        collectElements(tree.root(), found);
        return found.stream()
                .map(Element::widget)
                .filter(kind::isInstance)
                .map(kind::cast)
                .toList();
    }

    private static void collectElements(Element element, List<Element> found) {
        found.add(element);
        element.children().forEach(child -> collectElements(child, found));
    }

    /// Presses every check box in the document, top to bottom, the way a reader does.
    ///
    /// Through the router and the hit-test capture rather than by calling the widget,
    /// because `task-mark` is `markdown-view`'s own and what it reports is only visible
    /// from outside it as a press (`docs/testing.md` §4).
    private void pressTaskBoxes() {
        var boxes = new ArrayList<LogicalRect>();
        for (var region : HitTest.capture(render)) {
            if (region.owner() instanceof Element element
                    && element.widget() instanceof Styled styled
                    && "task-mark".equals(styled.cssType())) {
                boxes.add(region.painted());
            }
        }
        assertFalse(boxes.isEmpty(), "the note should have drawn its check boxes");
        boxes.sort(Comparator.comparingDouble(LogicalRect::top));
        for (var box : boxes) {
            var x = box.left() + box.width() / 2;
            var y = box.top() + box.height() / 2;
            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1);
        }
    }

    /// The note as a copy of all of it reads: a space between two words, a newline
    /// between two blocks.
    private static String expected(int blocks, int typed) {
        var text = new StringBuilder();
        for (var index = 0; index < blocks; index++) {
            if (index > 0) {
                text.append('\n');
            }
            var word = index == BLOCKS / 2 && typed >= 0
                    ? (typed == 0 ? "para graph" : "paragraph" + "x".repeat(typed))
                    : "paragraph";
            text.append("Block ").append(index).append(" is a ").append(word).append(" of its own.");
        }
        return text.toString();
    }
}
