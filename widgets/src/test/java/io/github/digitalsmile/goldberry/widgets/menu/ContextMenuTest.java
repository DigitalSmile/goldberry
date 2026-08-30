package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §8's context menus: `context-menu="…"` on any widget, opened by the secondary
/// button where the pointer is.
///
/// Driven through the real launcher, because the whole of it is a seam — the
/// toolkit notices the click and finds the name, and the catalog turns the name
/// into a menu ([ADR-0108]).
class ContextMenuTest {

    private static final class TestApp implements Application {

        private final Widget root;
        private final Consumer<Host> onStart;

        TestApp(Widget root, Consumer<Host> onStart) {
            this.root = root;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return root;
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(
                    Controls.baseStylesheet(),
                    Theme.NORD_DARK.load(),
                    Stylesheet.parse(CascadeLayer.APPLICATION, "#page { flex-grow: 1; background: #2e3440 }"));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

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

    private HeadlessWindow window() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private List<HeadlessPopup> popups() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .toList();
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

    /// The page carries a name; a right-click on it opens the menu that name means,
    /// **at the pointer** rather than at the widget.
    @Test
    @Timeout(20)
    @DisplayName("a right-click on a widget that named a menu opens it, where the pointer is")
    void opensAtThePointer() {
        var count = new int[1];
        var offset = new LogicalPoint[1];
        var page = new Column(
                List.of(new Text("right-click me")),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes(
                        "page", java.util.Set.of(), "page", null, "rows"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(
                    host, Map.of("rows", new Menu(new Item("Rename", () -> {}), new Item("Delete", () -> {}))));
            later(150, () -> {
                backend.post(new BackendEvent.PointerMoved(window(), 120, 90, 0));
                backend.post(new BackendEvent.PointerPressed(window(), 120, 90, 3, 1, 0));
                later(200, () -> {
                    count[0] = popups().size();
                    if (!popups().isEmpty()) {
                        offset[0] = popups().getFirst().offset();
                    }
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(1, count[0], "the secondary button opens the named menu");
        assertEquals(120f, offset[0].x(), "anchored to the click, not to the widget");
        assertTrue(offset[0].y() >= 90f, "and just below it");
    }

    /// The *primary* button is not a context menu, which is the one thing that
    /// would be maddening if it were wrong.
    @Test
    @Timeout(20)
    @DisplayName("a left-click opens nothing")
    void primaryButtonDoesNot() {
        var count = new int[1];
        var page = new Column(
                List.of(new Text("click me")),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes(
                        "page", java.util.Set.of(), "page", null, "rows"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> {}))));
            later(150, () -> {
                backend.post(new BackendEvent.PointerMoved(window(), 120, 90, 0));
                backend.post(new BackendEvent.PointerPressed(window(), 120, 90, 1, 1, 0));
                later(200, () -> {
                    count[0] = popups().size();
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(0, count[0]);
    }

    /// A widget that named nothing opens nothing — which is every widget unless
    /// somebody said otherwise.
    @Test
    @Timeout(20)
    @DisplayName("a widget with no name opens nothing")
    void noNameNoMenu() {
        var count = new int[1];
        var page = new Column(
                List.of(new Text("nothing here")),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes("page", java.util.Set.of(), "page"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> {}))));
            later(150, () -> {
                backend.post(new BackendEvent.PointerMoved(window(), 120, 90, 0));
                backend.post(new BackendEvent.PointerPressed(window(), 120, 90, 3, 1, 0));
                later(200, () -> {
                    count[0] = popups().size();
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(0, count[0]);
    }

    // --- the keyboard's half (ADR-0208) --------------------------------------

    /// SDL's `SDLK_APPLICATION` — the key between `AltGr` and `Ctrl`.
    private static final int MENU_KEY = 0x40000065;

    /// SDL's `SDLK_F10`, which with `Shift` is the companion binding everywhere
    /// and the only one on a keyboard that has no menu key.
    private static final int F10 = 0x40000043;

    /// SDL's left-shift mask, which is what a real event carries.
    private static final int SHIFT = 0x0001;

    /// A focusable widget carrying a menu name, so the keyboard has somewhere to
    /// be. The pointer's tests use a `Column`, which nothing can focus.
    private static Widget focusableRow() {
        return new Column(
                List.of(new io.github.digitalsmile.goldberry.widgets.controls.button.Button(
                        "rename me",
                        null,
                        () -> {},
                        false,
                        new io.github.digitalsmile.goldberry.widget.attr.Attributes(
                                "target", java.util.Set.of(), "target", null, "rows"))),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes("page", java.util.Set.of(), "page"));
    }

    /// Opens whatever the focused widget named, and reports where the popup
    /// landed.
    private void pressWithFocus(int keycode, int modifiers, int[] count, LogicalPoint[] offset) {

        Goldberry.launch(new TestApp(focusableRow(), host -> {
            Menus.contextMenus(
                    host, Map.of("rows", new Menu(new Item("Rename", () -> {}), new Item("Delete", () -> {}))));
            later(150, () -> {
                // By id rather than by Tab: this test is about the menu key, and
                // routing focus through a traversal would make a focus bug look
                // like a menu bug.
                host.focus("target", true);
                later(100, () -> {
                    backend.post(new BackendEvent.KeyPressed(window(), keycode, modifiers, false));
                    later(200, () -> {
                        count[0] = popups().size();
                        if (!popups().isEmpty()) {
                            offset[0] = popups().getFirst().offset();
                        }
                        Goldberry.stop();
                    });
                });
            });
        }));
    }

    /// The keyboard's right-click. §7 names it and it was the half of ADR-0108
    /// that did not ship: a right-click is a thing only a pointer can do, and
    /// §2.2 requires everything to be reachable.
    @Test
    @Timeout(20)
    @DisplayName("the menu key opens the focused widget's menu, against the widget")
    void theMenuKeyOpensTheFocusedMenu() {
        var count = new int[1];
        var offset = new LogicalPoint[1];
        pressWithFocus(MENU_KEY, 0, count, offset);

        assertEquals(1, count[0], "the menu key opens the named menu");
        // **Against the widget, not at a point.** There is no pointer to anchor
        // to, so the menu hangs off the bottom of whatever has the focus ring —
        // which is where the reader is already looking. A zero anchor would put
        // it in the window's corner.
        assertTrue(offset[0].y() > 0f, () -> "anchored below the focused widget, and it is at y=" + offset[0].y());
    }

    /// The keyboards with no menu key on them, which is every Mac.
    @Test
    @Timeout(20)
    @DisplayName("Shift+F10 does the same, for a keyboard with no menu key")
    void shiftF10IsTheCompanion() {
        var count = new int[1];
        var offset = new LogicalPoint[1];
        pressWithFocus(F10, SHIFT, count, offset);

        assertEquals(1, count[0]);
    }

    /// `F10` alone is the menu bar's (ADR-0163) and must not be taken here, or
    /// an application with both would open a context menu where it meant to
    /// activate its menu bar.
    @Test
    @Timeout(20)
    @DisplayName("F10 without Shift is not this, because the menu bar has it")
    void bareF10IsNotIt() {
        var count = new int[1];
        var offset = new LogicalPoint[1];
        pressWithFocus(F10, 0, count, offset);

        assertEquals(0, count[0]);
    }

    /// Focus somewhere that named nothing opens nothing — the keyboard's version
    /// of a right-click on a widget with no name.
    @Test
    @Timeout(20)
    @DisplayName("the menu key over a widget that named nothing opens nothing")
    void theMenuKeyNeedsAName() {
        var count = new int[1];
        var page = new Column(
                List.of(new io.github.digitalsmile.goldberry.widgets.controls.button.Button(
                        "no menu here",
                        null,
                        () -> {},
                        false,
                        new io.github.digitalsmile.goldberry.widget.attr.Attributes(
                                "target", java.util.Set.of(), "target"))),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes("page", java.util.Set.of(), "page"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> {}))));
            later(150, () -> {
                host.focus("target", true);
                later(100, () -> {
                    backend.post(new BackendEvent.KeyPressed(window(), MENU_KEY, 0, false));
                    later(200, () -> {
                        count[0] = popups().size();
                        Goldberry.stop();
                    });
                });
            });
        }));

        assertEquals(0, count[0]);
    }

    /// With nothing focused there is no "here" for the keyboard to mean.
    @Test
    @Timeout(20)
    @DisplayName("the menu key with nothing focused opens nothing")
    void theMenuKeyNeedsAFocus() {
        var count = new int[1];
        var page = new Column(
                List.of(new Text("not focusable")),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes(
                        "page", java.util.Set.of(), "page", null, "rows"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> {}))));
            later(150, () -> {
                backend.post(new BackendEvent.KeyPressed(window(), MENU_KEY, 0, false));
                later(200, () -> {
                    count[0] = popups().size();
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(0, count[0], "a keyboard with no position has nothing to ask about");
    }

    /// A name nobody registered is logged and ignored: a right-click is not a
    /// request that can fail usefully, and taking the window down because a menu
    /// is missing is worse than the menu being missing.
    @Test
    @Timeout(20)
    @DisplayName("an unregistered name is ignored rather than fatal")
    void unknownName() {
        var count = new int[1];
        var survived = new boolean[1];
        var page = new Column(
                List.of(new Text("right-click me")),
                new io.github.digitalsmile.goldberry.widget.attr.Attributes(
                        "page", java.util.Set.of(), "page", null, "nothing-by-that-name"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> {}))));
            later(150, () -> {
                backend.post(new BackendEvent.PointerMoved(window(), 120, 90, 0));
                backend.post(new BackendEvent.PointerPressed(window(), 120, 90, 3, 1, 0));
                later(200, () -> {
                    count[0] = popups().size();
                    survived[0] = true;
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(0, count[0]);
        assertTrue(survived[0], "and the window is still running");
    }
}
