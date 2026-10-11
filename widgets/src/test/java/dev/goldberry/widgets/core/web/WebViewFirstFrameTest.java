package dev.goldberry.widgets.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.render.web.WebLoad;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.panel.Described;
import dev.goldberry.widgets.shell.web.WebPage;

/// A `web-view` on the screen that was just opened, in a window with nothing
/// else to draw.
///
/// The box the page goes over is read from the **last** frame, so the first
/// frame never finds it. Nothing else asks for a second frame in an idle
/// window, and the page opened only when the pointer moved or the screen was
/// visited again. What is asserted is that the widget asks for the frame that
/// finds its box itself, and stops asking after a few.
@DisplayName("a web-view on its first frame")
class WebViewFirstFrameTest {

    private static final String ID = "page";

    private TestHost host;
    private ElementTree tree;

    @BeforeEach
    void mount() {
        RendererRequirement.enforce();
        // Nothing anchored yet: no frame has painted the box.
        host = new TestHost().webViewAvailable(true);
        host.embeddedPagesLoad(WebLoad.FINISHED);
        tree = new ElementTree(
                new WebView(WebPage.of("https://example.org/")).withAttributes(Attributes.NONE.id(ID)), host);
        tree.flush();
    }

    @AfterEach
    void unmount() {
        if (tree != null) {
            tree.unmount();
        }
    }

    private void paint() {
        var painter = Described.first(tree, Canvas.class).painter();
        assertNotNull(painter);
        var buffer = PixelBuffer.allocate(new PhysicalSize(16, 16), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = Frame.over(buffer, DisplayScale.ONE);
        try {
            painter.paint(frame, new LogicalSize(640, 400));
        } finally {
            frame.end();
        }
    }

    private void nextTurn() {
        host.tickAll();
        tree.flush();
    }

    @Test
    @DisplayName("a frame that finds no box asks for the next one, and the next one opens the page")
    void asksForTheFrameThatFindsTheBox() {
        paint();

        assertNull(host.lastEmbeddedPage(), "there was no box to open a page over yet");
        assertTrue(host.hasPendingTimer(), "nothing asked for the frame that would find the box");

        host.anchoring(ID, 24, 80, 640, 400);
        nextTurn();
        paint();

        assertNotNull(host.lastEmbeddedPage(), "the second frame found the box and opened the page");
    }

    @Test
    @DisplayName("a box that never arrives is asked about a few times, not for ever")
    void stopsAsking() {
        var asked = 0;
        for (var frame = 0; frame < WebView.WebViewState.BOX_WAITS + 4; frame++) {
            paint();
            if (host.hasPendingTimer()) {
                asked++;
            }
            nextTurn();
        }

        assertEquals(WebView.WebViewState.BOX_WAITS, asked);
        assertFalse(host.hasPendingTimer(), "an idle window is left idle");
    }
}
