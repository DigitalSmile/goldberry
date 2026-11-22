package dev.goldberry.widgets.text;

import static dev.goldberry.widgets.TestLoop.later;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessPopup;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;

/// Copying the words of a `text` under `user-select: text`, through the real
/// launcher: a triple click selects it, and the desktop's copy accelerator puts
/// it on the clipboard with nothing focused to take the key.
class UserSelectCopyTest {

    private static final String WORDS = "Deploy the orc board";

    /// `SDL_KMOD_LCTRL` and `SDL_KMOD_LGUI`: the platform's own copy modifier.
    private static final int SDL_CTRL = 0x0040;

    private static final int SDL_GUI = 0x0400;

    private record TestApp(Widget root, Consumer<Host> onStart) implements Application {

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 200);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(
                    Controls.baseStylesheet(),
                    Theme.NORD_DARK.load(),
                    Stylesheet.parse(CascadeLayer.APPLICATION, "text { user-select: text }"));
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
        return backend.windows().stream()
                .filter(window -> !(window instanceof HeadlessPopup))
                .map(HeadlessWindow.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private void click(float x, float y, int count) {
        backend.post(new BackendEvent.PointerPressed(window(), x, y, 1, count, 0));
        backend.post(new BackendEvent.PointerReleased(window(), x, y, 1, count, 0));
    }

    @Test
    @Timeout(20)
    @DisplayName("a triple click and the copy accelerator put the words on the clipboard")
    void copies() {
        var copy = PrimaryModifier.current() == Mod.META ? SDL_GUI : SDL_CTRL;
        Goldberry.launch(new TestApp(
                new Column(List.of(new Text(WORDS)), Attributes.NONE),
                host -> later(200, () -> {
                    backend.post(new BackendEvent.PointerMoved(window(), 12, 8, 0));
                    click(12, 8, 1);
                    click(12, 8, 2);
                    click(12, 8, 3);
                    later(150, () -> {
                        backend.post(new BackendEvent.KeyPressed(window(), Key.C.sdlKeycode(), copy, false));
                        later(150, Goldberry::stop);
                    });
                })));

        assertEquals(WORDS, backend.clipboard().text());
    }
}
