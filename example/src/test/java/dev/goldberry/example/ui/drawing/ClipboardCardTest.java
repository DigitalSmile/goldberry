package dev.goldberry.example.ui.drawing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.ui.sheet.RecordingTestHost;
import dev.goldberry.image.Image;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.canvas.Canvas;

/// The clipboard card, which is the gallery's use of the clipboard's byte half: a
/// clipboard write is an offer.
///
/// The card looks the same in a golden image whether or not `Ctrl+V` does
/// anything, because the picture is of a board nobody has pasted onto. So the
/// wiring is asserted here: a copy puts a PNG on the clipboard, and a paste takes
/// one off it.
class ClipboardCardTest {

    private Clipboard clipboard;

    private Canvas board;

    @BeforeEach
    void buildTheCard() {
        // Decoding the card's own picture is the rasterizer's.
        RendererRequirement.enforce();
        clipboard = new HeadlessBackend().clipboard();
        var tree = new ElementTree(new ClipboardCard(), new RecordingTestHost(clipboard).host());
        tree.flush();
        board = find(tree.root());
    }

    private static Canvas find(Element root) {
        var found = new ArrayList<Canvas>();
        walk(root, found);
        return found.stream()
                .filter(canvas -> ClipboardCard.BOARD.equals(canvas.attributes().id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the clipboard card has no canvas #" + ClipboardCard.BOARD));
    }

    private static void walk(Element element, List<Canvas> found) {
        if (element.widget() instanceof Canvas canvas) {
            found.add(canvas);
        }
        element.children().forEach(child -> walk(child, found));
    }

    private static KeyEvent control(Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, new Modifiers(false, true, false, false), false, null);
    }

    @Test
    @DisplayName("Ctrl+C puts the card's picture on the clipboard as a PNG")
    void copiesAPng() {
        var event = control(Key.C);
        board.onKey(event);

        assertTrue(event.isConsumed(), "the copy happened, so the key was taken");
        assertTrue(clipboard.has(Image.PNG_MIME));

        // Read back through the decoder, which shares no code with the encoder.
        var pasted = Image.fromClipboard(clipboard).orElseThrow();
        assertEquals(96, pasted.width(), "the sample this card ships with");
        assertEquals(64, pasted.height());
    }

    @Test
    @DisplayName("Ctrl+V takes an image off the clipboard and drops it under the pointer")
    void pastesAnImage() {
        Image.ofArgb(4, 4, new int[16]).toClipboard(clipboard);
        board.onPointer(
                new PointerEvent(PointerEvent.Kind.MOVED, 40, 30, null, 0, Float.NaN, Float.NaN, Modifiers.NONE, null));

        var event = control(Key.V);
        board.onKey(event);

        assertTrue(event.isConsumed(), "there was an image, so the paste took the key");
    }

    @Test
    @DisplayName("Ctrl+V with nothing to paste leaves the key alone")
    void anEmptyClipboardIsNotAPaste() {
        var event = control(Key.V);
        board.onKey(event);

        // An application may have its own Ctrl+V, and a paste that did not happen
        // must not swallow it.
        assertFalse(event.isConsumed());
    }

    @Test
    @DisplayName("the board is drawn on rather than typed into")
    void doesNotAskForTheKeyboard() {
        assertFalse(board.wantsTextInput());
    }

    @Nested
    @DisplayName("through the router, which is how a user reaches it")
    class ThroughTheRouter {

        /// Click the board and press Ctrl+C, the way somebody would: a hit region
        /// to click, a focusable canvas, and a router that walks the focused chain.
        @Test
        @DisplayName("clicking the board focuses it, and Ctrl+C then copies")
        void aClickAndAKey() {
            var tree = new ElementTree(new ClipboardCard(), new RecordingTestHost(clipboard).host());
            // The application's sheets, because they give the canvas its height:
            // without them it is zero tall and there is nothing to click.
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
            sheets.addAll(ShowcaseStyles.sheets());

            try (var fonts = Fonts.bundled()) {
                var renderer = new WidgetRenderer(sheets, fonts);
                var target = TestFrames.of(1200, 900, 1.0f);
                try (var render = RenderTree.create()) {
                    var router = new PointerRouter();
                    router.focusRoot(tree.root());
                    renderer.prepare(tree);
                    tree.flush();
                    render.update(target.frame(), renderer.render(tree));
                    var regions = HitTest.capture(render);
                    router.updateRegions(regions);

                    var region = regions.stream()
                            .filter(candidate -> candidate.owner() instanceof Element owner
                                    && ClipboardCard.BOARD.equals(owner.id()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("the board has no hit region to click"));
                    assertTrue(region.height() > 0, "a canvas with no height cannot be clicked: " + region);

                    router.pointerPressed(region.left() + 20, region.top() + 20, PointerEvent.Button.PRIMARY, 1);
                    router.pointerReleased(region.left() + 20, region.top() + 20, PointerEvent.Button.PRIMARY, 1);

                    var focused = router.focused();
                    assertTrue(
                            focused != null && ClipboardCard.BOARD.equals(focused.id()), "the click focused the board");
                    assertTrue(
                            router.keyPressed(Key.C, new Modifiers(false, true, false, false), false),
                            "Ctrl+C reached the board through the focused chain");
                    assertTrue(clipboard.has(Image.PNG_MIME), "and put a picture on the clipboard");
                } finally {
                    target.end();
                }
            }
        }
    }
}
