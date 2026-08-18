package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.GoldberryTestAccess;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// [Menus] — the half of §8 that is a window rather than a tree, driven through
/// the real launcher and the real frame loop.
///
/// Clicks are posted at coordinates inside the popup's own window, so what is
/// being tested is the shipping path: the router hit-tests the frame the popup
/// painted, an `item` turns a click into its command, and `Menus` turns that into
/// "and close the stack".
class MenusTest {

    private static final class TestApp implements Application {

        private final Consumer<Host> onStart;

        TestApp(Consumer<Host> onStart) {
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Column(List.of(), io.github.digitalsmile.goldberry.widget.Attributes.NONE);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load());
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

    private List<HeadlessPopup> popups() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .toList();
    }

    /// A click at `(x, y)` in `window`'s own coordinates: press, then release,
    /// which is what the router turns into a `CLICKED`.
    private void click(HeadlessWindow window, float x, float y) {
        backend.post(new BackendEvent.PointerMoved(window, x, y, 0));
        backend.post(new BackendEvent.PointerPressed(window, x, y, 1, 1, 0));
        backend.post(new BackendEvent.PointerReleased(window, x, y, 1, 1, 0));
    }

    /// Runs `action` on the UI thread after `millis` — long enough for the popup
    /// to have painted, which is what gives its router something to hit-test.
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

    private static final LogicalRect ANCHOR = LogicalRect.of(10, 10, 80, 24);

    /// Choosing a command runs it **and** closes the menu, which is what choosing
    /// a command does everywhere — and which `Menus` arranges so that an
    /// application cannot forget it on one row out of nine.
    @Test
    @Timeout(20)
    @DisplayName("clicking an item runs its command and closes the menu")
    void chooseACommand() {
        var chosen = new ArrayList<String>();
        var openAfter = new boolean[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(
                    new Item("First", () -> chosen.add("first")),
                    new Item("Second", () -> chosen.add("second")));
            var popup = Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                // The first row: 4px of the menu's padding, then half a row down.
                click((HeadlessWindow) popups().getFirst(), 40, 20);
                later(200, () -> {
                    openAfter[0] = popup.isOpen();
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(List.of("first"), chosen);
        assertFalse(openAfter[0], "a chosen command closes the menu it was chosen from");
    }

    /// Hovering a row with children opens its submenu **beside** it, and the
    /// submenu is a second popup rather than something drawn inside the first.
    @Test
    @Timeout(20)
    @DisplayName("hovering an item with children opens a submenu beside it")
    void openASubmenu() {
        var count = new int[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(
                    new Item("Plain", () -> { }),
                    new Item("More").submenu(new Item("Inner", () -> { })));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                // The second row, which is the one with children.
                var first = (HeadlessWindow) popups().getFirst();
                backend.post(new BackendEvent.PointerMoved(first, 40, 52, 0));
                later(300, () -> {
                    count[0] = popups().size();
                    Goldberry.stop();
                });
            });
        }));

        assertEquals(2, count[0], "the submenu is a window of its own, beside its item");
    }

    /// And choosing from the submenu closes both, because a command closes the
    /// whole stack rather than one level of it.
    @Test
    @Timeout(20)
    @DisplayName("a command in a submenu closes the whole stack")
    void chooseFromASubmenu() {
        var chosen = new ArrayList<String>();
        var left = new int[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(
                    new Item("Plain", () -> { }),
                    new Item("More").submenu(new Item("Inner", () -> chosen.add("inner"))));
            Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                backend.post(new BackendEvent.PointerMoved(
                        (HeadlessWindow) popups().getFirst(), 40, 52, 0));
                later(300, () -> {
                    var submenu = popups().get(1);
                    click((HeadlessWindow) submenu, 40, 20);
                    later(200, () -> {
                        left[0] = (int) popups().stream().filter(HeadlessPopup::isOpen).count();
                        Goldberry.stop();
                    });
                });
            });
        }));

        assertEquals(List.of("inner"), chosen);
        assertEquals(0, left[0], "choosing a command leaves nothing on screen");
    }

    @Test
    @Timeout(20)
    @DisplayName("a disabled item does nothing at all")
    void disabledDoesNothing() {
        var chosen = new ArrayList<String>();
        var stillOpen = new boolean[1];
        Goldberry.launch(new TestApp(host -> {
            var menu = new Menu(new Item("Nope", () -> chosen.add("nope")).disabled(true));
            var popup = Menus.open(host, ANCHOR, menu).orElseThrow();

            later(200, () -> {
                click((HeadlessWindow) popups().getFirst(), 40, 20);
                later(200, () -> {
                    stillOpen[0] = popup.isOpen();
                    Goldberry.stop();
                });
            });
        }));

        assertTrue(chosen.isEmpty(), "a disabled command does not run");
        assertTrue(stillOpen[0], "and does not close the menu either");
    }
}
