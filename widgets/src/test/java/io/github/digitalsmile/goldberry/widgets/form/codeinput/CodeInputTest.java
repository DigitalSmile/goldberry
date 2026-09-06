package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
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

/// §4's one-time-code field, driven the way a user drives it.
///
/// The editing rules are [CodeEditTest]'s, testable with no widget at all. What
/// is here is everything that needs one: which key means what, when the model is
/// told, when `complete` fires, what a `mask` draws, and what a document can
/// write.
class CodeInputTest {

    private final TestHost host = new TestHost();

    private ElementTree mounted(CodeInput input) {
        var tree = new ElementTree(input, host);
        render(tree);
        return tree;
    }

    /// Settles whatever `setState` deferred and then describes a frame — the
    /// order the real loop uses, and the reason a test that only rendered would
    /// keep reading the node built before the keystroke.
    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    /// The `code-input` node the widget describes — what a stylesheet and the
    /// router both see, and what takes the keys.
    private CodeField field(ElementTree tree) {
        return (CodeField) tree.root().children().getFirst().widget();
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

    private void focus(ElementTree tree, boolean gained) {
        field(tree).onFocusChanged(gained, true);
        render(tree);
    }

    /// Every box, in order, as it would be drawn.
    private List<String> boxes(ElementTree tree) {
        var drawn = new ArrayList<String>();
        for (var group : field(tree).children()) {
            for (var box : ((CodeGroup) group).boxes()) {
                drawn.add(((CodeBox) box).character());
            }
        }
        return drawn;
    }

    private String held(ElementTree tree) {
        return field(tree).edit().value();
    }

    @Nested
    @DisplayName("the keyboard")
    class Keys {

        @Test
        @DisplayName("typing fills the boxes and advances the ring")
        void typing() {
            var tree = mounted(new CodeInput());
            focus(tree, true);

            type(tree, "1");
            type(tree, "2");

            assertEquals("12", held(tree));
            assertEquals(List.of("1", "2", "", "", "", ""), boxes(tree));
            assertEquals(2, field(tree).edit().caret());
        }

        @Test
        @DisplayName("what the type refuses never reaches the boxes")
        void filtered() {
            var tree = mounted(new CodeInput());
            focus(tree, true);

            type(tree, "a");

            assertEquals("", held(tree));
        }

        @Test
        @DisplayName("Backspace clears the previous box")
        void backspace() {
            var tree = mounted(new CodeInput());
            focus(tree, true);
            type(tree, "12");

            key(tree, Key.BACKSPACE);

            assertEquals("1", held(tree));
        }

        /// Not consumed, so it reaches whatever is behind the field — which is
        /// what makes a `Backspace` in an empty code do the browser-ish thing the
        /// application decides rather than being swallowed by a field with
        /// nothing to delete.
        @Test
        @DisplayName("Backspace on an empty code is not handled")
        void backspaceOnEmpty() {
            var tree = mounted(new CodeInput());
            focus(tree, true);

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.BACKSPACE, Modifiers.NONE, false, null);
            field(tree).onKey(event);

            assertFalse(event.isConsumed());
        }

        @Test
        @DisplayName("Escape clears every box")
        void escape() {
            var tree = mounted(new CodeInput());
            focus(tree, true);
            type(tree, "1234");

            key(tree, Key.ESCAPE);

            assertEquals("", held(tree));
        }

        /// So an empty field's `Escape` still closes the dialog around it.
        @Test
        @DisplayName("Escape on an empty code is not handled, so a dialog still hears it")
        void escapeOnEmpty() {
            var tree = mounted(new CodeInput());
            focus(tree, true);

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            field(tree).onKey(event);

            assertFalse(event.isConsumed());
        }

        /// §4's "a paste of the full code fills every box at once", through the
        /// clipboard rather than through committed text — which is how every
        /// desktop delivers `Ctrl+V`.
        @Test
        @DisplayName("Ctrl+V fills every box from the clipboard, separators and all")
        void paste() {
            var tree = mounted(new CodeInput());
            focus(tree, true);
            host.clipboard().text("123 456");

            key(tree, Key.V, Modifiers.of(Mod.CTRL));

            assertEquals("123456", held(tree));
        }

        /// A code is typed once and read from somewhere else. Offering a copy on
        /// an unmasked field only would make the keys depend on how it is drawn.
        @Test
        @DisplayName("Ctrl+C takes nothing out, masked or not")
        void noCopy() {
            var tree = mounted(new CodeInput());
            focus(tree, true);
            type(tree, "123456");

            key(tree, Key.C, Modifiers.of(Mod.CTRL));

            assertEquals("", host.clipboard().text());
        }

        /// Unlike `text-input`, which owns its arrows because it has a caret they
        /// move. This one has an insertion point that is a function of what is
        /// filled, so the arrows stay with the focus scope around it.
        @Test
        @DisplayName("the arrows are left alone, so a scope around it still navigates")
        void arrowsAreNotConsumed() {
            var tree = mounted(new CodeInput());
            focus(tree, true);
            type(tree, "12");

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE, false, null);
            field(tree).onKey(event);

            assertFalse(event.isConsumed());
            assertEquals("12", held(tree));
        }

