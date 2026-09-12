package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.example.ui.CanvasScreen;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Canvas;

/// The Canvas screen's image card, which is the showcase's use of the clipboard's
/// byte half — ADR-0286.
///
/// [StickyEditorTest]'s reason, again: the card looks right in a golden image
/// whether or not `Ctrl+V` does anything, because the picture is taken of a card
/// nobody has pasted into. So the wiring is asserted here — that a copy puts a
/// PNG on the clipboard, and that a paste takes one off it and draws that
/// instead.
class ImageClipboardCardTest {

    private Clipboard clipboard;
    private Canvas images;

    @BeforeEach
    void buildTheScreen() {
        // Decoding the card's own picture is the rasterizer's.
        RendererRequirement.enforce();
        clipboard = new HeadlessBackend().clipboard();
        var tree = new ElementTree(new CanvasScreen(), new TourTestHost(List.of(), clipboard));
        tree.flush();
        images = find(tree.root());
    }

    private static Canvas find(Element root) {
        var found = new ArrayList<Canvas>();
        walk(root, found);
        return found.stream()
                .filter(canvas -> "images".equals(canvas.attributes().id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Canvas screen has no canvas with id=\"images\""));
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
        images.onKey(event);

        assertTrue(event.isConsumed(), "the copy happened, so the key was taken");
        assertTrue(clipboard.has(Image.PNG_MIME));

        // Read back through the decoder, which shares no code with the encoder:
        // the bytes really are the picture rather than something PNG-shaped.
        var pasted = Image.fromClipboard(clipboard).orElseThrow();
        assertEquals(96, pasted.width(), "the sample this card ships with");
        assertEquals(64, pasted.height());
    }

    @Test
    @DisplayName("Ctrl+V takes an image off the clipboard")
    void pastesAnImage() {
        Image.ofArgb(4, 4, new int[16]).toClipboard(clipboard);

        var event = control(Key.V);
        images.onKey(event);

        assertTrue(event.isConsumed(), "there was an image, so the paste took the key");
    }

    @Test
    @DisplayName("Ctrl+V with nothing to paste leaves the key alone")
    void anEmptyClipboardIsNotAPaste() {
        var event = control(Key.V);
        images.onKey(event);

        // An application may have its own Ctrl+V, and a paste that did not happen
        // must not swallow it.
        assertFalse(event.isConsumed());
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("through the router, which is how a user reaches it")
    class ThroughTheRouter {

        /// Click the card and press Ctrl+C, the way somebody would.
        ///
        /// **The test the first version of this file did not have.** Dispatching
        /// to the widget proves it handles a key; it does not prove the key ever
        /// arrives, which needs a hit region to click, a focusable canvas and a
        /// router that walks the focused chain. Reported as "nothing happens on
        /// Ctrl+C" and reproduced here in one pass (ADR-0286).
        @Test
        @DisplayName("clicking the card focuses it, and Ctrl+C then copies")
        void aClickAndAKey() {
            var host = new TourTestHost(List.of(), clipboard);
            var tree = new ElementTree(new CanvasScreen(), host);
            var sheets = new java.util.ArrayList<io.github.digitalsmile.goldberry.css.Stylesheet>(
                    io.github.digitalsmile.goldberry.widgets.Controls.stylesheets(
                            io.github.digitalsmile.goldberry.css.Theme.NORD_DARK));
            // The application's own sheet, because that is what gives the canvas
            // its height — without it the card lays out zero pixels tall, there is
            // nothing to click, and every assertion below fails for a reason that
            // has nothing to do with the clipboard.
            sheets.add(io.github.digitalsmile.goldberry.css.Stylesheet.resource(
                    io.github.digitalsmile.goldberry.css.cascade.CascadeLayer.APPLICATION,
                    Showcase.class,
                    "showcase.css"));

            try (var fonts = io.github.digitalsmile.goldberry.text.font.Fonts.bundled()) {
                var renderer = new io.github.digitalsmile.goldberry.widget.WidgetRenderer(sheets, fonts);
                var target = io.github.digitalsmile.goldberry.paint.TestFrames.of(1200, 900, 1.0f);
                try (var render = io.github.digitalsmile.goldberry.paint.tree.RenderTree.create()) {
                    var router = new io.github.digitalsmile.goldberry.input.PointerRouter();
                    router.focusRoot(tree.root());
                    renderer.prepare(tree);
                    tree.flush();
                    render.update(target.frame(), renderer.render(tree));
                    var regions = io.github.digitalsmile.goldberry.input.hit.HitTest.capture(render);
                    router.updateRegions(regions);

                    var region = regions.stream()
                            .filter(candidate ->
                                    candidate.owner() instanceof Element owner && "images".equals(owner.id()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("the image card has no hit region to click"));
                    assertTrue(region.height() > 0, "a canvas with no height cannot be clicked: " + region);

                    router.pointerPressed(
                            region.left() + 20,
                            region.top() + 20,
                            io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY,
                            1);
                    router.pointerReleased(
                            region.left() + 20,
                            region.top() + 20,
                            io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY,
                            1);

                    var focused = router.focused();
                    assertTrue(focused != null && "images".equals(focused.id()), "the click focused the card");

                    assertTrue(
                            router.keyPressed(Key.C, new Modifiers(false, true, false, false), false),
                            "Ctrl+C reached the card through the focused chain");
                    assertTrue(clipboard.has(Image.PNG_MIME), "and put a picture on the clipboard");
                } finally {
                    target.end();
                }
            }
        }
    }

    @Test
    @DisplayName("the card is drawn on rather than typed into")
    void doesNotAskForTheKeyboard() {
        // It takes Ctrl+C and Ctrl+V, which are keys. An on-screen keyboard over
        // a picture would be a bug (ADR-0285).
        assertFalse(images.wantsTextInput());
    }
}
