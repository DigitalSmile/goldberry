package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A [Located] widget **inside a popup** — `docs/gaps.md` G28, ADR-0320.
///
/// A popup has a pointer router of its own, so the rectangle it reported was in
/// the popup's own space: a swatch eight points from a floating bar's left edge
/// was told it was at x=8. `Host.attachedPopup` places in the *owner* window's
/// coordinates, so the popover anchored to that rectangle opened at (8, 30) of the
/// main window — the corner — however far across the screen the bar was.
///
/// The fix is a translation in the router, so what is asserted here is one
/// sentence: **the rectangle a widget in a popup is told is the rectangle the
/// owner window would use to place something beside it.**
class LocatedInPopupTest {

    /// A node that writes down every rectangle it is told about. Sized by the
    /// stylesheet, so the numbers are predictable.
    private record Swatch(String name, List<LogicalRect> selves, List<LogicalRect> clips)
            implements Widget.Leaf, Styled, Paints, Located {

        @Override
        public String cssType() {
            return "swatch";
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public void located(LogicalRect self, LogicalRect clip) {
            selves.add(self);
            clips.add(clip);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

    /// The bar the swatch sits in: padded, so the swatch is not at the popup's own
    /// origin and a translation that forgot the node's place inside the popup would
    /// still be wrong.
    private record Bar(List<Widget> items) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "bar";
        }

        @Override
        public List<Widget> children() {
            return items;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    private static final class TestApp implements Application {

        private final Consumer<Host> onStart;

        TestApp(Consumer<Host> onStart) {
            this.onStart = onStart;
        }

        @Override
        public Widget root() {
            return new Bar(List.of());
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(400, 300);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    bar { background: #eceff4; padding: 8px }
                    swatch { width: 24px; height: 24px; background: #bf616a }
                    """));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }

        @Override
        public void stop() {}
    }

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    /// Opens a popup at `at` with one swatch in it, and returns what the swatch was
    /// told. `then` runs three turns after it opened, for a test that moves it.
    private Told open(LogicalPoint at, Consumer<Popup> then) {
        var told = new Told(new ArrayList<>(), new ArrayList<>(), new Popup[1]);
        Goldberry.launch(
                new TestApp(host -> {
                    told.popup()[0] = host.popup(
                                    new Bar(List.of(new Swatch("swatch", told.selves(), told.clips()))),
                                    at,
                                    LogicalSize.of(120, 40))
                            .orElseThrow()
                            .lightDismiss(false);
                    afterTurns(host, 3, () -> {
                        then.accept(told.popup()[0]);
                        afterTurns(host, 3, () -> {
                            told.popup()[0].close();
                            Goldberry.stop();
                        });
                    });
                }),
                new String[] {"--frames=400"});
        return told;
    }

    /// @param selves every `self` the swatch was told, in order
    /// @param clips  every `clip` beside it
    /// @param popup  the popup, so a test can move it
    private record Told(List<LogicalRect> selves, List<LogicalRect> clips, Popup[] popup) {}

    @Test
    @Timeout(20)
    @DisplayName("a widget in a popup is told where it is in the owner window")
    void locatedIsInTheOwnersSpace() {
        var told = open(LogicalPoint.of(140, 90), popup -> {});

        assertTrue(!told.selves().isEmpty(), "the swatch was never told where it is");
        var self = told.selves().getFirst();
        // The popup is at (140, 90) of the owner and the bar pads its child by 8,
        // so the swatch is painted at (148, 98) of the window — which is the number
        // `Host.attachedPopup` would need to open a plane beside it.
        assertEquals(148, self.left(), 0.5, "the swatch reported its position inside the popup instead");
        assertEquals(98, self.top(), 0.5);
        assertEquals(24, self.width(), 0.5, "and the size is the size, which no translation may touch");
        assertEquals(24, self.height(), 0.5);
    }

    /// The clip is translated with it, because the two are documented as
    /// comparable: an `affix` inside a popup subtracts one from the other.
    @Test
    @Timeout(20)
    @DisplayName("the clip it is told about is in the same space")
    void theClipMovesWithIt() {
        var told = open(LogicalPoint.of(140, 90), popup -> {});

        var clip = told.clips().getFirst();
        assertEquals(140, clip.left(), 0.5, "nothing clips the swatch, so the clip is the popup's own rectangle");
        assertEquals(90, clip.top(), 0.5);
        assertTrue(
                clip.left() <= told.selves().getFirst().left(),
                "a clip that does not contain what it clips cannot be compared with it");
    }

    /// A popover follows a scrolling anchor by being **moved**, rather than closed
    /// and reopened — so the rectangles inside it have to follow too.
    @Test
    @Timeout(20)
    @DisplayName("moving the popup re-reports where its widgets are")
    void movingReports() {
        var told = open(LogicalPoint.of(140, 90), popup -> popup.move(LogicalPoint.of(40, 20)));

        assertNotNull(told.selves().getFirst());
        var last = told.selves().getLast();
        assertEquals(48, last.left(), 0.5, "the swatch is still reporting the place the popup used to be");
        assertEquals(28, last.top(), 0.5);
    }

    /// The other half: a widget in the **window** is unaffected, because a window's
    /// own space is the space its popups are placed in.
    @Test
    @Timeout(20)
    @DisplayName("a widget in the window itself is told the same thing as before")
    void theWindowIsUnchanged() {
        var selves = new ArrayList<LogicalRect>();
        var clips = new ArrayList<LogicalRect>();
        Goldberry.launch(
                new Application() {
                    @Override
                    public Widget root() {
                        return new Bar(List.of(new Swatch("swatch", selves, clips)));
                    }

                    @Override
                    public LogicalSize size() {
                        return LogicalSize.of(400, 300);
                    }

                    @Override
                    public List<Stylesheet> stylesheets() {
                        return new TestApp(host -> {}).stylesheets();
                    }

                    @Override
                    public void start(Host host) {}

                    @Override
                    public void stop() {}
                },
                new String[] {"--frames=3"});

        assertEquals(8, selves.getFirst().left(), 0.5, "the bar's padding, and nothing added to it");
        assertEquals(8, selves.getFirst().top(), 0.5);
        assertEquals(0, clips.getFirst().left(), 0.5, "the window's own rectangle starts at its own origin");
    }
}
