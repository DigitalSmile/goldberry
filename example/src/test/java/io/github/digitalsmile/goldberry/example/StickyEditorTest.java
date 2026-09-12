package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.example.ui.CanvasScreen;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Canvas;

/// The Canvas screen's sticky, which is the showcase's use of `text.edit.Editor`.
///
/// **Why a test for a demo.** The sticky took arrow keys, moved its caret,
/// extended its selection and inserted newlines — and typing a letter did
/// nothing, because the platform produces no committed text until a widget says
/// it is typed into and this one had not (ADR-0285). Every part of that was
/// working except one `return true`, and no test could tell: the golden image is
/// of an *unfocused* sticky, and the editor's own tests drive it directly.
///
/// So this asserts the wiring rather than the editing: that the canvas asks for
/// the keyboard, and that text handed to it lands in the editor.
class StickyEditorTest {

    private Canvas sticky;

    @BeforeEach
    void buildTheScreen() {
        // The editor shapes text on first use, and shaping is the rasterizer's.
        RendererRequirement.enforce();
        var tree = new ElementTree(new CanvasScreen());
        tree.flush();
        sticky = find(tree.root());
    }

    /// The canvas with `id="sticky"`, found the way a test that is about the
    /// screen's *wiring* should: by walking what was built.
    private static Canvas find(Element root) {
        var found = new ArrayList<Canvas>();
        walk(root, found);
        return found.stream()
                .filter(canvas -> "sticky".equals(canvas.attributes().id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Canvas screen has no canvas with id=\"sticky\""));
    }

    private static void walk(Element element, List<Canvas> found) {
        if (element.widget() instanceof Canvas canvas) {
            found.add(canvas);
        }
        element.children().forEach(child -> walk(child, found));
    }

    @Test
    @DisplayName("the sticky asks the platform for text input")
    void asksForTheKeyboard() {
        // The one line whose absence made the card look broken while every other
        // part of it worked.
        assertTrue(
                sticky.wantsTextInput(),
                "a canvas holding an Editor and not asking for text input receives"
                        + " arrow keys and never a character");
    }

    @Test
    @DisplayName("the other canvases on the screen do not")
    void theOthersDoNot() {
        var tree = new ElementTree(new CanvasScreen());
        tree.flush();
        var others = new ArrayList<Canvas>();
        walk(tree.root(), others);
        others.removeIf(canvas -> "sticky".equals(canvas.attributes().id()));

        assertFalse(others.isEmpty(), "the screen has other canvases to be wrong about");
        others.forEach(canvas -> assertFalse(
                canvas.wantsTextInput(),
                canvas.attributes().id() + " is drawn on rather than typed into, and an on-screen"
                        + " keyboard over it would be a bug"));
    }

    @Test
    @DisplayName("committed text reaches the editor, and a letter is not a key")
    void textLandsInTheEditor() {
        var event = new TextEvent("x", null);
        sticky.onText(event);

        assertTrue(event.isConsumed(), "the editor took the text, which is what consuming it says");
    }

    @Test
    @DisplayName("an arrow moves the caret and a Tab is left alone")
    void keysBehave() {
        var arrow = new KeyEvent(KeyEvent.Kind.PRESSED, Key.LEFT, Modifiers.NONE, false, null);
        sticky.onKey(arrow);
        assertTrue(arrow.isConsumed(), "the caret moved, so the key was taken");

        var tab = new KeyEvent(KeyEvent.Kind.PRESSED, Key.TAB, Modifiers.NONE, false, null);
        sticky.onKey(tab);
        assertFalse(tab.isConsumed(), "Tab still moves focus out of a sticky being edited");
    }
}
