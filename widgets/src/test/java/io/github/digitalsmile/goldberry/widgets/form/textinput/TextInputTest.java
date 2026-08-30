package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// §4's first field, driven the way a user drives it.
///
/// The editing rules are [TextEditTest]'s and the undo rules are
/// [EditHistoryTest]'s — both testable with no widget at all, which is why they
/// are. What is here is everything that needs the widget: which key means what,
/// what a click does, when the model is told, what a `password` refuses, and the
/// two facilities that had to be built underneath this control before it could
/// exist at all — the clipboard and the platform's text input.
class TextInputTest {

    private final TestHost host = new TestHost();

    /// Mounts `input` and renders it once, so the field has a shaped paragraph
    /// and a measurement — which is what a click needs to land anywhere.
    private ElementTree mounted(TextInput input) {
        var tree = new ElementTree(input, host);
        render(tree);
        field(tree).measured(new Extent(200, 32), new Extent(200, 32));
        return tree;
    }

    /// Settles whatever `setState` deferred and then describes a frame — which is
    /// the order the real loop uses, and the reason a test that only rendered
    /// would keep reading the node built before the keystroke.
    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    /// The `text-input` node the widget describes — what a stylesheet and the
    /// router both see, and what takes the keys.
    private TextField field(ElementTree tree) {
        return (TextField) tree.root().children().getFirst().widget();
    }

    private void type(ElementTree tree, String text) {
        field(tree).onText(new TextEvent(text, null));
        render(tree);
    }

