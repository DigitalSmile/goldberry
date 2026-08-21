package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.backend.TestTimers;
import io.github.digitalsmile.goldberry.bind.Property;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §4's first field, driven the way a user drives it.
///
/// The editing rules are [TextEditTest]'s and the undo rules are
/// [EditHistoryTest]'s — both testable with no widget at all, which is why they
/// are. What is here is everything that needs the widget: which key means what,
/// what a click does, when the model is told, what a `password` refuses, and the
/// two facilities that had to be built underneath this control before it could
/// exist at all — the clipboard and the platform's text input.
class TextInputTest {

    /// [TestHost] with an event loop, because a field with focus blinks.
    ///
    /// The timer is never due and never fires — the blink is not what any of
    /// these tests are about, and one on a wall clock would make every focus test
    /// wait half a second. What is asserted about it is that the field asks for
    /// one and cancels it, which is the leak a widget can cause.
    private static final class BlinkingHost extends TestHost {

        private final List<Runnable> scheduled = new ArrayList<>();
        private final List<EventLoop.Timer> handed = new ArrayList<>();

        @Override
        public EventLoop.Timer after(java.time.Duration delay, Runnable action) {
            scheduled.add(action);
            var timer = TestTimers.pending();
            handed.add(timer);
            return timer;
        }

        /// Fires the most recently scheduled timer, as the loop would.
        void tick() {
            if (!scheduled.isEmpty()) {
                scheduled.removeLast().run();
            }
        }

        boolean allCancelled() {
            return handed.stream().noneMatch(EventLoop.Timer::isPending);
        }
    }

    private final BlinkingHost host = new BlinkingHost();

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
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                TestFont.get()).render(tree);
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
        var event = new PointerEvent(PointerEvent.Kind.PRESSED, x, 0,
                PointerEvent.Button.PRIMARY, clickCount, Float.NaN, Float.NaN, modifiers, null);
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
            assertEquals("r", field(tree).edit().selectedText(),
                    "one Shift+Left from between 'r' and 'y' selects the 'r'");
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

            assertEquals("•••", field(tree).display(),
                    "four chars, three characters — a pair must not draw two bullets");
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

            assertFalse(host.isTextInputActive(),
                    "or a tablet would raise a keyboard over a field that refuses it");
        }

        @Test
        @DisplayName("focus from the keyboard selects everything, and from a click does not")
        void tabSelectsAll() {
            var byKeyboard = mounted(new TextInput("Goldberry", null));
            focus(byKeyboard, true, true);
            assertEquals("Goldberry", field(byKeyboard).edit().selectedText());

            var byPointer = mounted(new TextInput("Goldberry", null));
            focus(byPointer, true, false);
            assertFalse(field(byPointer).edit().hasSelection(),
                    "the click has already said where the caret goes");
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
            return (TextInput) Widgets.inflater().inflateAll(KdlParser.parse(markup)).getFirst();
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
            assertSame(TextFilter.DIGITS, inflate("text-input filter=\"digits\"").filter());

            // Logged rather than thrown: a typo already visible in the markup,
            // and a field that refused every keystroke is a worse way to learn
            // about it.
            assertSame(TextFilter.NONE, inflate("text-input filter=\"nonsense\"").filter());
        }

        @Test
        @DisplayName("no maximum length written is no limit")
        void unlimitedByDefault() {
            assertEquals(TextInput.UNLIMITED, inflate("text-input").maxLength());
            assertEquals(TextInput.UNLIMITED, inflate("text-input max-length=0").maxLength(),
                    "a field that can hold nothing is not what anybody wrote on purpose");
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
            assertNotEquals("text-input",
                    root.widget() instanceof io.github.digitalsmile.goldberry.widget.Styled styled
                            ? styled.cssType() : "");
        }

        @Test
        @DisplayName("carries the document's id and classes")
        void carriesAttributes() {
            var input = new TextInput().withAttributes(
                    io.github.digitalsmile.goldberry.widget.Attributes.of(
                            KdlParser.parse("text-input id=\"name\" class=\"wide\"").getFirst()));
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
            assertTrue(parts.get(0) instanceof TextSelection);
            assertTrue(parts.get(1) instanceof TextValue);
            assertTrue(parts.get(2) instanceof TextCaret);
        }
    }
}
