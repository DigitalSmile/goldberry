package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.GoldberryTestAccess;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
            return List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                    Stylesheet.parse(CascadeLayer.APPLICATION,
                            "#page { flex-grow: 1; background: #2e3440 }"));
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
        }).thenRun(action);
    }

    /// The page carries a name; a right-click on it opens the menu that name means,
    /// **at the pointer** rather than at the widget.
    @Test
    @Timeout(20)
    @DisplayName("a right-click on a widget that named a menu opens it, where the pointer is")
    void opensAtThePointer() {
        var count = new int[1];
        var offset = new io.github.digitalsmile.goldberry.backend.LogicalPoint[1];
        var page = new Column(List.of(new Text("right-click me")),
                new io.github.digitalsmile.goldberry.widget.Attributes("page",
                        java.util.Set.of(), "page", null, "rows"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(
                    new Item("Rename", () -> { }),
                    new Item("Delete", () -> { }))));
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
        var page = new Column(List.of(new Text("click me")),
                new io.github.digitalsmile.goldberry.widget.Attributes("page",
                        java.util.Set.of(), "page", null, "rows"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> { }))));
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
        var page = new Column(List.of(new Text("nothing here")),
                new io.github.digitalsmile.goldberry.widget.Attributes("page",
                        java.util.Set.of(), "page"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> { }))));
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

    /// A name nobody registered is logged and ignored: a right-click is not a
    /// request that can fail usefully, and taking the window down because a menu
    /// is missing is worse than the menu being missing.
    @Test
    @Timeout(20)
    @DisplayName("an unregistered name is ignored rather than fatal")
    void unknownName() {
        var count = new int[1];
        var survived = new boolean[1];
        var page = new Column(List.of(new Text("right-click me")),
                new io.github.digitalsmile.goldberry.widget.Attributes("page",
                        java.util.Set.of(), "page", null, "nothing-by-that-name"));

        Goldberry.launch(new TestApp(page, host -> {
            Menus.contextMenus(host, Map.of("rows", new Menu(new Item("Rename", () -> { }))));
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
