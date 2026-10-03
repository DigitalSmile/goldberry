package dev.goldberry.example.ui.drawing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widgets.core.canvas.Canvas;

/// The sticky note, which is the gallery's use of `text.edit.Editor` on a canvas.
///
/// The golden image is of an unfocused sticky and the editor's own tests drive it
/// directly, so neither can tell whether typing reaches it. The sticky once took
/// arrow keys and never a character, because the canvas had not said it was
/// typed into. This asserts the wiring: that the canvas asks for the keyboard,
/// and that text handed to it lands in the editor.
class StickyCardTest {

    private Canvas sticky;

    @BeforeEach
    void buildTheCard() {
        // The editor shapes text on first use, and shaping is the rasterizer's.
        RendererRequirement.enforce();
        var tree = new ElementTree(new StickyCard());
        tree.flush();
        sticky = canvases(tree.root()).stream()
                .filter(canvas -> "sticky".equals(canvas.attributes().id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the sticky card has no canvas with id=\"sticky\""));
    }

    private static List<Canvas> canvases(Element root) {
        var found = new ArrayList<Canvas>();
        walk(root, found);
        return found;
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
        assertTrue(
                sticky.wantsTextInput(),
                "a canvas holding an Editor and not asking for text input receives arrow keys and never a character");
    }

    @Test
    @DisplayName("the other canvases on the screen do not")
    void theOthersDoNot() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 900)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(scene.root("drawing"))) {
            var others = canvases(session.byId("screen-drawing").orElseThrow());
            others.removeIf(canvas -> "sticky".equals(canvas.attributes().id()));

            assertFalse(others.isEmpty(), "the screen has other canvases to be wrong about");
            others.forEach(canvas -> assertFalse(
                    canvas.wantsTextInput(),
                    canvas.attributes().id() + " is drawn on rather than typed into, and an on-screen keyboard"
                            + " over it would be a bug"));
        }
    }

    @Test
    @DisplayName("committed text reaches the editor")
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
