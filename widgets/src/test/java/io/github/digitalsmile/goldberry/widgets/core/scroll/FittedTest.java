package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// [Fitted] — what `menu` and `select` both answer [io.github.digitalsmile.goldberry.Host.Fit]
/// with ([ADR-0179]).
///
/// A pure function of a measurement and a rectangle, which is the point of
/// pulling it out of `Menus`: the decision that used to be an estimate buried in
/// the menu opener is now a value with a threshold anyone can check.
class FittedTest {

    /// 600 tall, so the room is 584 — the margin is kept at **both** ends.
    private static final LogicalRect SCREEN = LogicalRect.of(0, 0, 1920, 600);

    private static final float ROOM = 600 - Fitted.MARGIN * 2;

    private final Fitted fitted = new Fitted("menu-viewport");

    private final Widget content = new Text("whatever is in the popup");

    private Widget fit(float height) {
        return fitted.fit(content, new LogicalSize(200, height), SCREEN);
    }

    /// Which is nearly every popup, and it must cost nothing: a viewport over
    /// content that fits draws a thumb, takes the wheel and adds an element.
    @Test
    @DisplayName("content that fits comes back untouched")
    void fitsIsUntouched() {
        assertSame(content, fit(100));
        assertSame(content, fit(ROOM), "exactly the room available is still a fit");
    }

    @Test
    @DisplayName("content taller than the room becomes a viewport of the room's height")
    void tallerScrolls() {
        var viewport = assertInstanceOf(Scroll.class, fit(ROOM + 1));

        assertEquals(ROOM, viewport.height(), 0.001, "the viewport is not the height of the space it has to fit in");
        assertEquals(ScrollAxis.VERTICAL, viewport.axis());
        assertEquals(
                java.util.List.of(content),
                viewport.children(),
                "the content is inside the viewport rather than replaced by it");
    }

    /// A panel flush against the top and bottom of the screen looks like one that
    /// has been cut off even when it has not — so the margin is real space and is
    /// taken from both ends.
    @Test
    @DisplayName("the margin is kept at both ends, not one")
    void marginIsBothEnds() {
        assertEquals(600 - 2 * Fitted.MARGIN, ROOM, 0.001);
        assertSame(content, fit(ROOM), "content exactly filling the room between the margins is a fit");
        assertInstanceOf(
                Scroll.class, fit(600 - Fitted.MARGIN), "content that would fit with one margin was let through");
    }

    /// Two classes rather than one, so a stylesheet can tell a menu's viewport
    /// from a list's without either inheriting the other's future.
    @Test
    @DisplayName("the viewport carries the class it was made with")
    void carriesItsClass() {
        assertTrue(((Scroll) fit(9999)).attributes().classes().contains("menu-viewport"));
        assertTrue(((Scroll) new Fitted("select-viewport").fit(content, new LogicalSize(200, 9999), SCREEN))
                .attributes()
                .classes()
                .contains("select-viewport"));
    }

    /// The work area is what the popup is placed against, and a short one is an
    /// ordinary state of affairs — a laptop screen with a dock on it.
    @Test
    @DisplayName("a short work area is what decides it, not the display")
    void theWorkAreaDecides() {
        var shallow = LogicalRect.of(0, 0, 1920, 200);

        assertInstanceOf(Scroll.class, fitted.fit(content, new LogicalSize(200, 300), shallow));
        assertSame(content, fitted.fit(content, new LogicalSize(200, 100), shallow));
    }
}