    private void key(ElementTree tree, Key key, Modifiers modifiers) {
        field(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null));
        render(tree);
    }

    private void key(ElementTree tree, Key key) {
        key(tree, key, Modifiers.NONE);
    }

    private void press(ElementTree tree, float x, int clickCount, Modifiers modifiers) {
        var event = new PointerEvent(
                PointerEvent.Kind.PRESSED,
                x,
                0,
                PointerEvent.Button.PRIMARY,
                clickCount,
                Float.NaN,
                Float.NaN,
                modifiers,
                null);
        // The router does this from the painted frame; a test driving the node
        // directly has to say where in the field the press landed.
        event.localTo(new PointerEvent.Local(x, 0, 200, 32));
        field(tree).onPointer(event);
        render(tree);
    }

    private void focus(ElementTree tree, boolean gained, boolean fromKeyboard) {
        field(tree).onFocusChanged(gained, fromKeyboard);
        render(tree);
    }

    /// What the field is holding — the **real** text, which for a masked field is
    /// not what [TextField#edit()] carries: that one is in display offsets over
    /// bullets, because it is what the caret and the highlight are drawn against.
    private String text(ElementTree tree) {
        return ((TextInputState) tree.root().state().orElseThrow()).heldText();
    }

    @Nested
    @DisplayName("typing")
    class Typing {

        @Test
        @DisplayName("committed text goes in at the caret")
        void types() {
            var tree = mounted(new TextInput());

            type(tree, "Gold");
            type(tree, "berry");

            assertEquals("Goldberry", text(tree));
            assertEquals(9, field(tree).edit().caret());
        }

        @Test
        @DisplayName("a field starts from the value it was given, caret at the end")
        void startsFromItsValue() {
            var tree = mounted(new TextInput("Goldberry", null));

            assertEquals("Goldberry", text(tree));
            assertEquals(9, field(tree).edit().caret());
        }

        @Test
        @DisplayName("every change is reported to the application")
        void reports() {
            var reported = new ArrayList<String>();
            var tree = mounted(new TextInput("", reported::add));

            type(tree, "a");
            type(tree, "b");
            key(tree, Key.BACKSPACE);

            assertEquals(List.of("a", "ab", "a"), reported);
        }

        @Test
        @DisplayName("a caret move reports nothing — the text did not change")
        void movingReportsNothing() {
            var reported = new ArrayList<String>();
            var tree = mounted(new TextInput("abc", reported::add));

            key(tree, Key.LEFT);
            key(tree, Key.HOME);

            assertTrue(reported.isEmpty());
        }

        @Test
        @DisplayName("a disabled field takes nothing")
        void disabledTakesNothing() {
            var tree = mounted(new TextInput().disabled(true));

            type(tree, "x");
            key(tree, Key.BACKSPACE);

            assertEquals("", text(tree));
            assertFalse(field(tree).isFocusable(), "and it is out of the tab order");
        }

        @Test
        @DisplayName("a read-only field takes a caret but no edits")
        void readOnlyMovesButDoesNotEdit() {
            var tree = mounted(new TextInput("Goldberry", null).readOnly(true));

            type(tree, "x");
            key(tree, Key.BACKSPACE);
            assertEquals("Goldberry", text(tree));

            key(tree, Key.HOME);
            assertEquals(0, field(tree).edit().caret(), "but it still has a caret");
            assertTrue(field(tree).isFocusable(), "and it is still reachable");
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keys {

        @Test
        @DisplayName("arrows move, and Shift extends")
        void arrows() {
            var tree = mounted(new TextInput("Goldberry", null));

            key(tree, Key.LEFT);
            assertEquals(8, field(tree).edit().caret());

            key(tree, Key.LEFT, Modifiers.of(Mod.SHIFT));
            assertEquals(
                    "r", field(tree).edit().selectedText(), "one Shift+Left from between 'r' and 'y' selects the 'r'");
            assertEquals(8, field(tree).edit().anchor(), "and the anchor stayed put");
        }

        @Test
        @DisplayName("Ctrl+arrow moves by word")
        void wordArrows() {
            var tree = mounted(new TextInput("Yoga laid this out", null));

            key(tree, Key.LEFT, Modifiers.of(Mod.CTRL));

            assertEquals(15, field(tree).edit().caret());
        }

        @Test
        @DisplayName("Home and End reach the ends, and so do Up and Down")
        void homeAndEnd() {
            var tree = mounted(new TextInput("Goldberry", null));

            key(tree, Key.HOME);
            assertEquals(0, field(tree).edit().caret());

            key(tree, Key.END);
            assertEquals(9, field(tree).edit().caret());

            // A single-line field has one line, so Up is Home -- and it has to be
            // taken, or it would walk out of a vertical focus scope from a field
            // somebody is editing.
            key(tree, Key.UP);
            assertEquals(0, field(tree).edit().caret());
        }

        @Test
        @DisplayName("Ctrl+A selects everything")
        void selectAll() {
            var tree = mounted(new TextInput("Goldberry", null));

            key(tree, Key.A, Modifiers.of(Mod.CTRL));

            assertEquals("Goldberry", field(tree).edit().selectedText());
        }

        @Test
        @DisplayName("a field consumes the keys it acts on, and leaves Tab alone")
        void consumes() {
            var tree = mounted(new TextInput("abc", null));

            var left = new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE, false, null);
            field(tree).onKey(left);
            assertTrue(left.isConsumed(), "or Left would walk the focus scope this sits in");

            var tab = new KeyEvent(KeyEvent.Kind.PRESSED, Key.TAB, Modifiers.NONE, false, null);
            field(tree).onKey(tab);
            assertFalse(tab.isConsumed(), "Tab is focus traversal and belongs to the router");

            var enter = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, null);
            field(tree).onKey(enter);
            assertFalse(enter.isConsumed(), "Enter belongs to the form around this");
        }

        @Test
        @DisplayName("undo takes back a typed run, and redo puts it forward")
        void undoAndRedo() {
            var tree = mounted(new TextInput());

            type(tree, "G");
            type(tree, "o");
            type(tree, "l");
            key(tree, Key.Z, Modifiers.of(Mod.CTRL));
            assertEquals("", text(tree), "three keystrokes are one Ctrl+Z");

            key(tree, Key.Z, Modifiers.of(Mod.CTRL, Mod.SHIFT));
            assertEquals("Gol", text(tree));
        }
    }

    @Nested
    @DisplayName("the pointer")
    class Pointer {

        @Test
        @DisplayName("a press puts the caret where it landed")
        void pressPlacesTheCaret() {
            var tree = mounted(new TextInput("Goldberry", null));

            press(tree, 0, 1, Modifiers.NONE);
            assertEquals(0, field(tree).edit().caret(), "a press at the left edge is the start");

            press(tree, 400, 1, Modifiers.NONE);
            assertEquals(9, field(tree).edit().caret(), "and one past the end is the end");
        }

        @Test
        @DisplayName("a double-click selects a word")
        void doubleClickSelectsAWord() {
            var tree = mounted(new TextInput("Yoga laid", null));

            press(tree, 0, 2, Modifiers.NONE);

            assertEquals("Yoga", field(tree).edit().selectedText());
        }

        @Test
        @DisplayName("a triple-click selects the lot")
        void tripleClickSelectsEverything() {
            var tree = mounted(new TextInput("Yoga laid", null));

            press(tree, 0, 3, Modifiers.NONE);

            assertEquals("Yoga laid", field(tree).edit().selectedText());
        }

        @Test
        @DisplayName("a shift-press extends from where the caret was")
        void shiftPressExtends() {
            var tree = mounted(new TextInput("Goldberry", null));
            key(tree, Key.HOME);

            press(tree, 400, 1, Modifiers.of(Mod.SHIFT));

            assertEquals("Goldberry", field(tree).edit().selectedText());
        }
    }

    @Nested
    @DisplayName("the clipboard, which had to be built for this")
    class Clipboard {

        @Test
        @DisplayName("copy puts the selection on the session's clipboard")
        void copies() {
            var tree = mounted(new TextInput("Goldberry", null));
            key(tree, Key.A, Modifiers.of(Mod.CTRL));

            key(tree, Key.C, Modifiers.of(Mod.CTRL));

            assertEquals("Goldberry", host.clipboard().text());
            assertEquals("Goldberry", text(tree), "and copying changes nothing");
        }

        @Test
        @DisplayName("cut copies and then deletes")
        void cuts() {
            var tree = mounted(new TextInput("Goldberry", null));
            key(tree, Key.A, Modifiers.of(Mod.CTRL));

            key(tree, Key.X, Modifiers.of(Mod.CTRL));

            assertEquals("Goldberry", host.clipboard().text());
            assertEquals("", text(tree));
        }

        @Test
        @DisplayName("paste replaces the selection")
        void pastes() {
            host.clipboardText("berry");
            var tree = mounted(new TextInput("Gold!", null));
            key(tree, Key.END);
            key(tree, Key.BACKSPACE);

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            assertEquals("Goldberry", text(tree));
        }

        @Test
        @DisplayName("a pasted newline becomes a space rather than being refused")
        void flattensAPaste() {
            host.clipboardText("Yoga\nlaid");
            var tree = mounted(new TextInput());

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            assertEquals("Yoga laid", text(tree));
        }

        /// The crash this replaced: `Paragraph.of` refused right-to-left text,
        /// nothing between the clipboard and the paint caught it, and a user who
        /// pasted Arabic into a field took the window down with them. The
        /// paragraph approximates it now — the glyphs are shaped and their order
        /// is mirrored — and what this asserts is the half that is not an
        /// opinion: the paste lands, the field keeps it, and the frame is
        /// described ([ADR-0218]).
        @Test
        @DisplayName("pasting right-to-left text keeps it, and does not take the window down")
        void pastesRightToLeftText() {
            host.clipboardText("مرحبا");
            var tree = mounted(new TextInput());

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            assertEquals("مرحبا", text(tree));
            // Again, because the crash was on the *next* frame rather than in the
            // handler: the field held the text and died describing it.
            render(tree);
            assertEquals("مرحبا", text(tree));
        }

        @Test
        @DisplayName("one paste is one undo step")
        void pasteIsOneStep() {
            host.clipboardText("pasted");
            var tree = mounted(new TextInput());

            key(tree, Key.V, Modifiers.of(Mod.CTRL));
            key(tree, Key.Z, Modifiers.of(Mod.CTRL));

            assertEquals("", text(tree));
        }
    }

    @Nested
    @DisplayName("limits")
    class Limits {

        @Test
        @DisplayName("a maximum length refuses the keystroke past it")
        void maxLength() {
            var tree = mounted(new TextInput().maxLength(4));

            type(tree, "Gold");
            type(tree, "b");

            assertEquals("Gold", text(tree));
        }

        @Test
        @DisplayName("a paste is clipped to what fits rather than refused whole")
        void clipsAPaste() {
            host.clipboardText("Goldberry");
            var tree = mounted(new TextInput().maxLength(4));

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            // The alternative -- refuse a paste that is too long -- means a
            // field with a limit silently ignores the paste somebody just made.
            assertEquals("Gold", text(tree));
        }

        @Test
        @DisplayName("typing over a full field's selection works")
        void selectionMakesRoom() {
            var tree = mounted(new TextInput("Gold", null).maxLength(4));
            key(tree, Key.A, Modifiers.of(Mod.CTRL));

            type(tree, "Yoga");

            assertEquals("Yoga", text(tree));
        }

        @Test
        @DisplayName("a filter judges the result, not the keystroke")
        void filters() {
            var tree = mounted(new TextInput().filter(TextFilter.INTEGER));

            type(tree, "-");
            assertEquals("-", text(tree), "a lone minus is what a negative number starts as");
            type(tree, "5");
            assertEquals("-5", text(tree));
            type(tree, "-");
            assertEquals("-5", text(tree), "but a second minus is not an integer");
            type(tree, "x");
            assertEquals("-5", text(tree));
        }

        @Test
        @DisplayName("a rejected keystroke leaves the caret alone")
        void rejectionMovesNothing() {
            var tree = mounted(new TextInput("42", null).filter(TextFilter.DIGITS));
            key(tree, Key.HOME);

            type(tree, "x");

            assertEquals("42", text(tree));
            assertEquals(0, field(tree).edit().caret());
        }
    }

    @Nested
    @DisplayName("a password")
    class Password {

        @Test
        @DisplayName("draws bullets and holds the real text")
        void masks() {
            var tree = mounted(new TextInput("secret", null).password(true));

            assertEquals("••••••", field(tree).display());
            assertEquals("secret", text(tree));
        }

        @Test
        @DisplayName("refuses to copy itself out")
        void refusesToCopy() {
            var tree = mounted(new TextInput("secret", null).password(true));
            key(tree, Key.A, Modifiers.of(Mod.CTRL));

            key(tree, Key.C, Modifiers.of(Mod.CTRL));
            key(tree, Key.X, Modifiers.of(Mod.CTRL));

            assertEquals("", host.clipboard().text(), "§4: no clipboard-out from a password");
            assertEquals("secret", text(tree), "and the cut did not happen either");
        }

        @Test
        @DisplayName("still takes a paste — the ban is one-way")
        void acceptsAPaste() {
            host.clipboardText("hunter2");
            var tree = mounted(new TextInput().password(true));

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            assertEquals("hunter2", text(tree));
        }

        @Test
        @DisplayName("draws one bullet per character the user can see")
        void masksByCodePoint() {
            var tree = mounted(new TextInput("a🎨b", null).password(true));

            assertEquals(
                    "•••", field(tree).display(), "four chars, three characters — a pair must not draw two bullets");
        }

        @Test
        @DisplayName("deletes a whole character through the mask")
        void deletesThroughTheMask() {
            var tree = mounted(new TextInput("a🎨", null).password(true));

            key(tree, Key.BACKSPACE);

            assertEquals("a", text(tree), "half a surrogate pair would be a broken string");
        }
    }

    @Nested
    @DisplayName("focus, and the platform's text input")
    class Focus {

        @Test
        @DisplayName("asks the platform to start delivering text, and to stop")
        void followsFocus() {
            var tree = mounted(new TextInput());

            assertFalse(host.isTextInputActive(), "off until something asks");

            focus(tree, true, true);
            assertTrue(host.isTextInputActive());

            focus(tree, false, false);
            assertFalse(host.isTextInputActive());
        }

        @Test
        @DisplayName("a read-only field does not ask — there is nothing to type into it")
        void readOnlyDoesNotAsk() {
            var tree = mounted(new TextInput("abc", null).readOnly(true));

            focus(tree, true, true);

            assertFalse(host.isTextInputActive(), "or a tablet would raise a keyboard over a field that refuses it");
        }

        @Test
        @DisplayName("focus from the keyboard selects everything, and from a click does not")
        void tabSelectsAll() {
            var byKeyboard = mounted(new TextInput("Goldberry", null));
            focus(byKeyboard, true, true);
            assertEquals("Goldberry", field(byKeyboard).edit().selectedText());

            var byPointer = mounted(new TextInput("Goldberry", null));
            focus(byPointer, true, false);
            assertFalse(field(byPointer).edit().hasSelection(), "the click has already said where the caret goes");
        }

        @Test
        @DisplayName("the caret is drawn only while the field has focus")
        void caretFollowsFocus() {
            var tree = mounted(new TextInput("abc", null));

            assertFalse(field(tree).focused());

            focus(tree, true, false);
            assertTrue(field(tree).focused());
            assertTrue(field(tree).caretShown(), "and it starts solid rather than dark");
        }
    }

    @Nested
    @DisplayName("a value arriving from the model")
    class Binding {

        @Test
        @DisplayName("a bound field starts from the property")
        void startsFromTheBinding() {
            var name = Property.of("Jane");
            var tree = mounted(TextInput.of(name, null));

            assertEquals("Jane", text(tree));
        }

        @Test
        @DisplayName("a different value from outside takes the field")
        void outsideValueWins() {
            var name = Property.of("Jane");
            var tree = mounted(TextInput.of(name, null));

            name.set("Tom");
            render(tree);

            assertEquals("Tom", text(tree));
        }

        @Test
        @DisplayName("the echo of the user's own keystroke does not reset the caret")
        void echoIsIgnored() {
            // The handler writes back to the model, which is what every real form
            // does -- and without the "is this different from what I hold" test
            // the field would take its own text back and put the caret at the end
            // on every letter.
            var name = Property.of("");
            var tree = mounted(TextInput.of(name, name::set));

            type(tree, "Gold");
            key(tree, Key.HOME);
            type(tree, "!");

            assertEquals("!Gold", text(tree));
            assertEquals(1, field(tree).edit().caret());
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        private TextInput inflate(String markup) {
            return (TextInput)
                    Widgets.inflater().inflateAll(KdlParser.parse(markup)).getFirst();
        }

        @Test
        @DisplayName("a document writes what §4 spells")
        void inflates() {
            var it = inflate("""
                    text-input value="Jane" placeholder="Jane Doe" max-length=64
                    """);

            assertEquals("Jane", it.value());
            assertEquals("Jane Doe", it.placeholder());
            assertEquals(64, it.maxLength());
        }

        @Test
        @DisplayName("password, read-only and disabled are flags")
        void flags() {
            var it = inflate("""
                    text-input password=#true read-only=#true disabled=#true
                    """);

            assertTrue(it.password());
            assertTrue(it.readOnly());
            assertTrue(it.disabled());
        }

        @Test
        @DisplayName("a named filter is looked up, and an unknown one accepts everything")
        void filters() {
            assertSame(
                    TextFilter.DIGITS, inflate("text-input filter=\"digits\"").filter());

            // Logged rather than thrown: a typo already visible in the markup,
            // and a field that refused every keystroke is a worse way to learn
            // about it.
            assertSame(
                    TextFilter.NONE, inflate("text-input filter=\"nonsense\"").filter());
        }

        @Test
        @DisplayName("no maximum length written is no limit")
        void unlimitedByDefault() {
            assertEquals(TextInput.UNLIMITED, inflate("text-input").maxLength());
            assertEquals(
                    TextInput.UNLIMITED,
                    inflate("text-input max-length=0").maxLength(),
                    "a field that can hold nothing is not what anybody wrote on purpose");
        }
    }

    @Nested
    @DisplayName("the placeholder")
    class Placeholder {

        @Test
        @DisplayName("stands in for an empty field, and says that it is standing in")
        void showsWhenEmpty() {
            var tree = mounted(new TextInput().placeholder("Jane Doe"));

            assertEquals("Jane Doe", field(tree).display());
            assertTrue(field(tree).placeholder());
            // The class is how the stylesheet tells the two apart: §3 wants
            // `--gb-text-muted` here and `--gb-text` for a real value, and §8's
            // subset has no pseudo-class that means "standing in for content".
            assertTrue(((io.github.digitalsmile.goldberry.widgets.form.parts.Value)
                            field(tree).children().get(1))
                    .classes()
                    .contains("placeholder"));
        }

        @Test
        @DisplayName("goes as soon as there is anything to show")
        void goesWhenTyped() {
            var tree = mounted(new TextInput().placeholder("Jane Doe"));

            type(tree, "J");

            assertEquals("J", field(tree).display());
            assertFalse(field(tree).placeholder());
        }

        @Test
        @DisplayName("is not drawn instead of an empty *masked* field's bullets")
        void maskedEmptyStillShowsIt() {
            // An empty password field has no bullets to draw, so the placeholder
            // is right -- and this is the case where "empty" has to be asked of
            // the text rather than of the display.
            var tree = mounted(new TextInput().password(true).placeholder("Password"));

            assertEquals("Password", field(tree).display());
            assertTrue(field(tree).placeholder());
        }
    }

    @Nested
    @DisplayName("scrolling, for text wider than the box")
    class Scrolling {

        /// A field 60 points wide, which is narrower than the text below.
        private ElementTree narrow(String text) {
            var tree = new ElementTree(new TextInput(text, null), host);
            render(tree);
            field(tree).measured(new Extent(60, 32), new Extent(60, 32));
            render(tree);
            return tree;
        }

        @Test
        @DisplayName("a caret at the end brings the end into view")
        void scrollsToTheCaret() {
            var tree = narrow("Yoga laid this out, HarfBuzz shaped it");

            // A press at the field's right edge must land near the *end* of the
            // text rather than a few characters in, which is what it would if
            // the content had not moved under the caret.
            press(tree, 55, 1, Modifiers.NONE);

            assertTrue(
                    field(tree).edit().caret() > 20,
                    "the field did not scroll: a press at its right edge landed at "
                            + field(tree).edit().caret());
        }

        @Test
        @DisplayName("Home brings the start back")
        void scrollsBack() {
            var tree = narrow("Yoga laid this out, HarfBuzz shaped it");

            key(tree, Key.HOME);
            press(tree, 2, 1, Modifiers.NONE);

            assertEquals(0, field(tree).edit().caret());
        }

        @Test
        @DisplayName("a press is measured past the padding, not from the border")
        void allowsForPadding() {
            // The bug the Forms screen's first golden showed, from the other
            // side: the children are placed against the border box while the clip
            // is the padding box, so everything the field draws and everything it
            // measures has to carry the padding. Without it a press at the left
            // edge of the *text* lands one character in.
            var tree = mounted(new TextInput("Goldberry", null));

            // 8 points is this field's left padding, so this is the very start of
            // the text rather than the very start of the border.
            press(tree, 8, 1, Modifiers.NONE);

            assertEquals(0, field(tree).edit().caret());
        }
    }

    @Nested
    @DisplayName("dragging")
    class Dragging {

        /// A press, then a move with the button still down — which is what the
        /// router sends: `PointerRouter.pointerMoved` builds a `MOVED` event
        /// carrying the press origin, and **no button at all**, because a motion
        /// is not a button event.
        private void dragTo(ElementTree tree, float from, float to) {
            press(tree, from, 1, Modifiers.NONE);
            var moved = new PointerEvent(PointerEvent.Kind.MOVED, to, 0, null, 0, from, 0, Modifiers.NONE, null);
            moved.localTo(new PointerEvent.Local(to, 0, 200, 32));
            field(tree).onPointer(moved);
            render(tree);
        }

        @Test
        @DisplayName("press, hold and move selects what the pointer crossed")
        void dragSelects() {
            var tree = mounted(new TextInput("Goldberry", null));

            dragTo(tree, 8, 400);

            assertTrue(field(tree).edit().hasSelection(), "a drag selected nothing: the field never saw the motion");
            assertEquals("Goldberry", field(tree).edit().selectedText());
        }

        @Test
        @DisplayName("a drag keeps its anchor where the press was")
        void dragKeepsTheAnchor() {
            var tree = mounted(new TextInput("Goldberry", null));

            dragTo(tree, 400, 8);

            // Dragged right to left, so the anchor is at the end and the caret at
            // the start -- which is what lets Shift+Right shrink it afterwards.
            assertEquals(9, field(tree).edit().anchor());
            assertEquals(0, field(tree).edit().caret());
        }

        @Test
        @DisplayName("a hover with no button down selects nothing")
        void hoverDoesNotSelect() {
            var tree = mounted(new TextInput("Goldberry", null));
            key(tree, Key.HOME);

            // `dragX()` is NaN when no button is down, which is the router
            // reporting "no gesture" through the arithmetic (ADR-0075). A field
            // that read the position anyway would move the caret on hover.
            var hover = new PointerEvent(
                    PointerEvent.Kind.MOVED, 400, 0, null, 0, Float.NaN, Float.NaN, Modifiers.NONE, null);
            hover.localTo(new PointerEvent.Local(400, 0, 200, 32));
            field(tree).onPointer(hover);
            render(tree);

            assertEquals(0, field(tree).edit().caret());
            assertFalse(field(tree).edit().hasSelection());
        }
    }

    @Nested
    @DisplayName("where the parts are drawn")
    class Geometry {

        /// The boxes the field describes, rendered by hand — the only way to see
        /// where a caret actually goes, since its position is a measurement
        /// rather than anything a stylesheet or a layout decides.
        private List<Box> parts(ElementTree tree) {
            var context = TestFont.context();
            // The **real** resolved style, so the padding this asserts against is
            // the one `controls.css` actually gives a field rather than a number
            // repeated here.
            var element = tree.root().children().getFirst();
            var style = io.github.digitalsmile.goldberry.css.ComputedStyle.of(
                    new io.github.digitalsmile.goldberry.css.cascade.StyleResolver(
                                    Controls.stylesheets(Theme.NORD_DARK))
                            .resolve(element),
                    io.github.digitalsmile.goldberry.css.value.CssLength.Context.DEFAULT);
            var field = field(tree);
            var children = field.children().stream()
                    .map(child -> ((io.github.digitalsmile.goldberry.widget.style.Paints) child)
                            .render(style, List.of(), context))
                    .toList();
            return field.render(style, children, context).children();
        }

        private static float points(io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength length) {
            return length instanceof io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength.Points p
                    ? p.value()
                    : Float.NaN;
        }

        @Test
        @DisplayName("the caret is a line tall, not a control tall")
        void caretIsALineTall() {
            var tree = mounted(new TextInput("Goldberry", null));
            focus(tree, true, false);
            key(tree, Key.END);

            var caret = parts(tree).get(2);

            // A 32-point control holds an 18-point line. A caret filling the
            // control would be nearly twice the height of the text it sits in,
            // which reads as a terminal cursor rather than an insertion point.
            assertEquals(TestFont.one().lineHeight(), points(caret.height()), 0.01);
            assertTrue(points(caret.height()) < 24, "the caret is as tall as the whole field");
        }

        @Test
        @DisplayName("the highlight is a line tall too, so it sits behind the glyphs")
        void selectionIsALineTall() {
            var tree = mounted(new TextInput("Goldberry", null));
            focus(tree, true, false);
            key(tree, Key.A, Modifiers.of(Mod.CTRL));

            var selection = parts(tree).getFirst();

            assertEquals(TestFont.one().lineHeight(), points(selection.height()), 0.01);
        }

        @Test
        @DisplayName("every part's left carries the field's padding")
        void partsAllowForPadding() {
            var tree = mounted(new TextInput("Goldberry", null));
            focus(tree, true, false);
            key(tree, Key.HOME);

            var parts = parts(tree);

            // The bug the Forms screen's first golden showed: an absolutely
            // positioned child here is placed against the border box while the
            // clip is the padding box, so a `left` of zero draws the first
            // character under the padding and loses it.
            assertEquals(8, points(parts.get(1).inset().left()), 0.01, "the text");
            assertEquals(8, points(parts.get(2).inset().left()), 0.01, "the caret at offset 0");
        }
    }

    @Nested
    @DisplayName("the node a stylesheet sees")
    class Styling {

        @Test
        @DisplayName("is `text-input`, once")
        void oneNode() {
            var tree = mounted(new TextInput());
            var root = tree.root();

            // The stateful widget styles nothing, or every rule would apply
            // twice -- `scroll`'s and `tabs`' arrangement.
            assertEquals("text-input", field(tree).cssType());
            assertNotEquals(
                    "text-input",
                    root.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                            ? styled.cssType()
                            : "");
        }

        @Test
        @DisplayName("carries the document's id and classes")
        void carriesAttributes() {
            var input = new TextInput()
                    .withAttributes(io.github.digitalsmile.goldberry.widget.attr.Attributes.of(
                            KdlParser.parse("text-input id=\"name\" class=\"wide\"")
                                    .getFirst()));
            var tree = mounted(input);

            assertEquals("name", field(tree).id());
            assertTrue(field(tree).classes().contains("wide"));
        }

        @Test
        @DisplayName("has the three parts, in paint order")
        void hasItsParts() {
            var tree = mounted(new TextInput("abc", null));
            var parts = field(tree).children();

            // The highlight first, so it is behind the glyphs: §1.2 wants
            // selected text readable, and a wash over a glyph dims it.
            assertTrue(parts.get(0) instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Highlight);
            assertTrue(parts.get(1) instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Value);
            assertTrue(parts.get(2) instanceof io.github.digitalsmile.goldberry.widgets.form.parts.Caret);
        }
    }

    /// §4's autocomplete: "`text-input autocomplete=#true` attaches a `popover` of
    /// suggestions to the field: the widget raises the query, the application
    /// supplies the list, and the field's text is never rewritten without the
    /// user choosing" ([ADR-0182]).
    @Nested
    @DisplayName("suggesting")
    class Suggesting {

        private final List<String> reported = new java.util.ArrayList<>();

        /// Mounts, focuses and **locates** the field, which is what a window
        /// does after it paints: a popover is anchored to a rectangle only the
        /// painted frame knows ([ADR-0119]), and a widget test has no router to
        /// report one.
        private ElementTree offering(TextInput input) {
            var tree = mounted(input);
            focus(tree, true, false);
            TextInputTest.this
                    .field(tree)
                    .located(
                            io.github.digitalsmile.goldberry.render.model.LogicalRect.of(10, 20, 200, 32),
                            io.github.digitalsmile.goldberry.render.model.LogicalRect.of(0, 0, 800, 600));
            render(tree);
            return tree;
        }

        private io.github.digitalsmile.goldberry.widgets.controls.select.SelectList offered() {
            return (io.github.digitalsmile.goldberry.widgets.controls.select.SelectList)
                    host.opened.getFirst().content();
        }

        private TextInput field(String value, String... options) {
            var offered = java.util.Arrays.stream(options)
                    .map(o -> new io.github.digitalsmile.goldberry.widgets.controls.option.Option(o))
                    .toList();
            return new TextInput(value, reported::add).suggesting(offered);
        }

        /// The whole channel: what was typed goes up through `change`, and the
        /// application answers by handing back a list. Nothing in the widget
        /// decides what "matches" means, which is what makes a remote-backed
        /// autocomplete the same widget with a slower model.
        @Test
        @DisplayName("typing reports the query, and the suggestions come back as a rebuild")
        void theQueryGoesUp() {
            var tree = mounted(new TextInput("", reported::add));

            focus(tree, true, false);
            type(tree, "L");
            type(tree, "o");

            assertEquals(
                    List.of("L", "Lo"), reported, "the field did not raise what was typed, keystroke by keystroke");
        }

        /// A list under a field nobody is typing in is a panel floating over the
        /// application for no reason, and it would take the next click.
        @Test
        @DisplayName("nothing is offered until the field has the keyboard")
        void onlyWhileFocused() {
            var unfocused = mounted(field("", "London", "Lisbon"));
            TextInputTest.this
                    .field(unfocused)
                    .located(
                            io.github.digitalsmile.goldberry.render.model.LogicalRect.of(10, 20, 200, 32),
                            io.github.digitalsmile.goldberry.render.model.LogicalRect.of(0, 0, 800, 600));
            render(unfocused);

            assertTrue(host.opened.isEmpty(), "a panel opened over an unfocused field");

            offering(field("", "London", "Lisbon"));

            assertEquals(1, host.opened.size(), "nothing was offered to a focused field");
        }

        @Test
        @DisplayName("the panel is the same list a select opens, one row per suggestion")
        void theRowsAreTheSuggestions() {
            offering(field("", "London", "Lisbon"));

            var list = offered();
            assertEquals(2, list.children().size());
            assertEquals(
                    List.of("London", "Lisbon"),
                    list.children().stream()
                            .map(io.github.digitalsmile.goldberry.widgets.controls.option.Option.class::cast)
                            .map(io.github.digitalsmile.goldberry.widgets.controls.option.Option::value)
                            .toList());
        }

        /// §4's own sentence, and it falls out of the shape rather than being
        /// enforced: choosing reports, and what happens next is the
        /// application's. A handler that ignores it leaves the field as typed.
        @Test
        @DisplayName("choosing a suggestion reports it and rewrites nothing itself")
        void choosingReports() {
            var tree = offering(field("Lo", "London"));

            var row = (io.github.digitalsmile.goldberry.widgets.controls.option.Option)
                    offered().children().getFirst();
            row.onSelect().run();

            assertEquals(List.of("London"), reported);
            assertEquals("Lo", text(tree), "the field rewrote itself, which is the one thing §4 forbids");
        }

        /// Arrows move the focus and `Enter` commits — `Option.inAList()` — so a
        /// user arrowing through suggestions never has the field rewritten under
        /// them. The alternative, follow-the-focus, is a `select`'s and is wrong
        /// here for exactly that reason.
        @Test
        @DisplayName("the rows commit on Enter rather than on arrival")
        void arrowsDoNotChoose() {
            offering(field("", "London", "Lisbon"));

            var row = (io.github.digitalsmile.goldberry.widgets.controls.option.Option)
                    offered().children().getFirst();

            assertFalse(row.roving(), "the suggestions would rewrite the field as the keyboard passed over them");
        }

        @Test
        @DisplayName("an ordinary field offers nothing and opens nothing")
        void anOrdinaryFieldIsUnchanged() {
            var tree = offering(new TextInput("hello", reported::add));

            assertEquals(List.of(), widget(tree).suggestions());
            assertTrue(host.opened.isEmpty());
        }

        private TextInput widget(ElementTree tree) {
            return (TextInput) tree.root().widget();
        }
    }
}
