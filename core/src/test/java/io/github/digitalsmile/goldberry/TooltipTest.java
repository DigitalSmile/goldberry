package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.popup.PopupKind;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// `tooltip="…"` end to end: the pointer rests on a widget, a delay passes, and a
/// popup opens with the text in it.
///
/// Driven through the real launcher and the real event loop, because the whole
/// point of it is the seam between three things that are otherwise separate — the
/// router knows what is hovered, the loop owns the delay, and the launcher owns
/// the window ([ADR-0105]).
class TooltipTest {

    /// A node that fills its window and carries a tooltip.
    private record Target(Attributes attributes) implements Widget.Leaf, Styled, Paints, Attributed<Target> {

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

    /// Two [Target]s side by side, so the pointer can move **between** two
    /// tooltipped nodes — which is the case §3's second number is about and the
    /// one a single full-window target cannot produce.
    private record Pair(Widget left, Widget right) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "pair";
        }

        @Override
        public List<Widget> children() {
            return List.of(left, right);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of()
                    .style(style)
                    .grow(1)
                    .direction(io.github.digitalsmile.goldberry.layout.FlexDirection.ROW)
                    .children(children.toArray(Box[]::new));
        }
    }

    private static final class TestApp implements Application {

        private final Widget root;
        private final java.util.function.Consumer<Host> onStart;

        /// Rules the test adds on top of the two below — how a token that only
        /// exists in a stylesheet gets in front of the launcher.
        private final String extraCss;

        TestApp(Widget root, java.util.function.Consumer<Host> onStart) {
            this(root, onStart, "");
        }

        TestApp(Widget root, java.util.function.Consumer<Host> onStart, String extraCss) {
            this.root = root;
            this.onStart = onStart;
            this.extraCss = extraCss;
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
                    """ + extraCss));
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
                host -> hoverAfterTheFirstFrame(() -> later(60, () -> {
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
                host -> hoverAfterTheFirstFrame(() -> later(900, () -> {
                    appeared[0] = tooltipWindow().isPresent();
                    Goldberry.stop();
                }))));

        assertFalse(appeared[0]);
    }

    /// Runs `action` on the UI thread after `millis`.
    ///
    /// Waiting on a virtual thread rather than on [EventLoop#after],
    /// deliberately: the loop's own timer is what is under test here, and a test
    /// that measured it with itself would pass whatever it did.
    /// The cursor the owner window is showing, and what it is hovering — the two
    /// things a tooltip appearing must not disturb.
    @org.junit.jupiter.api.Test
    @Timeout(20)
    @DisplayName("a tooltip appearing does not take the hover off what it describes")
    void doesNotDisturbTheHover() {
        var cursorAfter = new Cursor[1];
        var hoveredAfter = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save the document")).id("target"),
                host -> hoverAfterTheFirstFrame(() -> later(900, () -> {
                    cursorAfter[0] = ownerWindow().cursor();
                    hoveredAfter[0] = tooltipWindow().isPresent();
                    Goldberry.stop();
                }))));

        assertTrue(hoveredAfter[0], "the tooltip is up");
        assertEquals(Cursor.POINTER, cursorAfter[0], "and the pointer still shows what it is over");
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

    /// A pointer move, posted **after the first frame**: hit testing runs against
    /// the frame that was painted (ADR-0054), so a pointer event that arrives
    /// before there is one lands on nothing at all.
    private void hoverAfterTheFirstFrame(Runnable then) {
        later(150, () -> {
            backend.post(new BackendEvent.PointerMoved(ownerWindow(), 50, 50, 0));
            then.run();
        });
    }

    /// **A stylesheet can change the delay**, which §3's `tooltip` row has always
    /// pinned and nothing could read: "delay 500ms show / 100ms move-between".
    ///
    /// The entry that asked for this called it blocked twice over — "nothing
    /// above the cascade can read a resolved custom property", and whether the
    /// design system should carry a duration that is not motion. Both expired:
    /// `BuildContext.duration` reads one, and §3 had carried the number all
    /// along ([ADR-0262]).
    ///
    /// Asserted at **60ms against a default of 500**, and read at 250 — a window
    /// where the token's answer is open and the default's is not, so the test
    /// fails against the old code rather than merely passing against the new.
    @Test
    @Timeout(20)
    @DisplayName("a stylesheet may set the delay, and the launcher honours it")
    void theDelayIsAToken() {
        var shown = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save")).id("target"),
                host -> hoverAfterTheFirstFrame(() -> later(250, () -> {
                    shown[0] = tooltipWindow().isPresent();
                    Goldberry.stop();
                })),
                "\ntarget { --gb-tooltip-delay: 60ms }\n"));

        assertTrue(shown[0], "250ms is past a 60ms token and short of the 500ms default");
    }

    /// A token that is **not a duration** leaves the default alone.
    ///
    /// `ComputedStyle.durationMillis` is the one parser, so `--gb-tooltip-delay:
    /// 60px` is refused here for the reason `transition: color 200` is refused
    /// there — guessing the unit would make the one stylesheet that meant
    /// something else silently wrong.
    @Test
    @Timeout(20)
    @DisplayName("and a token that is not a duration is ignored rather than guessed at")
    void aLengthIsNotADelay() {
        var shown = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save")).id("target"),
                host -> hoverAfterTheFirstFrame(() -> later(250, () -> {
                    shown[0] = tooltipWindow().isPresent();
                    Goldberry.stop();
                })),
                "\ntarget { --gb-tooltip-delay: 60px }\n"));

        assertFalse(shown[0], "a length is not a delay, so the 500ms default should still be waiting");
    }

    /// **§3's second number, which had never been built.** The `tooltip` row says
    /// "delay 500ms show / **100ms move-between**", and every move scheduled the
    /// full 500 — so a user reading along a toolbar was served the whole sentence
    /// of hover intent again at every button.
    ///
    /// The window is the assertion: the pointer moves to the second target and
    /// the tooltip is read **250ms** later, which is past the 100ms move delay
    /// and short of the 500ms first-hover one. It fails against the old code
    /// ([ADR-0262]).
    @Test
    @Timeout(20)
    @DisplayName("moving from one tooltip to another waits §3's shorter delay")
    void movingBetweenTooltipsIsQuicker() {
        var shown = new boolean[1];
        Goldberry.launch(new TestApp(
                new Pair(
                        new Target(Attributes.NONE.tooltip("The first")).id("first"),
                        new Target(Attributes.NONE.tooltip("The second")).id("second")),
                host -> hoverAfterTheFirstFrame(() -> later(900, () -> {
                    // The first tooltip is up by now -- 900ms is past its 500.
                    backend.post(new BackendEvent.PointerMoved(ownerWindow(), 350, 50, 0));
                    later(250, () -> {
                        shown[0] = tooltipWindow().isPresent();
                        Goldberry.stop();
                    });
                }))));

        assertTrue(shown[0], "250ms is past §3's 100ms move-between and short of its 500ms first hover");
    }

    /// And the first of the two still waits the full delay, so the shorter one is
    /// a statement about *moving between* rather than a faster tooltip.
    @Test
    @Timeout(20)
    @DisplayName("but the first tooltip in a row still waits the full one")
    void theFirstStillWaits() {
        var shown = new boolean[1];
        Goldberry.launch(new TestApp(
                new Pair(
                        new Target(Attributes.NONE.tooltip("The first")).id("first"),
                        new Target(Attributes.NONE.tooltip("The second")).id("second")),
                host -> hoverAfterTheFirstFrame(() -> later(250, () -> {
                    shown[0] = tooltipWindow().isPresent();
                    Goldberry.stop();
                }))));

        assertFalse(shown[0], "nothing was showing to move between, so this is a first hover");
    }
}
