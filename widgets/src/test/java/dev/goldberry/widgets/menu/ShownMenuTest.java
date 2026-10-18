package dev.goldberry.widgets.menu;

import static dev.goldberry.widgets.TestLoop.later;
import static dev.goldberry.widgets.TestLoop.popups;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
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
import dev.goldberry.input.key.Key;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessPopup;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;

/// [Menus#show]: a menu whose rows change while it is open.
class ShownMenuTest {

    private static final class TestApp implements Application {

        private final Consumer<Host> onStart;

        TestApp(Consumer<Host> onStart) {
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Column(List.of(), Attributes.NONE);
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

    private static final LogicalRect ANCHOR = LogicalRect.of(10, 10, 80, 24);

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

    private void press(Key key) {
        var owner = backend.windows().stream()
                .filter(window -> !(window instanceof HeadlessPopup))
                .map(HeadlessWindow.class::cast)
                .findFirst()
                .orElseThrow();
        backend.post(new BackendEvent.KeyPressed(owner, key.sdlKeycode(), 0, false));
    }

    @Test
    @Timeout(20)
    @DisplayName("a row enabled while the menu is open can be chosen, in the same popup")
    void updatedRowsRun() {
        var chosen = new ArrayList<String>();
        var popupsAfterUpdate = new int[1];
        Goldberry.launch(new TestApp(host -> {
            var shown = Menus.show(
                            host,
                            ANCHOR,
                            new Menu(
                                    new Item("Paste", () -> chosen.add("stale")).disabled(true),
                                    new Item("Other", () -> chosen.add("other"))))
                    .orElseThrow();
            later(200, () -> {
                shown.update(new Menu(
                        new Item("Paste", () -> chosen.add("paste")), new Item("Other", () -> chosen.add("other"))));
                later(150, () -> {
                    popupsAfterUpdate[0] = popups(backend).size();
                    // The focus stayed on Other, the one row that could run;
                    // Paste is above it now that it can run too.
                    press(Key.UP);
                    press(Key.ENTER);
                    later(150, Goldberry::stop);
                });
            });
        }));

        assertAll(
                () -> assertEquals(1, popupsAfterUpdate[0], "updated in place, not reopened"),
                () -> assertEquals(List.of("paste"), chosen));
    }

    @Test
    @Timeout(20)
    @DisplayName("an update after the menu closed does nothing")
    void afterClosing() {
        var open = new boolean[] {true};
        Goldberry.launch(new TestApp(host -> {
            var shown = Menus.show(host, ANCHOR, new Menu(new Item("One", () -> {})))
                    .orElseThrow();
            later(150, () -> {
                shown.close();
                shown.close();
                shown.update(new Menu(new Item("Two", () -> {})));
                later(100, () -> {
                    open[0] = shown.isOpen() || popups(backend).stream().anyMatch(HeadlessPopup::isOpen);
                    Goldberry.stop();
                });
            });
        }));

        assertFalse(open[0]);
    }
}
