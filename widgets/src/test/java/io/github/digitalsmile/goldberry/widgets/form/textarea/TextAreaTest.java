package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// §4's multi-line field.
///
/// Most of what a `text-area` does is `text-input`'s, unchanged and already
/// tested: the editing model, the undo history, the clipboard, the caret's blink.
/// What is here is only what a **second dimension** makes different — `Enter`,
/// vertical movement and the column it keeps, a selection that is more than one
/// rectangle, and a control that grows.
class TextAreaTest {

    private final TestHost host = new TestHost();

    /// Mounted and measured 300 points wide, which is wide enough that the test
    /// texts wrap where the newlines are and nowhere else — so "line" means what
    /// the test wrote and the wrap is not silently under test too.
    private ElementTree mounted(TextArea area) {
        var tree = new ElementTree(area, host);
        render(tree);
        box(tree).measured(new Extent(300, 200), new Extent(300, 200));
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        render(tree, null);
    }

    /// The same, with one application rule on top — how a test says `text-align`
    /// without inventing a stylesheet of its own ([ADR-0324]).
    private void render(ElementTree tree, String css) {
        tree.flush();
        var sheets = new java.util.ArrayList<io.github.digitalsmile.goldberry.css.Stylesheet>(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()));
        if (css != null) {
            sheets.add(io.github.digitalsmile.goldberry.css.Stylesheet.parse(
                    io.github.digitalsmile.goldberry.css.cascade.CascadeLayer.APPLICATION, css));
        }
        new WidgetRenderer(sheets, TestFont.get()).render(tree);
    }

    private TextAreaBox box(ElementTree tree) {
        return (TextAreaBox) tree.root().children().getFirst().widget();
    }

    private String text(ElementTree tree) {
        return ((TextAreaState) tree.root().state().orElseThrow()).heldText();
    }

    private void key(ElementTree tree, Key key, Modifiers modifiers) {
        box(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null));
        render(tree);
    }

    private void key(ElementTree tree, Key key) {
        key(tree, key, Modifiers.NONE);
    }

    private void type(ElementTree tree, String text) {
        box(tree).onText(new TextEvent(text, null));
        render(tree);
    }

    private void focus(ElementTree tree, boolean gained) {
        box(tree).onFocusChanged(gained, true);
        render(tree);
    }

    @Nested
    @DisplayName("opening on a document")
    class Opening {

        /// Twenty lines into an area that shows three: enough that the top and the
        /// caret's line cannot both be on screen.
        private TextArea document() {
            var text = new StringBuilder();
            for (var line = 1; line <= 20; line++) {
                text.append("line ").append(line).append('\n');
            }
            return new TextArea(text.toString(), null);
        }

        /// **The defect this pins.** `TextEdit.of` puts the caret at the end of the
        /// value it is given — right for a field somebody is about to type into —
        /// and the area kept the caret's line in view from its very first layout. So
        /// an area opened on a note showed its *last* line, and a reader had to
        /// scroll up to find the beginning of their own document.
        @Test
        @DisplayName("shows the top of its value, not the end of it")
        void startsAtTheTop() {
            var tree = mounted(document());

            assertEquals(0, scrollOffset(tree), 0.01, "nobody has touched it, so the content starts where it starts");
        }

        @Test
        @DisplayName("chases the caret once the keyboard arrives")
        void focusMakesTheCaretMatter() {
            var tree = mounted(document());

            focus(tree, true);

            assertTrue(
                    scrollOffset(tree) > 0,
                    "the caret is at the end of twenty lines, and a focused area shows where typing would go");
        }

        @Test
        @DisplayName("chases it after an edit, too")
        void typingMakesTheCaretMatter() {
            var tree = mounted(document());

            type(tree, "!");

            assertTrue(scrollOffset(tree) > 0, "what was typed has to be visible");
        }

        private double scrollOffset(ElementTree tree) {
            return ((TextAreaState) tree.root().state().orElseThrow()).scrolledBy();
        }
    }

    @Nested
    @DisplayName("filling its container")
    class Filling {

        private TextArea document() {
            var text = new StringBuilder();
            for (var line = 1; line <= 40; line++) {
                text.append("line ").append(line).append('\n');
            }
            return new TextArea(text.toString(), null).fill(true);
        }

        /// The measured height decides how many lines are on screen, so this is the
        /// number everything else in the control is derived from.
        private int visibleRows(ElementTree tree) {
            return ((TextAreaBox) tree.root().children().getFirst().widget()).maxRows();
        }

        @Test
        @DisplayName("takes its line count from the height it was given, not from max-rows")
        void linesComeFromTheHeight() {
            var tree = mounted(document());

            // 200 points tall, less the padding, at the test font's line height. The
            // assertion is the *relationship* rather than a magic number: a filling
            // area shows many more lines than `DEFAULT_MAX_ROWS`, because the
            // container is what decides.
            assertTrue(visibleRows(tree) > 5, "a 200-point pane holds more than five lines: " + visibleRows(tree));
            assertNotEquals(
                    TextArea.DEFAULT_MAX_ROWS, visibleRows(tree), "max-rows is what a filling area stops consulting");
        }

        @Test
        @DisplayName("scrolls within that height rather than growing past it")
        void scrollsInsideIt() {
            var tree = mounted(document());
            var rows = visibleRows(tree);

            focus(tree, true);

            // The caret is at the end of forty lines and only `rows` of them fit, so
            // the content has scrolled by the difference -- which is the arithmetic
            // `maximumScroll` does, checked through what it produces.
            assertTrue(scrolledBy(tree) > 0, "forty lines in a pane that holds " + rows + " has somewhere to scroll");
        }

        @Test
        @DisplayName("follows a pane that got taller")
        void aTallerPaneShowsMore() {
            var tree = mounted(document());
            var before = visibleRows(tree);

            box(tree).measured(new Extent(300, 400), new Extent(300, 400));
            render(tree);

            assertTrue(
                    visibleRows(tree) > before,
                    "twice the height is more lines: " + before + " then " + visibleRows(tree));
        }

        @Test
        @DisplayName("leaves an ordinary area sized by its text")
        void anOrdinaryAreaIsUnchanged() {
            var tree = mounted(new TextArea("one\ntwo", null));

            assertEquals(
                    TextArea.DEFAULT_MAX_ROWS,
                    visibleRows(tree),
                    "§4's auto-grow is what a field in a form does, and this changes none of it");
        }

        private double scrolledBy(ElementTree tree) {
            return ((TextAreaState) tree.root().state().orElseThrow()).scrolledBy();
        }
    }

    @Nested
    @DisplayName("more than one line")
    class Lines {

        @Test
        @DisplayName("Enter inserts a newline rather than reaching the form")
        void enterTypes() {
            var tree = mounted(new TextArea("Yoga", null));

            var enter = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, null);
            box(tree).onKey(enter);
            render(tree);

            assertEquals("Yoga\n", text(tree));
            // Consumed, or a form's default button would fire on every line break.
            assertTrue(enter.isConsumed());
        }

        @Test
        @DisplayName("a read-only area leaves Enter alone")
        void readOnlyLeavesEnter() {
            var tree = mounted(new TextArea("Yoga", null).readOnly(true));

            var enter = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, null);
            box(tree).onKey(enter);

            assertEquals("Yoga", text(tree));
            assertFalse(enter.isConsumed(), "so the form around it still gets it");
        }

        @Test
        @DisplayName("Down moves a line and Up comes back")
        void verticalMovement() {
            var tree = mounted(new TextArea("one\ntwo\nthree", null));
            key(tree, Key.HOME, Modifiers.of(Mod.CTRL));

            key(tree, Key.DOWN);
            assertEquals(4, box(tree).edit().caret(), "the start of the second line");

            key(tree, Key.DOWN);
            assertEquals(8, box(tree).edit().caret());

            key(tree, Key.UP);
            assertEquals(4, box(tree).edit().caret());
        }

        @Test
        @DisplayName("a run of Down keeps the column it started in")
        void keepsTheColumn() {
            // Down from the end of a long line, through a short one, into another
            // long one. Recomputing the column each time would leave the caret at
            // the end of "b" and never come back.
            var tree = mounted(new TextArea("aaaaaaaa\nb\ncccccccc", null));
            key(tree, Key.END, Modifiers.of(Mod.CTRL));
            key(tree, Key.HOME);
            key(tree, Key.UP);
            key(tree, Key.UP);
            key(tree, Key.END);
            var column = box(tree).edit().caret();
            assertEquals(8, column, "at the end of the first line");

            key(tree, Key.DOWN);
            assertEquals(10, box(tree).edit().caret(), "clamped to the end of the short line");

            key(tree, Key.DOWN);

            // Back out to roughly where it started, rather than to 11 — which is
            // where the short line's end would have stranded it. Not an exact
            // offset, because the column is an **x**: eight `a`s and eight `c`s
            // are not the same width, so which `c` that x falls on is the font's
            // business and not this test's.
            assertTrue(
                    box(tree).edit().caret() >= 17,
                    "the run lost its column and came back at "
                            + box(tree).edit().caret());
        }

        @Test
        @DisplayName("a horizontal move abandons the column")
        void horizontalClearsTheColumn() {
            var tree = mounted(new TextArea("aaaaaaaa\nb\ncccccccc", null));
            key(tree, Key.HOME, Modifiers.of(Mod.CTRL));
            key(tree, Key.END);
            key(tree, Key.DOWN);
            key(tree, Key.LEFT);

            key(tree, Key.DOWN);

            // The run ended at the Left, so this starts a new one from where the
            // caret actually is rather than from where the old run was aiming.
            assertEquals(11, box(tree).edit().caret());
        }

        @Test
        @DisplayName("Up on the first line goes to the very start")
        void upAtTheTop() {
            var tree = mounted(new TextArea("one\ntwo", null));
            key(tree, Key.HOME, Modifiers.of(Mod.CTRL));
            key(tree, Key.RIGHT);

            key(tree, Key.UP);

            assertEquals(0, box(tree).edit().caret());
        }

        @Test
        @DisplayName("Home and End are the line's, and Ctrl makes them the document's")
        void homeAndEnd() {
            var tree = mounted(new TextArea("one\ntwo\nthree", null));
            key(tree, Key.HOME, Modifiers.of(Mod.CTRL));
            key(tree, Key.DOWN);

            key(tree, Key.END);
            assertEquals(7, box(tree).edit().caret(), "the end of the second line");

            key(tree, Key.HOME);
            assertEquals(4, box(tree).edit().caret());

            key(tree, Key.END, Modifiers.of(Mod.CTRL));
            assertEquals(13, box(tree).edit().caret(), "the end of everything");
        }
    }

    @Nested
    @DisplayName("selecting")
    class Selecting {

        @Test
        @DisplayName("a selection across lines is one rectangle per line")
        void oneRectanglePerLine() {
            var tree = mounted(new TextArea("one\ntwo\nthree", null));
            focus(tree, true);
            key(tree, Key.A, Modifiers.of(Mod.CTRL));

            // Three lines selected, so three highlights — a run of wrapped text
            // is not a rectangle, which is the whole of what a second dimension
            // costs the selection.
            var parts = box(tree).children();
            var highlights = parts.stream()
                    .filter(io.github.digitalsmile.goldberry.widgets.form.parts.Highlight.class::isInstance)
                    .count();
            assertEquals(
                    box(tree).maxRows(), highlights, "the count is the bound, and render fills what the layout needs");
            assertTrue(box(tree).edit().hasSelection());
        }

        @Test
        @DisplayName("Shift+Down extends down a line")
        void shiftExtends() {
            var tree = mounted(new TextArea("one\ntwo", null));
            key(tree, Key.HOME, Modifiers.of(Mod.CTRL));

            key(tree, Key.DOWN, Modifiers.of(Mod.SHIFT));

            assertEquals("one\n", box(tree).edit().selectedText());
        }
    }

    @Nested
    @DisplayName("the clipboard keeps the newlines")
    class Clipboard {

        @Test
        @DisplayName("a pasted paragraph stays a paragraph")
        void pasteKeepsLines() {
            host.clipboardText("one\ntwo");
            var tree = mounted(new TextArea());

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            // The one place this differs from `text-input`, which flattens: a
            // multi-line control is exactly where a pasted paragraph belongs.
            assertEquals("one\ntwo", text(tree));
        }

        @Test
        @DisplayName("a Windows paste is one document, not one with stray returns")
        void normalisesCarriageReturns() {
            host.clipboardText("one\r\ntwo\rthree");
            var tree = mounted(new TextArea());

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            assertEquals("one\ntwo\nthree", text(tree));
        }
    }

    @Nested
    @DisplayName("growing")
    class Growing {

        @Test
        @DisplayName("a document that names rows gets at least that many")
        void respectsRows() {
            var area = new TextArea().rows(2, 6);

            assertEquals(2, area.rows());
            assertEquals(6, area.maxRows());
        }

        @Test
        @DisplayName("naming rows and not a maximum raises the maximum to match")
        void rowsRaiseTheMaximum() {
            var area = (TextArea) Widgets.inflater()
                    .inflateAll(KdlParser.parse("text-area rows=14"))
                    .getFirst();

            // Otherwise `rows=14` would be a control asked for fourteen lines and
            // clamped back to the default ten, which is a smaller area than the
            // one the document wrote.
            assertEquals(14, area.rows());
            assertTrue(area.maxRows() >= 14);
        }

        @Test
        @DisplayName("a maximum below the minimum is refused where it is written")
        void refusesAnImpossibleRange() {
            assertThrows(IllegalArgumentException.class, () -> new TextArea().rows(6, 2));
            assertThrows(IllegalArgumentException.class, () -> new TextArea().rows(0, 4));
        }
    }

    @Nested
    @DisplayName("what it shares with `text-input`, and what it does not")
    class Shared {

        @Test
        @DisplayName("the parts are the same four, so the stylesheet is one")
        void sharesItsParts() {
            var tree = mounted(new TextArea("one", null));
            var parts = box(tree).children();
            var maxRows = new TextArea("one", null).maxRows();

            // `maxRows` highlights, the value, the caret, then `maxRows`
            // underlines. The bounded runs are what a wrapped selection and a
            // wrapped composition need (ADR-0292), and the two singletons sit
            // between them at fixed positions so the reconciler matches them by
            // index through every edit.
            for (var i = 0; i < maxRows; i++) {
                assertTrue(
                        parts.get(i) instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Highlight,
                        "part " + i + " should be a highlight");
            }
            assertTrue(parts.get(maxRows) instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Value);
            assertTrue(parts.get(maxRows + 1) instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Caret);
            for (var i = 0; i < maxRows; i++) {
                assertTrue(
                        parts.get(maxRows + 2 + i)
                                instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Underline,
                        "part " + (maxRows + 2 + i) + " should be an underline");
            }
            assertEquals(2 * maxRows + 2, parts.size());
        }

        @Test
        @DisplayName("focus does not select everything, unlike a one-line field")
        void tabDoesNotSelectAll() {
            var tree = mounted(new TextArea("a paragraph somebody wrote", null));

            focus(tree, true);

            // Replacing a whole paragraph because somebody tabbed into it is a
            // different scale of accident from replacing a name — and the next
            // keystroke would do it.
            assertFalse(box(tree).edit().hasSelection());
        }

        @Test
        @DisplayName("it asks the platform for text input like every editable thing")
        void asksForTextInput() {
            var tree = mounted(new TextArea());

            assertFalse(host.isTextInputActive());
            focus(tree, true);
            assertTrue(host.isTextInputActive());
            focus(tree, false);
            assertFalse(host.isTextInputActive());
        }

        @Test
        @DisplayName("the node a stylesheet sees is `text-area`, once")
        void oneNode() {
            var tree = mounted(new TextArea());

            assertEquals("text-area", box(tree).cssType());
            assertNotEquals(
                    "text-area",
                    tree.root().widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled s
                            ? s.cssType()
                            : "");
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a document writes what §4 spells")
        void inflates() {
            var area = (TextArea)
                    Widgets.inflater().inflateAll(KdlParser.parse("""
                    text-area value="Hello" placeholder="Say something" rows=4 max-rows=8 \
                              max-length=200
                    """)).getFirst();

            assertEquals("Hello", area.value());
            assertEquals("Say something", area.placeholder());
            assertEquals(4, area.rows());
            assertEquals(8, area.maxRows());
            assertEquals(200, area.maxLength());
        }

        @Test
        @DisplayName("it goes inside a `field` like any other control")
        void insideAField() {
            var widgets = Widgets.inflater().inflateAll(KdlParser.parse("""
                    field label="Bio" { text-area rows=4 }
                    """));
            var field = (io.github.digitalsmile.goldberry.widgets.form.field.Field) widgets.getFirst();

            assertEquals("Bio", field.label());
            assertTrue(field.children().getFirst() instanceof TextArea);
        }
    }

    /// The one scrollable thing in the toolkit that is **not** a `scroll`, and
    /// therefore the one place the wheel's convention had to be written out a
    /// second time — and was written out wrong ([ADR-0314]).
    @Nested
    @DisplayName("the wheel")
    class Wheel {

        private TextArea document() {
            var text = new StringBuilder();
            for (var line = 1; line <= 40; line++) {
                text.append("line ").append(line).append('\n');
            }
            return new TextArea(text.toString(), null).fill(true);
        }

        private double scrolledBy(ElementTree tree) {
            return ((TextAreaState) tree.root().state().orElseThrow()).scrolledBy();
        }

        /// Turns the wheel by `notches` over the middle of the control.
        private boolean wheel(ElementTree tree, float notches) {
            var event = PointerEvent.wheel(150, 100, 0, notches, null);
            box(tree).onPointer(event);
            render(tree);
            return event.isConsumed();
        }

        /// **The direction**, which is the whole of the bug: the handler negated
        /// the delta, so a `text-area` beside a `scroll` — which is precisely the
        /// Markdown screen — scrolled the other way.
        @Test
        @DisplayName("turning the wheel down moves the document down, the way every other viewport does")
        void downIsDown() {
            var tree = mounted(document());

            assertTrue(wheel(tree, 1), "there are forty lines in a pane that holds a handful");

            assertTrue(
                    scrolledBy(tree) > 0,
                    "a notch down left the offset at " + scrolledBy(tree) + ", which is up the document");
        }

        /// **Three lines, in the control's own terms.** Asserted by asking what
        /// is now at the top of the pane rather than by multiplying a line height
        /// out here: the distance is in points and what it has to be worth is a
        /// number of *lines*, which is the conversion the bug got wrong in both
        /// directions at once — one pixel, upwards ([ADR-0314]).
        @Test
        @DisplayName("and one notch brings the fourth line to the top, because a notch is three lines")
        void aNotchIsThreeLines() {
            var tree = mounted(document());

            wheel(tree, 1);
            // A triple-click on the first line still on screen, which is the
            // line `WHEEL_LINES` of them down from where it opened.
            tripleClickAtTheTop(tree);

            assertEquals(
                    "line " + (TextAreaBox.WHEEL_LINES + 1), box(tree).edit().selectedText());
        }

        @Test
        @DisplayName("two notches go twice as far, which is what a trackpad's fractions rest on")
        void notchesAreADistance() {
            var one = mounted(document());
            var two = mounted(document());

            wheel(one, 1);
            wheel(two, 2);

            assertEquals(2 * scrolledBy(one), scrolledBy(two), 0.5);
        }

        private void tripleClickAtTheTop(ElementTree tree) {
            var event = new PointerEvent(
                    PointerEvent.Kind.PRESSED,
                    8,
                    8,
                    PointerEvent.Button.PRIMARY,
                    3,
                    Float.NaN,
                    Float.NaN,
                    Modifiers.NONE,
                    null);
            event.localTo(new PointerEvent.Local(8, 8, 300, 200));
            box(tree).onPointer(event);
            render(tree);
        }

        @Test
        @DisplayName("at the top, turning up moves nothing and the page behind keeps the wheel")
        void theTopChains() {
            var tree = mounted(document());

            assertFalse(wheel(tree, -1), "an area already at the top consumed a wheel it could not use");
            assertEquals(0, scrolledBy(tree), 1e-9);
        }

        @Test
        @DisplayName("an area with nothing to scroll never takes the wheel at all")
        void aShortAreaChains() {
            var tree = mounted(new TextArea("one\ntwo", null));

            assertFalse(wheel(tree, 1), "two lines in a pane that holds five has nowhere to go");
        }
    }

    @Nested
    @DisplayName("the pointer")
    class Pointer {

        private void press(ElementTree tree, float x, float y, int clickCount) {
            var event = new PointerEvent(
                    PointerEvent.Kind.PRESSED,
                    x,
                    y,
                    PointerEvent.Button.PRIMARY,
                    clickCount,
                    Float.NaN,
                    Float.NaN,
                    Modifiers.NONE,
                    null);
            event.localTo(new PointerEvent.Local(x, y, 300, 200));
            box(tree).onPointer(event);
            render(tree);
        }

        @Test
        @DisplayName("a press lands on the line it was over")
        void pressPicksALine() {
            var tree = mounted(new TextArea("one\ntwo\nthree", null));

            // 6 points of padding plus a line and a half: the second line.
            press(tree, 8, 6 + 27, 1);

            assertTrue(
                    box(tree).edit().caret() >= 4 && box(tree).edit().caret() <= 7,
                    "landed at " + box(tree).edit().caret() + ", which is not on line two");
        }

        @Test
        @DisplayName("a triple-click selects the line it is on")
        void tripleClickSelectsALine() {
            var tree = mounted(new TextArea("one\ntwo\nthree", null));

            press(tree, 8, 6 + 27, 3);

            // "Select the line" means something here, unlike in a one-line field
            // where it is select-all by another name.
            assertEquals("two", box(tree).edit().selectedText());
        }
    }

    /// `text-align` in a multi-line field — `docs/gaps.md` G30, ADR-0324.
    ///
    /// The area's value box is given a **definite width**, unlike a `text-input`'s,
    /// so the paint indents each line by its own share of the slack and what this
    /// control has to do is agree with it — per line, because two lines of different
    /// lengths do not start in the same place.
    ///
    /// [io.github.digitalsmile.goldberry.widgets.form.textinput.TextInputTest]'s
    /// aligned tests assert the same invariant from the other shape.
    @Nested
    @DisplayName("text-align")
    class Aligned {

        private static final String LINES = "a much longer first line\nshort";

        private String rule;

        private ElementTree centred(String text, String alignment) {
            rule = "text-area { text-align: " + alignment + " }";
            var tree = new ElementTree(new TextArea(text, null), host);
            render(tree, rule);
            box(tree).measured(new Extent(300, 200), new Extent(300, 200));
            render(tree, rule);
            box(tree).onFocusChanged(true, false);
            render(tree, rule);
            return tree;
        }

        private void key(ElementTree tree, Key which) {
            key(tree, which, Modifiers.NONE);
        }

        private void key(ElementTree tree, Key which, Modifiers modifiers) {
            box(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, which, modifiers, false, null));
            render(tree, rule);
            render(tree, rule);
        }

        /// The **document's** start, which is `Ctrl+Home`: plain `Home` is the start
        /// of the *visual line*, and an area mounts with its caret at the end.
        private void toStart(ElementTree tree) {
            key(tree, Key.HOME, Modifiers.of(Mod.CTRL));
        }

        /// Where the caret is drawn, in the content box's coordinates: the line's
        /// own indent, plus how far along the line the caret is.
        private double caretX(ElementTree tree) {
            var area = box(tree).caretArea().orElseThrow();
            return area.left() + box(tree).caretOffsetIn(area);
        }

        private void pressAtCaret(ElementTree tree) {
            var area = box(tree).caretArea().orElseThrow();
            // 8 and 6 are `text-area`'s own padding in `controls.css`, which the
            // press is measured past and the caret's rectangle is not.
            var x = (float) (8 + caretX(tree));
            var y = 6 + area.top() + area.size().height() / 2;
            var event = new PointerEvent(
                    PointerEvent.Kind.PRESSED,
                    x,
                    y,
                    PointerEvent.Button.PRIMARY,
                    1,
                    Float.NaN,
                    Float.NaN,
                    Modifiers.NONE,
                    null);
            event.localTo(new PointerEvent.Local(x, y, 300, 200));
            box(tree).onPointer(event);
            render(tree, rule);
        }

        @Test
        @DisplayName("nothing is indented at the leading edge")
        void startIsUnchanged() {
            var tree = centred(LINES, "start");

            assertEquals(0, box(tree).caretArea().orElseThrow().left(), 0.01);
        }

        /// The case a single "where does the text start" number cannot describe: two
        /// lines of different lengths are indented by different amounts.
        @Test
        @DisplayName("every line is indented by its own share of the slack")
        void indentIsPerLine() {
            var tree = centred(LINES, "center");
            toStart(tree);

            var onFirst = box(tree).caretArea().orElseThrow().left();
            key(tree, Key.DOWN);
            var onSecond = box(tree).caretArea().orElseThrow().left();

            assertTrue(onFirst > 0, () -> "the long line starts at " + onFirst);
            assertTrue(
                    onSecond > onFirst + 10,
                    () -> "the short line starts at " + onSecond + " and the long one at " + onFirst);
        }

        @Test
        @DisplayName("pressing on the caret does not move it, on either line")
        void pressRoundTrips() {
            var tree = centred(LINES, "center");
            toStart(tree);
            for (var i = 0; i < 4; i++) {
                key(tree, Key.RIGHT);
            }

            for (var line = 0; line < 2; line++) {
                var before = box(tree).edit().caret();

                pressAtCaret(tree);

                assertEquals(before, box(tree).edit().caret(), "the press landed somewhere else on line " + line);
                key(tree, Key.DOWN);
            }
        }

        /// `Down` and back keeps the **visual** column, which under `center` is not
        /// the paragraph's: a column carried between two differently indented lines
        /// without their indents is a caret that walks sideways.
        @Test
        @DisplayName("Down then Up comes back to the column it started in")
        void verticalMovementKeepsTheVisualColumn() {
            var tree = centred("a much longer first line\nshort\nanother long line here", "center");
            toStart(tree);
            for (var i = 0; i < 12; i++) {
                key(tree, Key.RIGHT);
            }
            var offset = box(tree).edit().caret();
            var column = caretX(tree);

            key(tree, Key.DOWN);
            key(tree, Key.DOWN);
            key(tree, Key.UP);
            key(tree, Key.UP);

            assertEquals(offset, box(tree).edit().caret(), "the caret did not come back to where the run started");
            assertEquals(column, caretX(tree), 0.5, "and it is in the same column on the screen");
        }
    }
}