        @Test
        @DisplayName("a disabled field takes neither text nor keys")
        void disabled() {
            var tree = mounted(new CodeInput().disabled(true));

            type(tree, "1");
            key(tree, Key.BACKSPACE);

            assertEquals("", held(tree));
            assertFalse(field(tree).isFocusable());
        }
    }

    @Nested
    @DisplayName("what the application is told")
    class Reporting {

        @Test
        @DisplayName("every change is reported with the whole code")
        void change() {
            var seen = new ArrayList<String>();
            var tree = mounted(new CodeInput(6, seen::add));
            focus(tree, true);

            type(tree, "1");
            type(tree, "2");
            key(tree, Key.BACKSPACE);

            assertEquals(List.of("1", "12", "1"), seen);
        }

        /// §4: `complete` "fires when the last box fills, which is what lets a
        /// form submit without a button".
        @Test
        @DisplayName("complete fires on the edit that filled the last box")
        void complete() {
            var completed = new ArrayList<String>();
            var tree = mounted(new CodeInput().onComplete(completed::add));
            focus(tree, true);

            type(tree, "12345");
            assertEquals(List.of(), completed);

            type(tree, "6");
            assertEquals(List.of("123456"), completed);
        }

        /// The flag exists for this: a full field rebuilt, or typed into again,
        /// must not submit the form a second time.
        @Test
        @DisplayName("and not again while it stays full")
        void completeOnce() {
            var completed = new ArrayList<String>();
            var tree = mounted(new CodeInput().onComplete(completed::add));
            focus(tree, true);
            type(tree, "123456");

            type(tree, "7");
            render(tree);

            assertEquals(List.of("123456"), completed);
        }

        /// Two codes, so two completions — which is what a mistyped code
        /// corrected and finished again is.
        @Test
        @DisplayName("a correction and a refill completes a second time")
        void completeAgain() {
            var completed = new ArrayList<String>();
            var tree = mounted(new CodeInput().onComplete(completed::add));
            focus(tree, true);
            type(tree, "123456");

            key(tree, Key.BACKSPACE);
            type(tree, "9");

            assertEquals(List.of("123456", "123459"), completed);
        }

        @Test
        @DisplayName("complete carries the code, and change has already carried it")
        void orderOfEvents() {
            var order = new ArrayList<String>();
            var tree = mounted(new CodeInput(1, value -> order.add("change:" + value))
                    .onComplete(value -> order.add("complete:" + value)));
            focus(tree, true);

            type(tree, "7");

            assertEquals(List.of("change:7", "complete:7"), order);
        }
    }

    @Nested
    @DisplayName("a bound value")
    class Binding {

        @Test
        @DisplayName("starts the field, filtered like a paste")
        void initial() {
            var code = Property.of("12a3");
            var tree = mounted(CodeInput.of(code, null));

            assertEquals("123", held(tree));
        }

        @Test
        @DisplayName("a value the application changes takes the field")
        void override() {
            var code = Property.of("12");
            var tree = mounted(CodeInput.of(code, null));

            code.set("456");
            render(tree);

            assertEquals("456", held(tree));
        }

        /// Without this every `change` handler that wrote back to its model would
        /// undo the user's own keystroke on the next build.
        @Test
        @DisplayName("the echo of the user's own keystroke is ignored")
        void echo() {
            var code = Property.of("");
            var tree = mounted(CodeInput.of(code, code::set));
            focus(tree, true);

            type(tree, "1");
            type(tree, "2");

            assertEquals("12", held(tree));
        }
    }

    @Nested
    @DisplayName("how it is drawn")
    class Drawing {

        @Test
        @DisplayName("a mask draws bullets and never the characters")
        void masked() {
            var tree = mounted(new CodeInput().mask(true));
            focus(tree, true);
            type(tree, "12");

            assertEquals(List.of("•", "•", "", "", "", ""), boxes(tree));
            assertEquals("12", held(tree));
        }

        /// §2's "group gap 16 at the midpoint when `length` is even", which no
        /// selector can express — see [CodeGroup].
        @Test
        @DisplayName("an even length is two groups and an odd one is a single group")
        void groups() {
            assertEquals(2, field(mounted(new CodeInput().length(6))).children().size());
            assertEquals(1, field(mounted(new CodeInput().length(5))).children().size());
        }

        @Test
        @DisplayName("the ring is on the active box, and only while the field has focus")
        void activeBox() {
            var tree = mounted(new CodeInput());
            focus(tree, true);
            type(tree, "12");

            var drawn = new ArrayList<Boolean>();
            for (var group : field(tree).children()) {
                for (var box : ((CodeGroup) group).boxes()) {
                    drawn.add(((CodeBox) box).active());
                }
            }
            assertEquals(List.of(false, false, true, false, false, false), drawn);

            focus(tree, false);
            for (var group : field(tree).children()) {
                for (var box : ((CodeGroup) group).boxes()) {
                    assertFalse(((CodeBox) box).active(), "a field without the keyboard shows no ring");
                }
            }
        }

        @Test
        @DisplayName("a filled box says so, which is what the stronger edge reads")
        void filledClass() {
            var tree = mounted(new CodeInput());
            focus(tree, true);
            type(tree, "1");

            var first = (CodeBox)
                    ((CodeGroup) field(tree).children().getFirst()).boxes().getFirst();
            var second = (CodeBox)
                    ((CodeGroup) field(tree).children().getFirst()).boxes().get(1);

            assertTrue(first.classes().contains("filled"));
            assertFalse(second.classes().contains("filled"));
            assertTrue(second.classes().contains("active"));
        }

        /// §4: "six boxes are a drawing, not six fields, and announcing them
        /// separately would be a lie."
        @Test
        @DisplayName("the field is the one Tab stop and the one node with a name")
        void oneNode() {
            var tree = mounted(new CodeInput());

            assertEquals("code-input", field(tree).cssType());
            assertTrue(field(tree).isFocusable());
            assertNull(field(tree).accessibleName());
        }
    }

    @Nested
    @DisplayName("what a document writes")
    class FromMarkup {

        private CodeInput inflated(String kdl) {
            return (CodeInput)
                    Widgets.inflater().inflateAll(KdlParser.parse(kdl)).getFirst();
        }

        @Test
        @DisplayName("length, type and mask")
        void properties() {
            var input = inflated("code-input length=8 type=\"alnum\" mask=#true");

            assertEquals(8, input.length());
            assertEquals(CodeType.ALNUM, input.type());
            assertTrue(input.mask());
        }

        @Test
        @DisplayName("a bare code-input is six digit boxes")
        void defaults() {
            var input = inflated("code-input");

            assertEquals(6, input.length());
            assertEquals(CodeType.DIGITS, input.type());
            assertFalse(input.mask());
        }

        /// A typo already visible in the markup. A field that refused every
        /// keystroke would be a worse way to find out — `text-input`'s answer to
        /// an unknown `filter=`, unchanged.
        @Test
        @DisplayName("an unknown type is logged and read as digits")
        void unknownType() {
            assertEquals(CodeType.DIGITS, inflated("code-input type=\"hex\"").type());
        }

        @Test
        @DisplayName("a length of zero is a typo, and six is what it meant")
        void zeroLength() {
            assertEquals(6, inflated("code-input length=0").length());
        }
    }
}
