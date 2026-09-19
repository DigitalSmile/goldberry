package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;

/// How a menu is **anchored**, which is not the same question as where it ends
/// up.
///
/// [MenusTest] drives the real launcher and asserts the geometry. This asserts
/// the one thing the geometry cannot show: which `Host.popup` overload the menu
/// went through. A menu placed against a rectangle sits in exactly the right
/// place and cannot follow it afterwards, because a rectangle is the answer it
/// already was and a name is a question the next frame can answer again
/// ([ADR-0270], [ADR-0432]).
class MenusAnchorTest {

    private static Menu menu(int items) {
        var rows = new ArrayList<Widget>();
        for (var i = 0; i < items; i++) {
            rows.add(new Item("row " + i, () -> {}));
        }
        return new Menu(List.copyOf(rows), Attributes.NONE);
    }

    /// The whole of entry one. `Menus` used to resolve the id itself —
    /// `host.anchor(id).painted()` — and hand the *rectangle* to the overload
    /// that also takes a minimum width and a `Fit`, because that was the only
    /// overload that took those two. So a menu could have a viewport or it could
    /// follow its heading, and it took the viewport.
    @Test
    @DisplayName("a menu opened against an id is placed by name, not by the rectangle the name resolved to")
    void opensByNameSoItCanFollow() {
        var host = new TestHost().anchoring("file", 10, 20, 60, 24);

        Menus.open(host, "file", menu(3));

        assertEquals(List.of("file"), host.anchoredBy(), "the menu did not go through the by-name overload");
        assertEquals(1, host.opened.size(), "exactly one popup should have been opened");
    }

    /// And it kept the two things it had to give the following up for. The
    /// overload takes an id *and* a floor *and* a `Fit`, which is the only reason
    /// the swap above is possible at all.
    @Test
    @DisplayName("a menu opened by name is still handed to the Fit that gives it a viewport")
    void theFitStillRunsWhenOpeningByName() {
        // Taller than the 200 the test host reports as its placeable area, so
        // `Fitted` has something to wrap.
        var host = new TestHost().anchoring("file", 10, 20, 60, 24).measuring(120, 4000);

        Menus.open(host, "file", menu(60));

        assertEquals(List.of("file"), host.anchoredBy(), "the menu did not go through the by-name overload");
        assertEquals(1, host.opened.size(), "exactly one popup should have been opened");
        assertInstanceOf(
                Scroll.class,
                host.opened.getFirst().content(),
                "a menu taller than the work area should have been wrapped in a viewport");
    }

    /// A menu is as wide as its commands, so the floor it passes is zero — the
    /// half of [ADR-0145] that says a `select` is the only caller that wants one.
    /// Recorded because the new overload made it possible to pass something else
    /// by accident.
    @Test
    @DisplayName("a menu asks for no minimum width of its own")
    void aMenuPassesNoFloor() {
        var host = new TestHost().anchoring("file", 10, 20, 60, 24);

        Menus.open(host, "file", menu(3), Placement.AFTER);

        assertEquals(0, host.opened.getFirst().minimumWidth(), "a menu is as wide as its commands and no wider");
        assertEquals(Placement.AFTER, host.opened.getFirst().placement(), "the placement was not carried through");
    }

    /// Nothing with that id was painted, so there is nowhere to put it — and
    /// nothing is opened rather than something being opened at the origin.
    @Test
    @DisplayName("a menu anchored to an id nothing painted opens nothing")
    void refusesAnUnpaintedAnchor() {
        var host = new TestHost();

        var opened = Menus.open(host, "missing", menu(3));

        assertTrue(opened.isEmpty(), "there was nowhere to put it");
        assertTrue(host.opened.isEmpty(), "and nothing should have been opened anyway");
    }
}
