package dev.goldberry.content.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.Host;
import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.html.view.HtmlView;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.markdown.view.MarkdownStyles;
import dev.goldberry.markdown.view.MarkdownView;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.clipboard.PrimarySelection;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.scroll.EdgeScroll;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.core.scroll.ScrollController;

/// Selecting text in a rendered document, end to end.
///
/// **Through a real frame**, because every part of this depends on geometry that only
/// a laid-out and painted frame has: a word reports where it is through
/// [dev.goldberry.input.handler.Located], which the router
/// delivers from the hit-test capture after a paint. A test that poked the model
/// directly would assert arithmetic and prove nothing about a document on a screen.
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
    private void mount(Widget view) {
        mount(view, null);
    }

    /// The same, in a window — which is the one way a press reaches a primary
    /// selection, through the element that heard it.
    private void mount(Widget view, @Nullable Host host) {
        tree = new ElementTree(view, host);
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
        // of it.
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
    @DisplayName("a hard break inside a paragraph is a newline in what is copied")
    void hardBreakIsANewline() {
        // The copy side of a hard break. The picture is two lines, and a copy that joined
        // them with a space would be saying the author's line ending was the width of
        // the pane -- which is exactly what a *soft* break means and exactly what this
        // one does not. `Inlines.text` and the HTML writer's `<br>` already agree; the
        // price is that a triple-click takes one line rather than the paragraph, which
        // is the right one for an address or a stanza.
        mount(MarkdownView.of("one  \ntwo\n").id("note"));

        assertEquals("one\ntwo", state().text());
    }

    @Test
    @DisplayName("and a soft break is the space it draws")
    void softBreakIsASpace() {
        mount(MarkdownView.of("one\ntwo\n").id("note"));

        assertEquals("one two", state().text(), "a newline the editor wrapped is not a line the author ended");
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
        // would highlight whatever is now at those indices.
        var source = dev.goldberry.bind.Property.of(DOCUMENT);
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
        var source = dev.goldberry.bind.Property.of(DOCUMENT);
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
        // until somebody scrolled.
        //
        // `height(...)` because this viewport is the root of the tree and nothing above
        // it bounds one: a `scroll` as tall as its content has nothing to scroll.
        mount(new Scroll(
                        List.of(MarkdownView.of(DOCUMENT.repeat(8)).id("note")),
                        ScrollAxis.VERTICAL,
                        Attributes.NONE.id("pane"))
                .height(200));
        // A viewport learns how much it overflows from the frame that laid it out
        // so the wheel has to come after one.
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

    /// A drag held below a pane carries the viewport on, for both views.
    ///
    /// The pane is 200 tall in a window 300 tall, so the pointer can be put below it
    /// and still be in the window — which is where a reader's pointer is when they
    /// drag past the bottom of a preview beside an editor. Against a virtual clock,
    /// because what is asserted is how far each frame moved, and that is a speed
    /// times the frame's time.
    @Nested
    @DisplayName("a drag held past the edge of the pane")
    class HeldAtTheEdge {

        private final Clock.Virtual clock = Clock.virtual();

        private final ScrollController pane = new ScrollController();

        /// `view` in a pane 200 tall, taken through the frames that measure it.
        private void inAPane(Widget view) {
            renderer.clock(clock);
            mount(new Scroll(List.of(view), ScrollAxis.VERTICAL, Attributes.NONE.id("pane"))
                    .height(200)
                    .controlledBy(pane));
            frame();
            frame();
        }

        /// One frame, 16 ms after the last.
        private void step() {
            clock.advance(16);
            frame();
        }

        /// Presses on the first word on screen and drags to 60 below the pane.
        private void dragBelow() {
            var first = rectOf(firstVisible());
            router.pointerPressed(first.left() + 1, first.top() + 1, PointerEvent.Button.PRIMARY, 1);
            frame();
            router.pointerMoved(200, 260);
            frame();
        }

        private List<Double> offsets(int frames) {
            var offsets = new ArrayList<Double>();
            for (var i = 0; i < frames; i++) {
                step();
                offsets.add(pane.position().offsetY());
            }
            return offsets;
        }

        @Test
        @DisplayName("a markdown-view's pane scrolls on, frame by frame, and the selection follows it")
        void markdownScrollsOn() {
            inAPane(MarkdownView.of(DOCUMENT.repeat(12)).id("note"));
            scrollsOn();
        }

        @Test
        @DisplayName("and an html-view's")
        void htmlScrollsOn() {
            inAPane(HtmlView.of("<p>The quick brown fox</p><p>jumps over it</p>".repeat(12)));
            scrollsOn();
        }

        private void scrollsOn() {
            dragBelow();
            // Before the edge, this was where it ended: every word is clipped to the
            // pane, a pointer below it is over none of them, and the drag stopped.
            var selected = state().selectedText().length();
            assertTrue(selected > 0, "the drag below the pane selected nothing");

            var offsets = offsets(10);

            // Sixty pixels past the edge is seventy-six past the band's inner edge,
            // which is 760 a second and 12.16 a frame.
            var perFrame = EdgeScroll.speed(EdgeScroll.BAND + 60) * 0.016;
            for (var i = 1; i < offsets.size(); i++) {
                assertEquals(
                        perFrame,
                        offsets.get(i) - offsets.get(i - 1),
                        1e-6,
                        "frame " + i + " moved by something other than speed times time: " + offsets);
            }
            assertTrue(
                    state().selectedText().length() > selected,
                    "the pane moved " + offsets.getLast() + " and the selection stayed at " + selected + " characters");
        }

        @Test
        @DisplayName("the selection ends at a word on screen, at the bottom of the pane")
        void theEndIsOnScreen() {
            inAPane(MarkdownView.of(DOCUMENT.repeat(12)).id("note"));
            dragBelow();
            offsets(10);
            frame();

            var washed = state().washed();
            var last = washed.getLast();
            assertTrue(
                    last.bottom() > 150 && last.top() < 200,
                    "the last washed rectangle is at " + last + ", not at the bottom of the pane");
        }

        @Test
        @DisplayName("it stops on the release")
        void stopsOnRelease() {
            inAPane(MarkdownView.of(DOCUMENT.repeat(12)).id("note"));
            dragBelow();
            offsets(3);
            router.pointerReleased(200, 260, PointerEvent.Button.PRIMARY, 1);
            var released = pane.position().offsetY();
            var text = state().selectedText();

            offsets(3);

            assertTrue(released > 0);
            assertEquals(released, pane.position().offsetY(), 1e-9, "the pane went on after the release");
            assertEquals(text, state().selectedText(), "the selection went on after the release");
            assertFalse(state().autoScrolling(), "a released drag kept the frame loop awake");
        }

        @Test
        @DisplayName("and when the pointer comes back inside")
        void stopsInside() {
            inAPane(MarkdownView.of(DOCUMENT.repeat(12)).id("note"));
            dragBelow();
            offsets(3);
            router.pointerMoved(200, 100);
            var inside = pane.position().offsetY();

            offsets(3);

            assertEquals(inside, pane.position().offsetY(), 1e-9);
        }

        @Test
        @DisplayName("and at the end of the document, where it stops asking for frames")
        void stopsAtTheEnd() {
            inAPane(MarkdownView.of(DOCUMENT.repeat(5)).id("note"));
            dragBelow();
            offsets(80);

            var end = pane.position();
            assertEquals(end.overflowY(), end.offsetY(), 1e-6, "eighty frames did not reach the end");
            step();
            assertFalse(state().autoScrolling(), "a pane at its end kept the frame loop awake");
            assertTrue(
                    state().selectedText().endsWith("over it"),
                    "the drag should have selected to the end of the document");
        }
    }

    /// A clipboard that remembers, which is all this needs — the real one is the
    /// window's and is three methods wide.
    /// A window with nothing in it but a primary selection: every other question
    /// is answered by `Host`'s own default, or with nothing.
    ///
    /// A proxy rather than a class, because a `Host` is forty methods and what this
    /// file asserts needs one of them.
    private static Host hostWith(PrimarySelection primary) {
        return (Host) Proxy.newProxyInstance(
                Host.class.getClassLoader(), new Class<?>[] {Host.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("primarySelection")) {
                        return Optional.of(primary);
                    }
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    return switch (method.getName()) {
                        case "equals" -> proxy == arguments[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "Host[a primary selection]";
                        default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                    };
                });
    }

    /// X11's middle-click buffer, which a document fills and never pastes from.
    @Nested
    @DisplayName("the primary selection")
    class ThePrimarySelection {

        @Test
        @DisplayName("a drag publishes what it selected when the button comes up")
        void aDragPublishesOnRelease() {
            var primary = new Primary();
            mount(MarkdownView.of(DOCUMENT).id("note"), hostWith(primary));

            press(rectOf(1), 1);
            moveTo(rectOf(2));
            assertEquals(0, primary.writes, "a drag in progress is not an ownership change yet");

            release(rectOf(2));

            // From the middle of one word to the middle of the next, which is what
            // the drag selected -- and exactly what a copy would take.
            assertEquals(state().selectedText(), primary.text());
            assertTrue(primary.text().contains(" "), "the drag crossed a word boundary");
            assertEquals(1, primary.writes);
        }

        @Test
        @DisplayName("a double click publishes its word, and a click that selects nothing publishes nothing")
        void clicks() {
            var primary = new Primary();
            mount(MarkdownView.of(DOCUMENT).id("note"), hostWith(primary));

            press(rectOf(2), 2);
            release(rectOf(2));
            assertEquals("brown", primary.text());

            press(rectOf(0), 1);
            release(rectOf(0));
            assertEquals("brown", primary.text(), "a click is a selection of nothing");
            assertEquals(1, primary.writes);
        }

        @Test
        @DisplayName("Ctrl+A publishes the lot")
        void selectAllPublishes() {
            var primary = new Primary();
            mount(MarkdownView.of(DOCUMENT).id("note"), hostWith(primary));
            router.focusById("note", false);
            var state = state();

            state.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.A, Modifiers.of(Mod.CTRL), false, tree.root()));

            assertEquals(state.text(), primary.text());
        }

        @Test
        @DisplayName("the seam offers what is selected, and nothing when there is none")
        void offers() {
            var primary = new Primary();
            var state = state();

            assertFalse(state.offerTo(primary));

            press(rectOf(2), 2);
            assertTrue(state.offerTo(primary));
            assertEquals("brown", primary.text());
        }

        @Test
        @DisplayName("a window without one is not an error")
        void noWindowNoPrimary() {
            press(rectOf(2), 2);
            release(rectOf(2));

            assertTrue(state().hasSelection(), "the selection is still made; it just goes nowhere");
        }
    }

    /// A primary selection with nothing under it, counting writes.
    private static final class Primary implements PrimarySelection {

        private String text = "";

        private int writes;

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
            writes++;
            return true;
        }
    }

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
