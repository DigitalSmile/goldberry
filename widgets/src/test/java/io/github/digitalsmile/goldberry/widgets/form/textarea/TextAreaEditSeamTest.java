package io.github.digitalsmile.goldberry.widgets.form.textarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.text.edit.TextEdit;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// `onEdit` and `edit(TextEdit)` — `docs/gaps.md` G38, [ADR-0332].
///
/// The seam an editor needs and a form does not: `change=` reports a `String`,
/// and `Ctrl+B` around a selection needs to know where the selection **is**. The
/// last test is the whole argument — three of the four things a shortcut cares
/// about change no text at all, so the caret cannot be inferred from two versions
/// of a string.
class TextAreaEditSeamTest {

    private final TestHost host = new TestHost();

    /// What the control reported, in order — the `TextEdit`s and the `String`s
    /// kept apart, because the point is that the two are independent.
    private final List<TextEdit> edits = new ArrayList<>();

    private final List<String> changes = new ArrayList<>();

    /// The widget the tree is rebuilt from, so a test can push an edit back the
    /// way an application does: change the description and rebuild.
    private TextArea current;

    private ElementTree tree;

    private ElementTree mounted(TextArea area) {
        current = area;
        tree = new ElementTree(area, host);
        render();
        box().measured(new Extent(300, 200), new Extent(300, 200));
        render();
        return tree;
    }

    private void render() {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    /// What an application does after computing an edit: describe the control
    /// again, carrying it.
    private void push(TextEdit next) {
        current = current.edit(next);
        tree.update(current);
        render();
    }

    private TextAreaBox box() {
        return (TextAreaBox) tree.root().children().getFirst().widget();
    }

    private String held() {
        return ((TextAreaState) tree.root().state().orElseThrow()).heldText();
    }

    private void key(Key key, Modifiers modifiers) {
        box().onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null));
        render();
    }

    private void type(String text) {
        box().onText(new TextEvent(text, null));
        render();
    }

    private TextArea listening(String value) {
        return new TextArea(value, changes::add).onEdit(edits::add);
    }

    @Test
    @DisplayName("typing reports the text and the caret, and reports both")
    void typingReportsBoth() {
        mounted(listening(""));

        type("hi");

        assertEquals(List.of("hi"), changes);
        assertEquals(1, edits.size());
        assertEquals("hi", edits.getFirst().text());
        assertEquals(2, edits.getFirst().caret());
    }

    /// **The argument for the whole entry.** A caret move changes no text, so
    /// `change=` says nothing — and a shortcut that wrapped a selection would have
    /// no idea where to wrap.
    @Test
    @DisplayName("a caret move reports an edit and no change")
    void aCaretMoveIsAnEditAndNotAChange() {
        mounted(listening("hello"));
        type("!");
        changes.clear();
        edits.clear();

        key(Key.LEFT, Modifiers.NONE);

        assertTrue(changes.isEmpty(), "no text changed, so a form has nothing to hear");
        assertEquals(1, edits.size());
        assertEquals(5, edits.getFirst().caret());
        assertFalse(edits.getFirst().hasSelection());
    }

    @Test
    @DisplayName("a selection reports where it starts and where it ends")
    void aSelectionIsReported() {
        mounted(listening("hello"));
        type("");
        edits.clear();

        key(Key.A, Modifiers.of(Mod.CTRL));

        var last = edits.getLast();
        assertTrue(last.hasSelection());
        assertEquals(0, last.start());
        assertEquals(5, last.end());
        assertEquals("hello", last.selectedText());
    }

    @Test
    @DisplayName("an area with no onEdit behaves exactly as it always did")
    void theListenerIsOptional() {
        mounted(new TextArea("", changes::add));

        type("hi");

        assertEquals(List.of("hi"), changes);
        assertTrue(edits.isEmpty());
    }

    @Test
    @DisplayName("an edit pushed in is adopted, caret and all")
    void aPushedEditIsAdopted() {
        mounted(listening("hello"));

        // What `Ctrl+B` around a whole word computes: new text, and a caret that
        // is neither at the splice's end nor where the widget would have put it.
        push(new TextEdit("**hello**", 2, 7));

        assertEquals("**hello**", held());
        assertEquals(2, box().edit().anchor());
        assertEquals(7, box().edit().caret());
        assertEquals("hello", box().edit().selectedText());
    }

    @Test
    @DisplayName("the same edit carried by every rebuild is adopted once")
    void aConstantEditDoesNotFight() {
        mounted(listening("hello"));
        push(new TextEdit("hello", 0, 0));

        type("X");

        // The caret moved on from where the application put it, and the rebuild
        // the keystroke caused did not drag it back.
        assertEquals("Xhello", held());
        assertEquals(1, box().edit().caret());
    }

    @Test
    @DisplayName("a pushed edit is not echoed back through onEdit")
    void pushingIsNotReported() {
        mounted(listening("hello"));
        edits.clear();
        changes.clear();

        push(new TextEdit("**hello**", 2, 7));

        assertTrue(edits.isEmpty(), "the application already knows what it pushed");
        assertTrue(changes.isEmpty(), "and a report raised during a build is a rebuild inside a rebuild");
    }

    @Test
    @DisplayName("Ctrl+Z undoes an edit the application pushed")
    void aPushedEditIsUndoable() {
        mounted(listening("hello"));
        push(new TextEdit("**hello**", 9, 9));

        key(Key.Z, Modifiers.of(Mod.CTRL));

        assertEquals("hello", held());
    }

    @Test
    @DisplayName("an area built with an edit opens at that caret")
    void anAreaCanOpenAtACaret() {
        mounted(new TextArea("hello", null).edit(new TextEdit("hello", 0, 0)));

        assertEquals(0, box().edit().caret());
    }
}
