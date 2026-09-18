package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.PreeditEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.form.parts.Underline;

/// `docs/gaps.md` G16, in the control with a second dimension.
///
/// `TextInputPreeditTest` pins the rules; what is different here is the shape of
/// them — a composition can **wrap**, so it is underlined once per visual line,
/// which is the same thing a selection already is and the reason both are bounded
/// runs of parts (ADR-0292).
///
/// There is no masked `text-area`, so the one refusal `text-input` has does not
/// arise.
class TextAreaPreeditTest {

    private final TestHost host = new TestHost();

    private ElementTree mounted(TextArea area) {
        var tree = new ElementTree(area, host);
        render(tree);
        box(tree).measured(new Extent(300, 200), new Extent(300, 200));
        box(tree).onFocusChanged(true, false);
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private TextAreaBox box(ElementTree tree) {
        return (TextAreaBox) tree.root().children().getFirst().widget();
    }

    private String text(ElementTree tree) {
        return ((TextAreaState) tree.root().state().orElseThrow()).heldText();
    }

    private void compose(ElementTree tree, String composition, int start, int length) {
        box(tree).onPreedit(new PreeditEvent(composition, start, length, null));
        render(tree);
    }

    private void compose(ElementTree tree, String composition) {
        compose(tree, composition, -1, -1);
    }

    private void type(ElementTree tree, String committed) {
        box(tree).onText(new TextEvent(committed, null));
        render(tree);
    }

    @Test
    @DisplayName("does not put the composition in the value")
    void doesNotEdit() {
        var tree = mounted(new TextArea("ab", null));

        compose(tree, "にほんご");

        assertEquals("ab", text(tree));
    }

    @Test
    @DisplayName("draws it inside the text, at the caret")
    void splicesItIntoWhatIsDrawn() {
        var tree = mounted(new TextArea("", null));
        type(tree, "ab");

        compose(tree, "XY");

        assertEquals("abXY", box(tree).display());
        assertEquals(2, box(tree).composing().start());
        assertEquals(4, box(tree).composing().end());
        assertEquals(4, box(tree).edit().caret(), "the caret goes to the end of what is being composed");
    }

    @Test
    @DisplayName("survives a newline in the text, because the splice is an offset and not a line")
    void composesOnASecondLine() {
        var tree = mounted(new TextArea("one\ntwo", null));
        box(tree).onFocusChanged(true, false);
        render(tree);
        // The caret is at the end after a value is loaded, which is the second
        // line -- the case that would break an implementation counting lines.
        compose(tree, "にほ");

        assertEquals("one\ntwoにほ", box(tree).display());
        assertEquals("one\ntwo", text(tree));
    }

    @Test
    @DisplayName("the accepted candidate is one edit and clears the composition")
    void commitIsOneEdit() {
        var seen = new java.util.ArrayList<String>();
        var tree = mounted(new TextArea("", seen::add));
        compose(tree, "にほんご");

        type(tree, "日本語");

        assertEquals("日本語", text(tree));
        assertEquals(List.of("日本語"), seen);
        assertFalse(box(tree).composing().isActive());
    }

    @Test
    @DisplayName("an abandoned composition leaves nothing behind")
    void abandonedCompositionClears() {
        var tree = mounted(new TextArea("ab", null));
        compose(tree, "にほんご");

        compose(tree, "");

        assertFalse(box(tree).composing().isActive());
        assertEquals("ab", box(tree).display());
    }

    @Test
    @DisplayName("underlines it, one rectangle per part slot and only while there is one")
    void drawsAnUnderline() {
        var tree = mounted(new TextArea("", null));
        var maxRows = new TextArea("", null).maxRows();

        assertFalse(underline(tree, maxRows).visible());

        compose(tree, "にほ");

        assertTrue(underline(tree, maxRows).visible());
    }

    @Test
    @DisplayName("the highlight draws the converting clause instead of a selection")
    void highlightsTheClause() {
        var tree = mounted(new TextArea("", null));

        compose(tree, "にほんご", 0, 2);

        assertTrue(box(tree).composing().hasClause());
        assertEquals(0, box(tree).composing().clauseStart());
        assertEquals(2, box(tree).composing().clauseEnd());
    }

    /// `text-input`'s bug, in this control's own copy of the same six lines: the
    /// early return compared the composition, the caret and the clause's start
    /// and not its extent, so an input method growing the clause it is converting
    /// — `Shift+Right`, and neither the text nor the start moves — was told
    /// nothing had changed. Driven through the editor seam, because a
    /// `PreeditEvent` derives its caret from the clause's own end and the two
    /// cannot be told apart there.
    @Test
    @DisplayName("growing the clause without moving its start moves the highlight")
    void resizesTheClause() {
        var tree = mounted(new TextArea("", null));
        box(tree).editor().compose("にほんご", 0, 0, 2);
        render(tree);
        assertEquals(2, box(tree).composing().clauseEnd());

        var changed = box(tree).editor().compose("にほんご", 0, 0, 3);
        render(tree);

        assertTrue(changed, "an unchanged answer leaves the event unconsumed");
        assertEquals(0, box(tree).composing().clauseStart());
        assertEquals(3, box(tree).composing().clauseEnd(), "the clause covers the character just taken in");
    }

    @Test
    @DisplayName("a composition hides a selection, because it replaces one when it commits")
    void compositionHidesTheSelection() {
        var tree = mounted(new TextArea("abcd", null));
        box(tree)
                .onKey(new io.github.digitalsmile.goldberry.input.event.KeyEvent(
                        io.github.digitalsmile.goldberry.input.event.KeyEvent.Kind.PRESSED,
                        io.github.digitalsmile.goldberry.input.key.Key.A,
                        io.github.digitalsmile.goldberry.input.key.Modifiers.of(
                                io.github.digitalsmile.goldberry.input.key.Mod.CTRL),
                        false,
                        null));
        render(tree);
        assertTrue(box(tree).edit().hasSelection());

        compose(tree, "にほ");

        // No clause was reported, so nothing is washed at all -- the selection is
        // gone from the drawing even though the edit still carries one.
        assertFalse(highlight(tree).visible());
    }

    @Test
    @DisplayName("a read-only area refuses, on the same terms as an edit")
    void readOnlyRefuses() {
        var tree = mounted(new TextArea("ab", null).readOnly(true));

        var event = new PreeditEvent("にほ", -1, -1, null);
        box(tree).onPreedit(event);
        render(tree);

        assertFalse(event.isConsumed());
        assertFalse(box(tree).composing().isActive());
    }

    @Test
    @DisplayName("reports the caret's line, not the whole control")
    void publishesTheCaretsLine() {
        var tree = mounted(new TextArea("one\ntwo\nthree", null));

        var area = box(tree).caretArea().orElseThrow();

        assertTrue(area.width() > 0);
        assertTrue(area.height() > 0);
        assertTrue(
                area.height() < 100,
                "a candidate window kept clear of the whole control would be pushed a long way from the text");
        assertTrue(area.top() > 0, "the caret is on the third line, which is not at the top of the control");
    }

    @Test
    @DisplayName("an unfocused area has no caret to place one against")
    void noAreaWhenUnfocused() {
        var tree = mounted(new TextArea("ab", null));
        box(tree).onFocusChanged(false, false);
        render(tree);

        assertTrue(box(tree).caretArea().isEmpty());
    }

    private Underline underline(ElementTree tree, int maxRows) {
        return (Underline) box(tree).children().get(maxRows + 2);
    }

    private io.github.digitalsmile.goldberry.widgets.form.parts.Highlight highlight(ElementTree tree) {
        return (io.github.digitalsmile.goldberry.widgets.form.parts.Highlight)
                box(tree).children().getFirst();
    }
}
