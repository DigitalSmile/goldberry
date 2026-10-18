package dev.goldberry.widgets;

import static dev.goldberry.widgets.TestLoop.later;
import static dev.goldberry.widgets.TestLoop.popups;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.Application;
import dev.goldberry.Goldberry;
import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.Host;
import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.key.Key;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessPopup;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.Menu;
import dev.goldberry.widgets.menu.Menus;

/// The toolkit's own openers say where their popups come from, so an
/// application's descendant selectors reach the rows of a `select`'s list and
/// of a submenu from the part of the window they were opened in.
///
/// Each case runs twice, inside a column with `roomy` and inside one without,
/// and the popup's height says whether `.roomy …` reached in.
@DisplayName("a stock popup is styled from the widget that opened it")
class OpenedFromWidgetsTest {

    private static final String SHEET = """
            #page { padding: 24px }
            .roomy select-list option { min-height: 64px }
            .roomy menu menu item { min-height: 64px }
            """;

    private HeadlessBackend backend;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A fresh headless backend for each launch. **Not once per test**: a launch
    /// shuts the runtime down as it returns, and a second launch with nothing
    /// installed would start the real desktop backend and open a real window.
    private void install() {
        backend = new HeadlessBackend();
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryTestAccess.shutdown();
    }

    private static final class TestApp implements Application {

        private final Widget content;
        private final Set<String> classes;
        private final Consumer<Host> onStart;

        TestApp(Widget content, Set<String> classes, Consumer<Host> onStart) {
            this.content = content;
            this.classes = classes;
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Column(List.of(content), Attributes.NONE.id("page").classes(classes.toArray(String[]::new)));
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(
                    Controls.baseStylesheet(),
                    Theme.NORD_DARK.load(),
                    Stylesheet.parse(CascadeLayer.APPLICATION, SHEET));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }
    }

    private HeadlessWindow main() {
        return backend.windows().stream()
                .filter(window -> !(window instanceof HeadlessPopup))
                .map(HeadlessWindow.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private float openSelect(Set<String> classes) {
        var height = new float[] {Float.NaN};
        install();
        Goldberry.launch(new TestApp(
                new Select("", value -> {}, new Option("london", "London"), new Option("paris", "Paris"))
                        .withAttributes(Attributes.NONE.id("city")),
                classes,
                host -> later(200, () -> {
                    var field = host.anchor("city").orElseThrow().bounds();
                    var x = field.left() + 20;
                    var y = field.top() + 8;
                    backend.post(new BackendEvent.PointerMoved(main(), x, y, 0));
                    backend.post(new BackendEvent.PointerPressed(main(), x, y, 1, 1, 0));
                    backend.post(new BackendEvent.PointerReleased(main(), x, y, 1, 1, 0));
                    later(300, () -> {
                        popups(backend).stream()
                                .filter(HeadlessPopup::isOpen)
                                .findFirst()
                                .ifPresent(list -> height[0] = list.size().height());
                        Goldberry.stop();
                    });
                })));
        return height[0];
    }

    /// The outer menu's height, then the submenu's.
    private float[] openSubmenu(Set<String> classes) {
        var heights = new float[] {Float.NaN, Float.NaN};
        install();
        Goldberry.launch(new TestApp(
                new Button("More").withAttributes(Attributes.NONE.id("more")),
                classes,
                host -> later(200, () -> {
                    var menu = new Menu(
                            new Item("Plain", () -> {}),
                            new Item("Share").submenu(new Item("Mail", () -> {}), new Item("Link", () -> {})));
                    Menus.open(host, "more", menu).orElseThrow();
                    later(300, () -> {
                        // The first row has the focus, so one Down is the branch.
                        press(Key.DOWN);
                        press(Key.RIGHT);
                        later(300, () -> {
                            var open = popups(backend).stream()
                                    .filter(HeadlessPopup::isOpen)
                                    .toList();
                            if (open.size() == 2) {
                                heights[0] = open.get(0).size().height();
                                heights[1] = open.get(1).size().height();
                            }
                            Goldberry.stop();
                        });
                    });
                })));
        return heights;
    }

    private void press(Key key) {
        backend.post(new BackendEvent.KeyPressed(main(), key.sdlKeycode(), 0, false));
    }

    @Test
    @Timeout(30)
    @DisplayName("a select's list, through the select")
    void selectList() {
        var plain = openSelect(Set.of());
        var roomy = openSelect(Set.of("roomy"));

        assertAll(
                () -> assertTrue(plain < 100, "two ordinary rows, not " + plain),
                () -> assertTrue(roomy >= 128, "two rows of at least 64px, not " + roomy));
    }

    @Test
    @Timeout(30)
    @DisplayName("a submenu, through its row and the menu that row is in")
    void submenu() {
        var plain = openSubmenu(Set.of());
        var roomy = openSubmenu(Set.of("roomy"));

        assertAll(
                () -> assertTrue(plain[1] < 100, "two ordinary rows, not " + plain[1]),
                () -> assertTrue(roomy[1] >= 128, "`.roomy menu menu item` reached the submenu: " + roomy[1]),
                () -> assertEquals(plain[0], roomy[0], "and only the submenu, which is the one under two menus"));
    }
}
