package io.github.digitalsmile.goldberry.widgets.form.textinput;

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

/// `docs/gaps.md` G16: a `text-input` being typed into by an input method.
///
/// The claim under all of it is the one ADR-0289 made for a canvas and ADR-0292
/// carries into the catalog's own fields — **a composition is not an edit**. Every
/// test here is a way for that to be false: the value changing, the model being
/// told, the undo history growing, a ghost left after `Escape`.
///
/// The `password` case is the one decision in G16 that was not mechanical, and it
/// has its own test: a candidate window is a second, unmasked window showing what
/// is being typed, so a masked field refuses to compose exactly as Windows's and
/// macOS's secure fields do.
class TextInputPreeditTest {

    private final TestHost host = new TestHost();

    private ElementTree mounted(TextInput input) {
        var tree = new ElementTree(input, host);
        render(tree);
        field(tree).measured(new Extent(200, 32), new Extent(200, 32));
        field(tree).onFocusChanged(true, false);
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

    private String text(ElementTree tree) {
        return ((TextInputState) tree.root().state().orElseThrow()).heldText();
    }

    private void compose(ElementTree tree, String composition, int start, int length) {
        field(tree).onPreedit(new PreeditEvent(composition, start, length, null));
        render(tree);
    }

    private void compose(ElementTree tree, String composition) {
        compose(tree, composition, -1, -1);
    }

    private void type(ElementTree tree, String committed) {
        field(tree).onText(new TextEvent(committed, null));
        render(tree);
    }

    @Test
    @DisplayName("does not put the composition in the value")
    void doesNotEdit() {
        var tree = mounted(new TextInput());
        type(tree, "ab");

        compose(tree, "にほんご");

        assertEquals("ab", text(tree), "a composition the user has not accepted is not in the field's value");
    }

    @Test
    @DisplayName("draws it inside the text, so the characters after it move along")
    void splicesItIntoWhatIsDrawn() {
        var tree = mounted(new TextInput());
        type(tree, "ab");
        field(tree).editor().move(TextEditor.Motion.LEFT, false, false);
        render(tree);

        compose(tree, "XY");

        assertEquals("aXYb", field(tree).display());
        assertTrue(field(tree).composing().isActive());
        assertEquals(1, field(tree).composing().start());
        assertEquals(3, field(tree).composing().end());
    }

    @Test
    @DisplayName("puts the caret inside the composition, where the input method has it")
    void caretIsInsideTheComposition() {
        var tree = mounted(new TextInput());

        compose(tree, "XY", 2, 0);

        assertEquals(2, field(tree).edit().caret(), "a caret before the composition would sit behind the text");
    }

    @Test
    @DisplayName("never tells the model, however long the composition gets")
    void neverReportsACompositionx() {
        var seen = new java.util.ArrayList<String>();
        var tree = mounted(new TextInput("", seen::add));

        compose(tree, "に");
        compose(tree, "にほ");
        compose(tree, "にほん");

        assertEquals(List.of(), seen, "three keystrokes of composing is nothing an application should hear about");
    }

    @Test
    @DisplayName("the accepted candidate is one edit, and the composition goes with it")
    void commitIsOneEdit() {
        var seen = new java.util.ArrayList<String>();
        var tree = mounted(new TextInput("", seen::add));
        compose(tree, "にほんご");

        type(tree, "日本語");

        assertEquals("日本語", text(tree));
        assertEquals(List.of("日本語"), seen, "one change, for the conversion rather than for each keystroke");
        assertFalse(field(tree).composing().isActive(), "an accepted candidate must not also stay underlined");
        assertEquals("日本語", field(tree).display());
    }

    @Test
    @DisplayName("an abandoned composition leaves nothing behind")
    void abandonedCompositionClears() {
        var tree = mounted(new TextInput());
        type(tree, "ab");
        compose(tree, "にほんご");

        compose(tree, "");

        assertFalse(field(tree).composing().isActive());
        assertEquals("ab", field(tree).display(), "a ghost left on screen is what forgetting this looks like");
    }

    @Test
    @DisplayName("losing focus ends the composition, because the empty event goes elsewhere")
    void blurEndsTheComposition() {
        var tree = mounted(new TextInput());
        compose(tree, "にほ");

        field(tree).onFocusChanged(false, false);
        render(tree);

        assertFalse(field(tree).composing().isActive());
        assertEquals("", field(tree).display());
    }

    @Test
    @DisplayName("an underline covers the composition, and only while there is one")
    void drawsAnUnderline() {
        var tree = mounted(new TextInput());

        assertFalse(underline(tree).visible(), "nothing is being composed, so there is nothing to underline");

        compose(tree, "にほ");

        assertTrue(underline(tree).visible());
    }

    @Test
    @DisplayName("the highlight draws the converting clause, because there is no selection to draw")
    void highlightsTheClause() {
        var tree = mounted(new TextInput());

        compose(tree, "にほんご", 0, 2);

        assertTrue(field(tree).composing().hasClause());
        assertEquals(0, field(tree).composing().clauseStart());
        assertEquals(2, field(tree).composing().clauseEnd());
        assertTrue(highlight(tree).visible());
    }

    @Test
    @DisplayName("no clause reported is a composition underlined and not highlighted")
    void noClauseNoHighlight() {
        var tree = mounted(new TextInput());

        compose(tree, "にほんご");

        assertFalse(field(tree).composing().hasClause());
        assertFalse(highlight(tree).visible());
        assertTrue(underline(tree).visible());
    }

    @Test
    @DisplayName("a placeholder gives way to a composition, because a field being composed into is not empty")
    void compositionHidesThePlaceholder() {
        var tree = mounted(new TextInput().placeholder("Search"));
        assertTrue(field(tree).placeholder());

        compose(tree, "にほ");

        assertFalse(field(tree).placeholder());
        assertEquals("にほ", field(tree).display());
    }

    @Test
    @DisplayName("a password refuses to compose, so the candidate window cannot show it")
    void passwordRefuses() {
        var tree = mounted(new TextInput().password(true));
        type(tree, "hunter2");

        var event = new PreeditEvent("にほんご", -1, -1, null);
        field(tree).onPreedit(event);
        render(tree);

        assertFalse(event.isConsumed(), "an unconsumed event is how a field says it is not composing");
        assertFalse(field(tree).composing().isActive());
        assertEquals("•••••••", field(tree).display(), "still bullets, and not one character of the composition");
    }

    @Test
    @DisplayName("a read-only field refuses too, on the same terms as an edit")
    void readOnlyRefuses() {
        var tree = mounted(new TextInput().readOnly(true));

        var event = new PreeditEvent("にほ", -1, -1, null);
        field(tree).onPreedit(event);
        render(tree);

        assertFalse(event.isConsumed());
        assertFalse(field(tree).composing().isActive());
    }

    @Test
    @DisplayName("reports where the line is, so the platform can place a candidate window")
    void publishesTheCaretArea() {
        var tree = mounted(new TextInput());
        type(tree, "abc");

        var area = field(tree).caretArea().orElseThrow();

        assertTrue(area.width() > 0, "a zero-width area lets a candidate window sit over the text");
        assertTrue(area.height() > 0);
        assertTrue(field(tree).caretOffsetIn(area) > 0, "the caret is three characters in, not at the left edge");
    }

    @Test
    @DisplayName("an unfocused field has no caret to place one against")
    void noAreaWhenUnfocused() {
        var tree = mounted(new TextInput());
        field(tree).onFocusChanged(false, false);
        render(tree);

        assertTrue(field(tree).caretArea().isEmpty());
    }

    private Underline underline(ElementTree tree) {
        var parts = field(tree).children();
        return (Underline) parts.get(parts.size() - 1);
    }

    private io.github.digitalsmile.goldberry.widgets.form.parts.Highlight highlight(ElementTree tree) {
        return (io.github.digitalsmile.goldberry.widgets.form.parts.Highlight)
                field(tree).children().getFirst();
    }
}
