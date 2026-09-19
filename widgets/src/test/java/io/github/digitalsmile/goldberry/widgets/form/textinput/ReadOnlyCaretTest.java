package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// Where a read-only field's caret starts — `docs/gaps.md` G34, [ADR-0326].
///
/// The value under test is a peer invite: 120 characters that exist to be
/// selected and copied onto another machine, in a box a fraction as wide. A
/// field follows its caret, so which end the caret is at decides which end of
/// the value a reader is shown — and for a field nobody can type into, the end
/// was the wrong one.
class ReadOnlyCaretTest {

    /// Long enough to overflow the 200-point box the tests measure with, and
    /// shaped so the two ends are told apart at a glance.
    private static final String INVITE = "endpointabrq" + "y".repeat(96) + "fiahiyvvqd";

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

    @Test
    @DisplayName("a read-only field opens with its caret at the start of the value")
    void readOnlyStartsAtTheHead() {
        var tree = mounted(new TextInput(INVITE, null).readOnly(true));

        assertEquals(0, field(tree).edit().caret());
        assertEquals(0, field(tree).edit().anchor(), "and nothing is selected");
        assertEquals(0, state(tree).scrolledBy(), "so the box shows the head of the value");
    }

    /// **This used to assert the opposite**, and the sentence it asserted was
    /// "an editable field still opens at the end, because that is where you type".
    /// Half of that survives and is still checked here: the caret *is* at the end,
    /// because that is where typing goes and four other controls lean on it. What
    /// went is the scroll that chased it before anybody had asked to type — see
    /// [FieldOpeningTest] and [ADR-0412].
    @Test
    @DisplayName("an editable field keeps its caret at the end and still shows the head")
    void editableKeepsItsCaretAtTheTail() {
        var tree = mounted(new TextInput(INVITE, null));

        assertEquals(INVITE.length(), field(tree).edit().caret());
        assertEquals(0, state(tree).scrolledBy(), 0.01, "but nobody has touched it, so the head is what is shown");

        field(tree).onFocusChanged(true, false);
        render(tree);

        assertTrue(state(tree).scrolledBy() > 0, "and the moment it is focused the box shows the caret");
    }

    @Test
    @DisplayName("a value the application changes later arrives at the head too")
    void aLaterValueAlsoStartsAtTheHead() {
        var value = Property.of("");
        var tree = mounted(TextInput.of(value, null).readOnly(true));

        value.set(INVITE);
        render(tree);

        assertEquals(0, field(tree).edit().caret());
        assertEquals(0, state(tree).scrolledBy());
    }

    @Test
    @DisplayName("it is still selectable and copyable, which is what read-only is for")
    void theWholeValueStillCopies() {
        var tree = mounted(new TextInput(INVITE, null).readOnly(true));

        assertTrue(field(tree).editor().selectAll());
        render(tree);
        assertEquals(INVITE, field(tree).edit().selectedText());
        assertTrue(field(tree).editor().copy());
        assertEquals(INVITE, host.clipboard().text());
    }

    @Test
    @DisplayName("TextEdit.atStart is TextEdit.of's mirror and clamps nothing away")
    void atStartIsOfsMirror() {
        assertEquals(new TextEdit(INVITE, 0, 0), TextEdit.atStart(INVITE));
        assertEquals(new TextEdit("", 0, 0), TextEdit.atStart(""));
        assertEquals(TextEdit.of(INVITE).text(), TextEdit.atStart(INVITE).text());
    }
}
