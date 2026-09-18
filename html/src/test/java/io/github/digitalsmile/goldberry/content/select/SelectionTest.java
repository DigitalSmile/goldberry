package io.github.digitalsmile.goldberry.content.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.html.view.HtmlView;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownStyles;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownView;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;

/// Selecting text in a rendered document, end to end.
///
/// **Through a real frame**, because every part of this depends on geometry that only
/// a laid-out and painted frame has: a word reports where it is through
/// [io.github.digitalsmile.goldberry.input.handler.Located], which the router
/// delivers from the hit-test capture after a paint. A test that poked the model
/// directly would assert arithmetic and prove nothing about a document on a screen
/// (ADR-0301).
///
/// So each test below runs the loop the window runs — build, style, lay out, capture,
/// notify — and then does what a reader does.
@DisplayName("selecting a document")
class SelectionTest {

    private static final String DOCUMENT = "The quick brown fox\n\njumps over it\n";

    private Fonts fonts;
    private ElementTree tree;
    private WidgetRenderer renderer;
    private PointerRouter router;
    private RenderTree render;
    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled();
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(MarkdownStyles.stylesheet());
        renderer = new WidgetRenderer(sheets, fonts);
        render = RenderTree.create();
        target = TestFrames.of(400, 300, 1.0f, 0);
        mount(MarkdownView.of(DOCUMENT).id("note"));
    }

    /// A tree holding `view`, wired to a router and taken through one frame.
    private void mount(io.github.digitalsmile.goldberry.widget.Widget view) {
        tree = new ElementTree(view);
        router = new PointerRouter();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, 400, 300));
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

    /// One frame of the window's own loop, including the capture that is what tells
    /// every word where it ended up.
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

    /// Every word of the document with the rectangle the last frame gave it.
    private List<Word> words() {
        var found = new ArrayList<Word>();
        collectWords(tree.root(), found);
        return found;
    }

    private static void collectWords(Element element, List<Word> found) {
        if (element.widget() instanceof Word word && word.child() == null) {
            found.add(word);
        }
        element.children().forEach(child -> collectWords(child, found));
    }

    /// The first word that wraps something instead of drawing text — a link's button.
    private Word wrappingWord() {
        var found = new ArrayList<Word>();
        collectWrappers(tree.root(), found);
        assertFalse(found.isEmpty(), "the document should hold a word wrapping a control");
        return found.getFirst();
    }

    private static void collectWrappers(Element element, List<Word> found) {
        if (element.widget() instanceof Word word && word.child() != null) {
            found.add(word);
        }
        element.children().forEach(child -> collectWrappers(child, found));
    }

    /// The middle of the `index`th word, in the window's coordinates — which is where
    /// a reader would put the pointer.
    private LogicalRect rectOf(int index) {
        return rectOf(words().get(index));
    }

    /// Where the last frame painted `word`.
    private LogicalRect rectOf(Word word) {
        for (var region : HitTest.capture(render)) {
            if (region.owner() instanceof Element element && element.widget() == word) {
                return region.painted();
            }
        }
        throw new AssertionError("the word '" + word.text() + "' was never painted");
    }

    private void press(LogicalRect rect, int clickCount) {
        router.pointerPressed(
                rect.left() + rect.width() / 2,
                rect.top() + rect.height() / 2,
                PointerEvent.Button.PRIMARY,
                clickCount);
    }

    private void moveTo(LogicalRect rect) {
        router.pointerMoved(rect.left() + rect.width() / 2, rect.top() + rect.height() / 2);
    }

    private void release(LogicalRect rect) {
        router.pointerReleased(
                rect.left() + rect.width() / 2, rect.top() + rect.height() / 2, PointerEvent.Button.PRIMARY, 1);
    }

    @Test
    @DisplayName("every word reports where the frame put it")
    void wordsAreLocated() {
        // The foundation the rest of this file stands on, asserted on its own so that a
        // failure says "the geometry never arrived" rather than "the selection is
        // empty".
        var words = words();

        assertEquals(7, words.size(), "The quick brown fox / jumps over it");
        for (var index = 0; index < words.size(); index++) {
            var rect = rectOf(index);
            assertTrue(rect.width() > 0 && rect.height() > 0, "word " + index + " has no rectangle");
        }
        assertTrue(rectOf(4).top() > rectOf(0).top(), "the second paragraph is below the first");
    }

    @Test
    @DisplayName("a drag selects what it was dragged across")
    void dragSelects() {
        press(rectOf(1), 1);
        moveTo(rectOf(2));
        release(rectOf(2));

        // From the middle of "quick" to the middle of "brown", which is what the
        // pointer was actually over -- character offsets, not whole words.
        assertEquals("ck bro", state().selectedText());
    }

    @Test
    @DisplayName("and across a paragraph, with the newline the document implies")
    void dragAcrossBlocks() {
        press(rectOf(3), 1);
        moveTo(rectOf(4));
        release(rectOf(4));

        // "fox" is the end of one paragraph and "jumps" the start of the next, so what
        // comes out has the break in it. A copy that joined them would paste
        // `foxjumps`, which is the whole reason a word carries the separator in front
        // of it (ADR-0301).
        assertEquals("x\njum", state().selectedText());
    }

    @Test
    @DisplayName("a double-click takes the word under it")
    void doubleClickTakesAWord() {
        press(rectOf(2), 2);

        assertEquals("brown", state().selectedText());
    }

    @Test
    @DisplayName("and a triple-click takes the block")
    void tripleClickTakesTheBlock() {
        press(rectOf(2), 3);

        assertEquals("The quick brown fox", state().selectedText(), "the paragraph, and not the document");
    }

    @Test
    @DisplayName("a selection that ends inside a link washes as much of it as it covers")
    void aSelectionIntoALinkWashesIt() {
        // The bug this caught: a link is a `button` inside a `Word`, so that word draws
        // no text and never hands the geometry a shaped paragraph. Every offset in it
        // answered the box's left edge, which made the two ends of a link the same
        // place -- a selection reaching into one washed none of it, while the text it
        // copied said it had.
        mount(MarkdownView.of("Read the [help](/x) first.\n").onLink(href -> {}).id("note"));
        var link = rectOf(wrappingWord());

        press(rectOf(1), 1);
        moveTo(link);
        release(link);

        var selected = state().selectedText();
        assertTrue(selected.endsWith(" he"), "the copy reaches halfway into the label: " + selected);
        var washed = state().washed();
        assertFalse(washed.isEmpty(), "a drag into a link should wash the part of it that is selected");
        var last = washed.getLast();
        assertEquals(link.left(), last.left(), 0.5, "the wash over a link begins where the link does");
        assertTrue(
                last.width() > link.width() * 0.3 && last.width() < link.width() * 0.7,
                "a caret in the middle of the label washes about half the box, not " + last.width());
    }

    @Test
    @DisplayName("and a double-click on a link highlights the whole of it")
    void doubleClickTakesAWholeLink() {
        mount(MarkdownView.of("Read the [help](/x) first.\n").onLink(href -> {}).id("note"));
        var link = rectOf(wrappingWord());

        press(link, 2);

        assertEquals("help", state().selectedText());
        var washed = state().washed();
        assertEquals(1, washed.size(), "one word, so one rectangle");
        assertEquals(link.left(), washed.getFirst().left(), 0.5);
        assertEquals(link.width(), washed.getFirst().width(), 0.5, "the whole of it, which is what a reader sees");
    }

    @Test
    @DisplayName("inline content after a block is a block of its own, not the tail of the one above")
    void inlineAfterABlock() {
        // An HTML page rather than a note, because this is `html-view`'s fold: it
        // announced one block boundary for a whole run of nodes, so the words after the
        // `p` inherited the paragraph's block. What a copy says is where that shows --
        // a space where the document means a newline, and a triple-click on either one
        // taking both.
        mount(HtmlView.of("<div><p>one</p>two</div>"));

        assertEquals("one\ntwo", state().text(), "a browser draws two lines and a copy keeps the break");
    }

    @Test
    @DisplayName("Ctrl+A takes the document and Escape lets it go")
    void selectAllAndClear() {
        router.focusById("note", false);
        var state = state();

        state.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.A, Modifiers.of(Mod.CTRL), false, tree.root()));
        assertEquals("The quick brown fox\njumps over it", state.selectedText());

        state.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, tree.root()));
        assertEquals("", state.selectedText());
        assertFalse(state.hasSelection());
    }

    @Test
    @DisplayName("a task list copies as its text, with no bullet the reader never saw")
    void aTaskListHasNoPhantomBullet() {
        // The fold used to build the bullet before it knew whether the item wanted
        // one, and throw the widget away for a task -- but the word had already been
        // registered here by then. Nothing on the screen changed and every Ctrl+A of a
        // list of things to do came out with a `•` in front of each line.
        //
        // The ordinary item is in the document on purpose: its bullet **is** part of
        // the text, and a fix that dropped that one too would be a different bug.
        mount(MarkdownView.of("- [ ] milk\n- [x] bread\n- an ordinary bullet\n").id("note"));
        router.focusById("note", false);
        var state = state();

        state.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.A, Modifiers.of(Mod.CTRL), false, tree.root()));

        assertEquals("milk\nbread\n•\nan ordinary bullet", state.selectedText());
    }

    @Test
    @DisplayName("a copy puts the selected text on the clipboard, and nothing when there is none")
    void copies() {
        var clipboard = new Recording();
        var state = state();

        assertFalse(state.copyTo(clipboard), "nothing is selected, so nothing is copied");

        press(rectOf(2), 2);
        assertTrue(state.copyTo(clipboard));
        assertEquals("brown", clipboard.text());
    }

    @Test
    @DisplayName("a press with nothing selected leaves nothing selected")
    void aClickClears() {
        press(rectOf(2), 2);
        assertTrue(state().hasSelection());

        press(rectOf(0), 1);
        release(rectOf(0));

        assertFalse(state().hasSelection(), "a click is a selection of nothing, which is how a reader dismisses one");
    }

    @Test
    @DisplayName("a document that changes under a selection drops it")
    void aRebuildDropsAStaleSelection() {
        // A **bound** view, because that is the case this exists for: a preview
        // re-parses on every keystroke, and a selection measured against the old words
        // would highlight whatever is now at those indices (ADR-0301).
        var source = io.github.digitalsmile.goldberry.bind.Property.of(DOCUMENT);
        mount(MarkdownView.following(source).id("note"));

        press(rectOf(2), 2);
        assertTrue(state().hasSelection());

        source.set("Entirely different words here\n");
        frame();

        assertFalse(state().hasSelection());
    }

    @Test
    @DisplayName("and a rebuild that changes nothing keeps it")
    void aRebuildThatChangesNothingKeepsIt() {
        // The other half, and the one that makes the rule worth having: a frame that
        // re-parses the same text must not take a reader's selection away.
        var source = io.github.digitalsmile.goldberry.bind.Property.of(DOCUMENT);
        mount(MarkdownView.following(source).id("note"));

        press(rectOf(2), 2);
        source.set(DOCUMENT);
        frame();

        assertEquals("brown", state().selectedText());
    }

    @Test
    @DisplayName("a word scrolled up is selected where it now is, not where it was laid out")
    void selectionFollowsAScroll() {
        // The reason the geometry comes from `Located` rather than from the layout:
        // `located` reports where a word was **painted**, which inside a viewport is
        // where it has been scrolled to. A selection built from layout positions would
        // pick the word that *used* to be under the pointer -- and would look right
        // until somebody scrolled (ADR-0119, ADR-0301).
        //
        // `height(...)` because this viewport is the root of the tree and nothing above
        // it bounds one: a `scroll` as tall as its content has nothing to scroll.
        mount(new io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll(
                        java.util.List.of(MarkdownView.of(DOCUMENT.repeat(8)).id("note")),
                        io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis.VERTICAL,
                        io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE.id("pane"))
                .height(200));
        // A viewport learns how much it overflows from the frame that laid it out
        // (ADR-0117), so the wheel has to come after one.
        frame();
        frame();
        var before = rectOf(4).top();

        router.pointerWheel(200, 150, 0, 7, Modifiers.NONE);
        for (var i = 0; i < 3; i++) {
            frame();
        }
        assertTrue(rectOf(4).top() < before, "the wheel should have moved the document up");

        // A word that is on screen **now**, which after a scroll is not the one that
        // was there before: the whole point of reading the painted rectangle.
        var visible = firstVisible();
        press(rectOf(visible), 2);

        assertEquals(words().get(visible).text(), state().selectedText());
    }

    /// The first word the viewport is currently showing.
    private int firstVisible() {
        for (var index = 0; index < words().size(); index++) {
            var rect = rectOf(index);
            if (rect.top() >= 0 && rect.bottom() <= 200 && rect.width() > 0) {
                return index;
            }
        }
        throw new AssertionError("the viewport is showing no words at all");
    }

    /// A clipboard that remembers, which is all this needs — the real one is the
    /// window's and is three methods wide.
    private static final class Recording implements Clipboard {

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
    }
}
