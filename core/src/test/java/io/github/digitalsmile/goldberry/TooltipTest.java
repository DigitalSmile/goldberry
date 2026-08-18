package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PopupKind;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// `tooltip="…"` end to end: the pointer rests on a widget, a delay passes, and a
/// popup opens with the text in it.
///
/// Driven through the real launcher and the real event loop, because the whole
/// point of it is the seam between three things that are otherwise separate — the
/// router knows what is hovered, the loop owns the delay, and the launcher owns
/// the window ([ADR-0105]).
class TooltipTest {

    /// A node that fills its window and carries a tooltip.
    private record Target(Attributes attributes)
            implements Widget.Leaf, Styled, Paints, Attributed<Target> {

        @Override
        public String cssType() {
            return "target";
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
        public Target withAttributes(Attributes value) {
            return new Target(value);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    private static final class TestApp implements Application {

        private final Widget root;
        private final java.util.function.Consumer<Host> onStart;

        TestApp(Widget root, java.util.function.Consumer<Host> onStart) {
            this.root = root;
            this.onStart = onStart;
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
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
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    target  { background: #204060; cursor: pointer }
                    tooltip { padding: 4px; background: #1c212a; color: #eceff4 }
                    """));
        }
    }

    private HeadlessBackend backend;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void tearDown() {
        GoldberryRuntime.shutdown();
    }

    private HeadlessWindow ownerWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private java.util.Optional<HeadlessPopup> tooltipWindow() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .filter(popup -> popup.kind() == PopupKind.TOOLTIP)
                .findFirst();
    }

    /// The pointer rests on the widget and, a delay later, its tooltip is a real
    /// popup window — of the **tooltip** kind, which is what keeps it from taking
    /// the focus of the field it is describing.
    @Test
    @Timeout(20)
    @DisplayName("resting the pointer on a widget with a tooltip opens one")
    void opensOnHover() {
        var shown = new boolean[1];
        var kind = new PopupKind[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save the document")).id("target"),
                host -> hoverAfterTheFirstFrame(() ->
                        // Long enough for the 500ms delay to come due. The loop is
                        // woken by its own timer rather than by this.
                        later(900, () -> {
                            shown[0] = tooltipWindow().isPresent();
                            kind[0] = tooltipWindow().map(HeadlessPopup::kind).orElse(null);
                            Goldberry.stop();
                        }))));

        assertTrue(shown[0], "no tooltip appeared after the delay");
        assertEquals(PopupKind.TOOLTIP, kind[0]);
    }

    /// And it does not appear the instant the pointer arrives, which is the whole
    /// reason for a delay: a pointer crossing a toolbar would otherwise open six.
    @Test
    @Timeout(20)
    @DisplayName("it does not open immediately")
    void waitsForTheDelay() {
        var immediately = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save")).id("target"),
                host -> hoverAfterTheFirstFrame(() ->
                        later(60, () -> {
                            immediately[0] = tooltipWindow().isPresent();
                            Goldberry.stop();
                        }))));

        assertFalse(immediately[0], "60ms is not 500ms");
    }

    /// The pointer leaving cancels the timer. Nothing opens, ever — as opposed to
    /// opening and closing again, which would flash.
    @Test
    @Timeout(20)
    @DisplayName("the pointer moving away before the delay cancels it")
    void cancelledByLeaving() {
        var appeared = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save")).id("target"),
                host -> hoverAfterTheFirstFrame(() -> {
                    backend.post(new BackendEvent.PointerExited(ownerWindow()));
                    later(900, () -> {
                        appeared[0] = tooltipWindow().isPresent();
                        Goldberry.stop();
                    });
                })));

        assertFalse(appeared[0]);
    }

    /// A widget with no tooltip attribute opens nothing, which is every widget in
    /// the catalog unless somebody said otherwise.
    @Test
    @Timeout(20)
    @DisplayName("a widget without the attribute has no tooltip")
    void noAttributeNoTooltip() {
        var appeared = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE).id("target"),
                host -> hoverAfterTheFirstFrame(() ->
                        later(900, () -> {
                            appeared[0] = tooltipWindow().isPresent();
                            Goldberry.stop();
                        }))));

        assertFalse(appeared[0]);
    }

    /// Runs `action` on the UI thread after `millis`.
    ///
    /// Waiting on a virtual thread rather than on [io.github.digitalsmile.goldberry.backend.EventLoop#after],
    /// deliberately: the loop's own timer is what is under test here, and a test
    /// that measured it with itself would pass whatever it did.
    /// The cursor the owner window is showing, and what it is hovering — the two
    /// things a tooltip appearing must not disturb.
    @org.junit.jupiter.api.Test
    @Timeout(20)
    @DisplayName("a tooltip appearing does not take the hover off what it describes")
    void doesNotDisturbTheHover() {
        var cursorAfter = new io.github.digitalsmile.goldberry.backend.Cursor[1];
        var hoveredAfter = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save the document")).id("target"),
                host -> hoverAfterTheFirstFrame(() ->
                        later(900, () -> {
                            cursorAfter[0] = ownerWindow().cursor();
                            hoveredAfter[0] = tooltipWindow().isPresent();
                            Goldberry.stop();
                        }))));

        assertTrue(hoveredAfter[0], "the tooltip is up");
        assertEquals(io.github.digitalsmile.goldberry.backend.Cursor.POINTER, cursorAfter[0],
                "and the pointer still shows what it is over");
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

    /// A pointer move, posted **after the first frame**: hit testing runs against
    /// the frame that was painted (ADR-0054), so a pointer event that arrives
    /// before there is one lands on nothing at all.
    private void hoverAfterTheFirstFrame(Runnable then) {
        later(150, () -> {
            backend.post(new BackendEvent.PointerMoved(ownerWindow(), 50, 50, 0));
            then.run();
        });
    }
}
