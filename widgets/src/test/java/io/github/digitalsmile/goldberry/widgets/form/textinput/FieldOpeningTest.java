package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// Which end of a long value a field opens on — [ADR-0412].
///
/// [io.github.digitalsmile.goldberry.text.edit.TextEdit#of] puts the caret at the
/// end of the value, which is where typing goes and is not where reading starts.
/// A field scrolls to keep its caret in view, so a field handed a value wider than
/// itself opened on the **last** characters of it: a reader saw the tail of a URL,
/// of a path, of a name, and had to press `Home` to find out what the value was.
///
/// The caret has not moved — this is about when it is *chased*. Everything below
/// checks the pair: untouched shows the head, and the first press, key, edit or
/// focus puts the old behaviour back for good.
///
/// [ReadOnlyCaretTest] is the other half, from [ADR-0326]: a field nobody can type
/// into has its caret moved instead, because there is no typing to come back to.
class FieldOpeningTest {

    /// Long enough to overflow the 200-point box below, with two ends that are
    /// told apart at a glance.
    private static final String INVITE = "endpointabrq" + "y".repeat(96) + "fiahiyvvqd";

    /// Short enough that the whole value fits, where neither end is hidden and
    /// nothing in this file should make a difference.
    private static final String SHORT = "Jane";

    private final TestHost host = new TestHost();

    private ElementTree mounted(TextInput input) {
        var tree = new ElementTree(input, host);
        render(tree);
        field(tree).measured(new Extent(200, 32), new Extent(200, 32));
        // A second frame, because the first had no measurement to place the text
        // against — the same two-frame bargain every measured control here makes.
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private TextField field(ElementTree tree) {
        return (TextField) tree.root().children().getFirst().widget();
    }

    private TextInputState state(ElementTree tree) {
        return (TextInputState) tree.root().state().orElseThrow();
    }

    @Nested
    @DisplayName("nobody has touched it")
    class Untouched {

        @Test
        @DisplayName("a field holding a long value shows the head of it")
        void showsTheHead() {
            var tree = mounted(new TextInput(INVITE, null));

            assertEquals(0, state(tree).scrolledBy(), 0.01, "the content starts where the content starts");
        }

        /// The caret is where it always was. Moving it would change what typing
        /// does, what an application's `change` handler sees on the next
        /// keystroke, and what four other controls lean on
        /// ([TextInputState#opening]).
        @Test
        @DisplayName("and its caret is still at the end, where typing goes")
        void theCaretDidNotMove() {
            var tree = mounted(new TextInput(INVITE, null));

            assertEquals(INVITE.length(), field(tree).edit().caret());
            assertEquals(INVITE.length(), field(tree).edit().anchor(), "and nothing is selected");
        }

        @Test
        @DisplayName("a value that fits is unaffected, which is most fields")
        void shortValuesAreUnchanged() {
            var tree = mounted(new TextInput(SHORT, null));

            assertEquals(0, state(tree).scrolledBy(), 0.01);
            assertEquals(SHORT.length(), field(tree).edit().caret());
        }

        /// A value the application pushes later is a value that has just arrived to
        /// be read, exactly as the first one was — so it arrives at the head too,
        /// as long as nobody has started working in the field.
        @Test
        @DisplayName("a value the application changes later also opens at the head")
        void aLaterValueAlsoOpensAtTheHead() {
            var value = Property.of("");
            var tree = mounted(TextInput.of(value, null));

            value.set(INVITE);
            render(tree);

            assertEquals(0, state(tree).scrolledBy(), 0.01);
        }
    }

    @Nested
    @DisplayName("somebody touches it")
    class Touched {

        @Test
        @DisplayName("focus chases the caret, and the tail comes back")
        void focusShowsTheTail() {
            var tree = mounted(new TextInput(INVITE, null));
            assertEquals(0, state(tree).scrolledBy(), 0.01);

            field(tree).onFocusChanged(true, false);
            render(tree);

            assertTrue(state(tree).scrolledBy() > 0, "a focused field shows the caret it is about to type at");
        }

        @Test
        @DisplayName("typing chases it, focus or no focus")
        void typingShowsTheTail() {
            var tree = mounted(new TextInput(INVITE, null));

            field(tree).editor().type("!");
            render(tree);

            assertTrue(state(tree).scrolledBy() > 0);
            assertEquals(INVITE + "!", state(tree).heldText());
        }

        @Test
        @DisplayName("a press chases it — the caret the press put there")
        void pressShowsWhereItWasPressed() {
            var tree = mounted(new TextInput(INVITE, null));

            // Far to the right of the box, which is past the end of what is drawn:
            // the press lands on the last visible character and the field follows.
            field(tree).editor().pointerAt(190, false, 1);
            render(tree);

            var caret = field(tree).edit().caret();
            assertTrue(caret > 0 && caret < INVITE.length() / 2, "the press landed in the head, which was under it");
            // Not exactly zero: a caret at the last visible character needs its own
            // width of room, which is a pixel and a bit of `--gb-caret-width`
            // (ADR-0253) rather than the width of the tail.
            assertTrue(
                    state(tree).scrolledBy() < 5,
                    "and the field stayed on the head: " + state(tree).scrolledBy());
        }

        /// Once it matters it keeps mattering. A field that forgot on blur would
        /// jump back to the head the moment the user tabbed on, and a user tabbing
        /// back would see it jump again.
        @Test
        @DisplayName("it stays chased after the focus leaves")
        void itDoesNotForget() {
            var tree = mounted(new TextInput(INVITE, null));

            field(tree).onFocusChanged(true, false);
            render(tree);
            var focused = state(tree).scrolledBy();

            field(tree).onFocusChanged(false, false);
            render(tree);

            assertEquals(focused, state(tree).scrolledBy(), 0.01);
        }

        /// `End` is the key a reader presses to see the tail, and it has to work on
        /// a field nobody has typed into — which is the showcase's own caption:
        /// "press End, then Home, and watch the text move under the caret".
        @Test
        @DisplayName("End shows the tail and Home brings the head back")
        void endAndHome() {
            var tree = mounted(new TextInput(INVITE, null));

            field(tree).editor().move(TextEditor.Motion.END, false, false);
            render(tree);
            assertTrue(state(tree).scrolledBy() > 0, "End is the tail");

            field(tree).editor().move(TextEditor.Motion.START, false, false);
            render(tree);
            assertEquals(0, state(tree).scrolledBy(), 0.01, "and Home is the head again");
        }
    }
}
