package dev.goldberry.widgets.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
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

/// How a `web-view` asks for the next look at its page, ADR-0491.
///
/// Everything the widget does after the first frame (opening the page,
/// asking whether it has loaded, bringing it back from where it waited) runs
/// in its canvas's painter. A frame calls a painter only where the frame is
/// damaged, and in a window that repaints in part, which is every window
/// presenting through the GPU, a bare `Host.repaint` damages nothing. The page
/// then stayed parked off the side of the window for good.
///
/// So each look is asked for as a **rebuild**: a new painter, which the damage
/// pass compares by identity and so cannot mistake for last frame's box. That
/// is what is asserted: a zero-delay rebuild is scheduled, and it changes the
/// painter.
@DisplayName("a web-view looks at its page again")
class WebViewPollingTest {

    private static final String ID = "page";

    private TestHost host;
    private ElementTree tree;

    @BeforeEach
    void mount() {
        RendererRequirement.enforce();
        host = new TestHost().webViewAvailable(true).anchoring(ID, 24, 80, 640, 400);
        tree = new ElementTree(
                new WebView(WebPage.of("https://example.org/")).withAttributes(Attributes.NONE.id(ID)), host);
        tree.flush();
    }

    @AfterEach
    void unmount() {
        // Null when `mount` aborted for want of the library: JUnit runs the
        // `@AfterEach` methods of an aborted test all the same.
        if (tree != null) {
            tree.unmount();
        }
    }

    /// The painter the canvas holds now.
    private Painter painter() {
        var painter = Described.first(tree, Canvas.class).painter();
        assertNotNull(painter, "a web-view's canvas always paints: it is how the widget hears each frame");
        return painter;
    }

    /// Runs the painter as a frame that reached the canvas would.
    private void paint(Painter painter) {
        var buffer = PixelBuffer.allocate(new PhysicalSize(16, 16), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = Frame.over(buffer, DisplayScale.ONE);
        try {
            painter.paint(frame, new LogicalSize(640, 400));
        } finally {
            frame.end();
        }
    }

    /// Fires what the widget scheduled and rebuilds, as the next turn of the
    /// loop would.
    private void nextTurn() {
        host.tickAll();
        tree.flush();
    }

    @Test
    @DisplayName("opening the page asks for a rebuild, so the frame after it reaches the painter")
    void afterOpening() {
        host.embeddedPagesLoad(WebLoad.LOADING);
        var first = painter();

        paint(first);

        assertNotNull(host.lastEmbeddedPage(), "the first painted frame opens the page");
        assertTrue(host.scheduledDelays().contains(Duration.ZERO), "nothing asked for the frame after opening");
        nextTurn();
        assertNotSame(first, painter(), "a rebuild that kept the painter damages nothing");
    }

    @Test
    @DisplayName("while the page loads, every look asks for the next one, and a loaded page stops asking")
    void whileLoading() {
        host.embeddedPagesLoad(WebLoad.LOADING);
        paint(painter());
        nextTurn();
        var page = host.lastEmbeddedPage();
        assertNotNull(page);

        for (var frame = 0; frame < 3; frame++) {
            var before = painter();
            paint(before);
            assertTrue(host.hasPendingTimer(), "a loading page is looked at again on frame " + frame);
            nextTurn();
            assertNotSame(before, painter(), "frame " + frame + " rebuilt nothing a partial repaint would see");
        }

        page.loading(WebLoad.FINISHED);
        paint(painter());
        // Taking the spinner down is one more rebuild, and the last.
        nextTurn();
        var settled = painter();
        paint(settled);
        assertFalse(host.hasPendingTimer(), "a loaded page is still being polled, so the loop never goes idle");
        tree.flush();
        assertSame(settled, painter(), "nothing rebuilds once the page has shown");
    }

    @Test
    @DisplayName("a loaded page is placed over its box, not left parked")
    void placedOnceLoaded() {
        host.embeddedPagesLoad(WebLoad.LOADING);
        paint(painter());
        nextTurn();
        var page = host.lastEmbeddedPage();
        assertNotNull(page);

        page.loading(WebLoad.FINISHED);
        paint(painter());

        assertEquals("bounds(24,80,640,400)", page.calls().getLast(), "the page was never brought back from parking");
    }
}
