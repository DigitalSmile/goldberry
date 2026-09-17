package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The launcher under `--resize=WxH` and `--late-budget=N` — the two flags a
/// frame-evidence run is made of ([ADR-0342]).
///
/// Headless, so the window manager is the backend's: every request is
/// honoured on the spot and no refresh is ever missed. What is under test is
/// the launcher's side — that the walk is driven from the painted frame, from
/// the window's own size, and that a run that was not over budget exits the
/// way it always did.
class LauncherEvidenceTest {

    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Plate(String id) {
            this(new Attributes(id, Set.of(), id));
        }

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public String id() {
            return attributes.id();
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

    private static final class WalkedApp implements Application {

        final List<LogicalSize> resizes = new ArrayList<>();

        @Override
        public Widget root() {
            return new Plate("content");
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "plate { background: #204060 }"));
        }

        @Override
        public void start(Host host) {
            host.window().onResize(resizes::add);
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
    @DisplayName("the window is walked a pixel a frame from where it opened")
    void walkedAPixelAFrame() {
        var app = new WalkedApp();

        Goldberry.launch(app, new String[] {"--frames=6", "--resize=410x310"});

        // Each painted frame asks for one more pixel on each axis, between
        // frames, so the sizes the application hears arrive in single steps
        // from where it opened. How many of the six frames' requests landed
        // before the loop stopped depends on the pump's ordering, so the
        // assertion is on the steps and not on their count.
        assertTrue(app.resizes.size() >= 3, () -> "saw " + app.resizes);
        for (var index = 0; index < app.resizes.size(); index++) {
            assertEquals(LogicalSize.of(401 + index, 301 + index), app.resizes.get(index), () -> "saw " + app.resizes);
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("a walk turns round at its target")
    void turnsRound() {
        var app = new WalkedApp();

        Goldberry.launch(app, new String[] {"--frames=8", "--resize=402x302"});

        assertTrue(
                app.resizes.containsAll(List.of(LogicalSize.of(402, 302), LogicalSize.of(401, 301))),
                () -> "expected the walk to reach 402x302 and come back, saw " + app.resizes);
    }

    @Test
    @Timeout(10)
    @DisplayName("a run under budget exits as it always did")
    void underBudget() {
        var app = new WalkedApp();

        // A budget of zero is the strictest there is, and a backend with no
        // display to be late for is never late.
        Goldberry.launch(app, new String[] {"--frames=4", "--resize=410x310", "--late-budget=0"});

        assertTrue(app.resizes.size() >= 1, () -> "saw " + app.resizes);
    }
}
