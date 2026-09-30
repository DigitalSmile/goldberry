package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A launch subscribes the application's models to its window, and gives the
/// subscriptions back when the window is gone.
///
/// The models are the application's, not the window's, and one can outlive a
/// launch. Found by Qodana's `AutoCloseableResource` in the 2026-09-30 triage
/// (`docs/static-analysis-plan.md`, Q4): the two subscriptions per model were
/// dropped on the floor, so a closed window went on being asked for frames.
class LauncherModelSubscriptionsTest {

    @Model
    static final class Settings {

        @Bind(value = "app.theme", restyle = true)
        String theme = "dark";

        @Bind("app.gain")
        int gain = 40;
    }

    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    /// An application with one model, which counts the model's frame listeners
    /// while it is running and then closes its window.
    private static final class ModelApp implements Application {

        final Settings settings = new Settings();
        int listenersWhileRunning = -1;

        @Override
        public Widget root() {
            return new Plate(Attributes.NONE);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(40, 40);
        }

        @Override
        public List<Object> models() {
            return List.of(settings);
        }

        @Override
        public void start(Host host) {
            // start() runs before the models are subscribed, so the count is
            // read from the loop, once start() has returned.
            Goldberry.ui().execute(() -> {
                listenersWhileRunning = Models.frameListenerCount(settings);
                host.window().close();
            });
        }
    }

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        GoldberryRuntime.install(new HeadlessBackend());
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("a model is let go of when its window closes, so a later change asks nobody for a frame")
    void modelsAreLetGo() {
        var app = new ModelApp();
        assertEquals(0, Models.frameListenerCount(app.settings));

        Goldberry.launch(app);

        assertEquals(2, app.listenersWhileRunning, "a restyle and a repaint listener while the window is open");
        assertEquals(0, Models.frameListenerCount(app.settings), "the launch kept a subscription to its model");
    }

    @Test
    @DisplayName("the count is of repaint and restyle listeners together, and a closed subscription leaves it")
    void countsBothKinds() throws Exception {
        var settings = new Settings();
        try (var _ = Models.onRepaint(settings, () -> {})) {
            try (var _ = Models.onRestyle(settings, () -> {})) {
                assertEquals(2, Models.frameListenerCount(settings));
            }
            assertEquals(1, Models.frameListenerCount(settings));
        }
        assertEquals(0, Models.frameListenerCount(settings));
    }
}
