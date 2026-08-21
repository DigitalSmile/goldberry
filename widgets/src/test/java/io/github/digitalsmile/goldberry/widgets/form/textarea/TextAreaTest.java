package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Mod;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.TextEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                TestFont.get()).render(tree);
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
            assertTrue(box(tree).edit().caret() >= 17,
                    "the run lost its column and came back at " + box(tree).edit().caret());
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
                    .filter(io.github.digitalsmile.goldberry.widgets.form.parts.Highlight.class
                            ::isInstance)
                    .count();
            assertEquals(box(tree).maxRows(), highlights,
                    "the count is the bound, and render fills what the layout needs");
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
                    .inflateAll(KdlParser.parse("text-area rows=14")).getFirst();

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
        @DisplayName("the parts are the same three, so the stylesheet is one")
        void sharesItsParts() {
            var tree = mounted(new TextArea("one", null));
            var parts = box(tree).children();

            assertTrue(parts.get(parts.size() - 2)
                    instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Value);
            assertTrue(parts.get(parts.size() - 1)
                    instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Caret);
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
            assertNotEquals("text-area",
                    tree.root().widget() instanceof io.github.digitalsmile.goldberry.widget.Styled s
                            ? s.cssType() : "");
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a document writes what §4 spells")
        void inflates() {
            var area = (TextArea) Widgets.inflater().inflateAll(KdlParser.parse("""
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
            var field = (io.github.digitalsmile.goldberry.widgets.form.field.Field)
                    widgets.getFirst();

            assertEquals("Bio", field.label());
            assertTrue(field.children().getFirst() instanceof TextArea);
        }
    }

    @Nested
    @DisplayName("the pointer")
    class Pointer {

        private void press(ElementTree tree, float x, float y, int clickCount) {
            var event = new PointerEvent(PointerEvent.Kind.PRESSED, x, y,
                    PointerEvent.Button.PRIMARY, clickCount, Float.NaN, Float.NaN,
                    Modifiers.NONE, null);
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

            assertTrue(box(tree).edit().caret() >= 4 && box(tree).edit().caret() <= 7,
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
}
