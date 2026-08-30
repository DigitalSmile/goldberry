package io.github.digitalsmile.goldberry.widgets.controls.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.GoldberryTestAccess;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode;

/// §3's `select` family, driven through the **real launcher and the real frame
/// loop** — which is what `MenusTest` does for menus and what nothing did for
/// these.
///
/// ## Why this exists
///
/// Six defects reached a running application while every unit test passed
/// (ADR-0182 through ADR-0187): a row that stayed grey, a field that took one
/// keystroke, a tree that would not open, a panel that took the keyboard, one
/// that then took no clicks, and a popup that would not grow. Every one of them
/// lives in a seam a hand-driven test cannot reach — the application's rebuild,
/// the platform's window flags, the pointer, the popup's own build schedule.
///
/// So these post **events** at a **window** and read what the backend was
/// actually asked for. Nothing here calls a handler directly; if it can be
/// asserted by calling `onChange` it belongs in `SelectTest` instead.
class SelectLoopTest {

    private HeadlessBackend backend;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryTestAccess.shutdown();
    }

    /// The application under test: one select, at a known id, in a column.
    private static final class TestApp implements Application {

        private final Widget content;
        private final Consumer<Host> onStart;

        TestApp(Widget content, Consumer<Host> onStart) {
            this.content = content;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Column(List.of(content), Attributes.NONE.id("page"));
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(420, 320);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(
                    Controls.baseStylesheet(),
                    Theme.NORD_DARK.load(),
                    Stylesheet.parse(
                            io.github.digitalsmile.goldberry.css.cascade.CascadeLayer.APPLICATION,
                            "#page { padding: 24px }"));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private List<HeadlessPopup> popups() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .toList();
    }

    private HeadlessWindow main() {
        return backend.windows().stream()
                .filter(w -> !(w instanceof HeadlessPopup))
                .map(HeadlessWindow.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private void click(HeadlessWindow window, float x, float y) {
        backend.post(new BackendEvent.PointerMoved(window, x, y, 0));
        backend.post(new BackendEvent.PointerPressed(window, x, y, 1, 1, 0));
        backend.post(new BackendEvent.PointerReleased(window, x, y, 1, 1, 0));
    }

    private static void later(long millis, Runnable action) {
        Goldberry.async(() -> {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                })
                .thenRun(action);
    }

    /// The reported defect, as a test: "the autocomplete popup starts near the
    /// middle of the text field".
    ///
    /// A popup is placed **below its anchor**, and the anchor is the rectangle
    /// the last frame painted the field as. So the popup's top must be at or
    /// below the field's bottom — which is the one assertion that tells a
    /// placement bug from an anchor bug, because it is stated in the window's own
    /// coordinates and reads the position the backend was actually given.
    @Test
    @Timeout(20)
    @DisplayName("a combobox's list opens below the field, not inside it")
    void openedBelowTheField() {
        var anchor = new LogicalRect[1];
        var top = new float[1];

        Goldberry.launch(new TestApp(
                new Select("", value -> {}, new Option("london", "London"))
                        .autocomplete(query -> {})
                        .placeholder("Pick a city")
                        .withAttributes(Attributes.NONE.id("city")),
                host -> later(200, () -> {
                    anchor[0] = host.anchor("city").orElseThrow().bounds();
                    // A click on the field, which focuses the editor and opens.
                    click(main(), anchor[0].left() + 20, anchor[0].top() + 8);
                    later(300, () -> {
                        top[0] = popups().isEmpty()
                                ? Float.NaN
                                : popups().getFirst().offset().y();
                        Goldberry.stop();
                    });
                })));

        assertFalse(Float.isNaN(top[0]), "no list opened at all");
        var bottom = anchor[0].top() + anchor[0].size().height();
        assertTrue(
                top[0] >= bottom - 0.5f,
                "the list opened at y=" + top[0] + ", and the field runs from "
                        + anchor[0].top() + " to " + bottom
                        + " — so it is drawn over the control it belongs to");
    }

    /// The same placement, from a window that is **not at the origin** and a
    /// display whose work area is **not the whole screen**.
    ///
    /// Every other test here runs the case that hides a coordinate-space bug: a
    /// window at (0,0) with a work area at (0,0) makes screen coordinates and
    /// window coordinates identical, so mixing them is invisible. A real desktop
    /// never looks like that — there is a taskbar and the window has been moved.
    ///
    /// `placeableArea` converts the work area into the window's own space by
    /// subtracting the window's origin, and `Placement` clamps the popup into it.
    /// If those two disagreed the popup would be pushed back up over the control
    /// it belongs to, which is what "it overlaps the text edit" would look like
    /// ([ADR-0189]).
    @Test
    @Timeout(20)
    @DisplayName("and still below it from a moved window on a display with a taskbar")
    void openedBelowFromAMovedWindow() {
        var anchor = new LogicalRect[1];
        var top = new float[1];
        // A taskbar 48 tall at the top of a 1600×900 display.
        backend.workArea(LogicalRect.of(0, 48, 1600, 852));

        Goldberry.launch(new TestApp(
                new Select("", value -> {}, new Option("london", "London"))
                        .autocomplete(query -> {})
                        .placeholder("Pick a city")
                        .withAttributes(Attributes.NONE.id("city")),
                host -> later(200, () -> {
                    main().moveTo(new io.github.digitalsmile.goldberry.render.model.LogicalPoint(220, 160));
                    anchor[0] = host.anchor("city").orElseThrow().bounds();
                    click(main(), anchor[0].left() + 20, anchor[0].top() + 8);
                    later(300, () -> {
                        top[0] = popups().isEmpty()
                                ? Float.NaN
                                : popups().getFirst().offset().y();
                        Goldberry.stop();
                    });
                })));

        assertFalse(Float.isNaN(top[0]), "no list opened at all");
        var bottom = anchor[0].top() + anchor[0].size().height();
        assertTrue(
                top[0] >= bottom - 0.5f,
                "the list opened at y=" + top[0] + " in the window's coordinates, and the field"
                        + " runs from " + anchor[0].top() + " to " + bottom
                        + " — the anchor and the placeable area are in different spaces");
    }

    /// ADR-0186's defect: a field that took one character and went dead, because
    /// the popup's **window** took the keyboard.
    ///
    /// Two characters posted at the main window, and both must arrive. The second
    /// is sent after the list is open, which is when the platform would have moved
    /// the keyboard.
    ///
    /// **What this cannot prove**, checked by reverting the fix and watching it
    /// still pass: the headless backend has no window flags, so a popup here never
    /// takes platform focus whatever kind it is. This covers the *router* half —
    /// that nothing inside the popup grabs the focus — and the `NOT_FOCUSABLE`
    /// flag that ADR-0187 turned on is beyond anything in this repository to
    /// exercise. Stated rather than left implied, because a test that looks like
    /// it guards something it does not is worse than no test.
    @Test
    @Timeout(20)
    @DisplayName("the list does not take the keyboard off the field it hangs off")
    void theKeyboardStaysOnTheField() {
        var typed = new ArrayList<String>();

        Goldberry.launch(new TestApp(
                new Select("", value -> {}, new Option("london", "London"), new Option("lisbon", "Lisbon"))
                        .autocomplete(typed::add)
                        .placeholder("Pick a city")
                        .withAttributes(Attributes.NONE.id("city")),
                host -> later(200, () -> {
                    var field = host.anchor("city").orElseThrow().bounds();
                    click(main(), field.left() + 20, field.top() + 8);
                    later(250, () -> {
                        backend.post(new BackendEvent.TextInput(main(), "L"));
                        later(150, () -> {
                            // The list is open now. This is the keystroke that
                            // used to go to the popup's window instead.
                            backend.post(new BackendEvent.TextInput(main(), "o"));
                            later(250, Goldberry::stop);
                        });
                    });
                })));

        assertEquals(List.of("L", "Lo"), typed, "the second keystroke went somewhere other than the field");
    }

    /// ADR-0187's defect: an `ATTACHED` popup must take the **pointer** even
    /// though it refuses the keyboard — which is exactly what borrowing the
    /// tooltip flag broke.
    @Test
    @Timeout(20)
    @DisplayName("a value can be picked from the list with the mouse")
    void pickedWithTheMouse() {
        var chosen = new ArrayList<String>();

        Goldberry.launch(new TestApp(
                new Select("", chosen::add, new Option("london", "London"))
                        .autocomplete(query -> {})
                        .placeholder("Pick a city")
                        .withAttributes(Attributes.NONE.id("city")),
                host -> later(200, () -> {
                    var field = host.anchor("city").orElseThrow().bounds();
                    click(main(), field.left() + 20, field.top() + 8);
                    later(300, () -> {
                        if (!popups().isEmpty()) {
                            // The first row, inside the popup's own window.
                            click((HeadlessWindow) popups().getFirst(), 40, 16);
                        }
                        later(250, Goldberry::stop);
                    });
                })));

        assertEquals(List.of("london"), chosen, "the panel refused the pointer, or the row reported nothing");
    }

    /// ADR-0187's other defect: a `tree` expanding a branch is a `setState` in
    /// the **popup's own** tree, which never reaches the widget that opened it —
    /// so the window has to grow from inside `paint`.
    @Test
    @Timeout(20)
    @DisplayName("a tree's popup grows when a branch is expanded")
    void thePopupGrowsWithTheTree() {
        var before = new float[1];
        var after = new float[1];

        Goldberry.launch(new TestApp(
                new Select("", value -> {})
                        .tree(List.of(TreeNode.of(
                                "europe",
                                "Europe",
                                TreeNode.leaf("no", "Norway"),
                                TreeNode.leaf("se", "Sweden"),
                                TreeNode.leaf("dk", "Denmark"))))
                        .placeholder("Choose a region")
                        .withAttributes(Attributes.NONE.id("region")),
                host -> later(200, () -> {
                    var field = host.anchor("region").orElseThrow().bounds();
                    click(main(), field.left() + 20, field.top() + 8);
                    later(300, () -> {
                        before[0] = popups().isEmpty()
                                ? Float.NaN
                                : popups().getFirst().size().height();
                        if (!popups().isEmpty()) {
                            // The first row is the branch; clicking it opens it,
                            // because in a leaf-only tree it is not an answer.
                            click((HeadlessWindow) popups().getFirst(), 40, 16);
                        }
                        later(400, () -> {
                            after[0] = popups().isEmpty()
                                    ? Float.NaN
                                    : popups().getFirst().size().height();
                            Goldberry.stop();
                        });
                    });
                })));

        assertFalse(Float.isNaN(before[0]), "no list opened at all");
        assertTrue(
                after[0] > before[0] + 1,
                "the popup was " + before[0] + " tall and is " + after[0]
                        + " after three rows appeared in it, so they were drawn"
                        + " into a window that had no room for them");
    }
}
