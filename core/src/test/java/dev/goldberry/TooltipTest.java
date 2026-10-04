package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.junit.DrivenRuntime;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessPopup;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.popup.PopupKind;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// `tooltip="…"` end to end: the pointer rests on a widget, a delay passes, and a
/// popup opens with the text in it.
///
/// Driven through the real launcher and the real event loop, because the whole
/// point of it is the seam between three things that are otherwise separate — the
/// router knows what is hovered, the loop owns the delay, and the launcher owns
/// the window.
///
/// **The delay is on a clock the test moves.** Every tooltip delay is an
/// [EventLoop#after], and the loop here is a [DrivenRuntime]'s, so "250 ms after
/// the hover" is a reading the test takes by advancing the clock 250 ms and
/// looking, not a sleep it hopes was 250 ms. A loaded runner that sleeps 88 ms
/// in a `sleep(1)` cannot make a tooltip that is not due appear, and cannot make
/// one that is due stay away: the first draft of this file slept, and a Windows
/// runner read "a length is not a delay" after the default had come due.
///
/// Read more: [Tooltips](https://goldberry.dev/docs/components/overlays.html#tooltips).
class TooltipTest {

    /// The delay a tooltip waits when the stylesheet says nothing usable.
    private static final Duration DEFAULT_DELAY = Duration.ofMillis(500);

    /// The shorter delay for moving from one tooltip straight to another.
    private static final Duration MOVE_DELAY = Duration.ofMillis(100);

    /// The least the clock moves: past a delay's last millisecond, or short of
    /// its first.
    private static final Duration A_TICK = Duration.ofMillis(1);

    /// The same, but in the Tab order — for the keyboard half of the rule.
    ///
    /// A second type rather than a flag on [Target], because `Target` is what
    /// every other test here hovers and making it focusable would put a focus
    /// ring in nine assertions that are about something else.
    private record Focusable(Attributes attributes)
            implements Widget.Leaf, Styled, Paints, Attributed<Focusable>, Handles {

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
        public boolean isFocusable() {
            return true;
        }

        @Override
        public Focusable withAttributes(Attributes value) {
            return new Focusable(value);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

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
    /// tooltipped nodes — which is the case the shorter move-between delay is
    /// about and the one a single full-window target cannot produce.
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
            return Box.of().style(style).grow(1).direction(FlexDirection.ROW).children(children.toArray(Box[]::new));
        }
    }

    private static final class TestApp implements Application {

        private final Widget root;
        private final Consumer<Host> onStart;

        /// Rules the test adds on top of the two below — how a token that only
        /// exists in a stylesheet gets in front of the launcher.
        private final String extraCss;

        TestApp(Widget root, Consumer<Host> onStart) {
            this(root, onStart, "");
        }

        TestApp(Widget root, Consumer<Host> onStart, String extraCss) {
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
    private DrivenRuntime runtime;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        runtime = DrivenRuntime.install(backend);
    }

    @AfterEach
    void tearDown() {
        // Null when setUp aborted before installing one: a build without the
        // library skips the class, and the skip must not become an error here.
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    private HeadlessWindow ownerWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    private Optional<HeadlessPopup> tooltipWindow() {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .filter(popup -> popup.kind() == PopupKind.TOOLTIP)
                .findFirst();
    }

    private boolean tooltipIsUp() {
        return tooltipWindow().isPresent();
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
                host -> hover(
                        host,
                        () -> elapsed(DEFAULT_DELAY.plus(A_TICK), () -> {
                            shown[0] = tooltipIsUp();
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
    @DisplayName("it does not open before the delay")
    void waitsForTheDelay() {
        var early = new boolean[1];
        var onTime = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save")).id("target"),
                host -> hover(
                        host,
                        () -> elapsed(DEFAULT_DELAY.minus(A_TICK), () -> {
                            early[0] = tooltipIsUp();
                            elapsed(A_TICK.multipliedBy(2), () -> {
                                onTime[0] = tooltipIsUp();
                                Goldberry.stop();
                            });
                        }))));

        assertFalse(early[0], "499ms is not 500ms");
        assertTrue(onTime[0], "and two ticks later it is");
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
                host -> hover(host, () -> {
                    backend.post(new BackendEvent.PointerExited(ownerWindow()));
                    afterTheNextPump(() -> elapsed(DEFAULT_DELAY.multipliedBy(2), () -> {
                        appeared[0] = tooltipIsUp();
                        Goldberry.stop();
                    }));
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
                host -> hover(
                        host,
                        () -> elapsed(DEFAULT_DELAY.multipliedBy(2), () -> {
                            appeared[0] = tooltipIsUp();
                            Goldberry.stop();
                        }))));

        assertFalse(appeared[0]);
    }

    /// **The bug this file was missing**: click the thing, move the pointer off
    /// it, and the tooltip stays.
    ///
    /// A click **focuses** the control. `tooltipTarget` fell back to
    /// `router.focused()` whenever nothing was hovered, so when the pointer left
    /// the answer was still the button — the target had not changed,
    /// `pointingChanged` returned early, and nothing ever hid the popup. It then
    /// sat over the window until something else took the focus.
    ///
    /// The pointer leaves by `PointerExited` rather than by moving elsewhere,
    /// because that is the shape the report had and the one where the fallback
    /// bites: there is nothing else under the pointer to take the hover.
    @Test
    @Timeout(20)
    @DisplayName("clicking a widget and moving the pointer off it closes the tooltip")
    void aClickDoesNotPinTheTooltip() {
        var upAfterTheClick = new boolean[1];
        var stillUpAfterLeaving = new boolean[1];
        Goldberry.launch(new TestApp(
                // **Focusable**, which is the whole point: a click on something
                // that cannot take the focus never reaches the fallback, and the
                // first draft of this test used `Target` and passed against the
                // unfixed launcher.
                new Focusable(Attributes.NONE.tooltip("Save the document")).id("target"),
                host -> hover(
                        host,
                        () -> elapsed(DEFAULT_DELAY.plus(A_TICK), () -> {
                            upAfterTheClick[0] = tooltipIsUp();
                            // Press and release where the pointer already is, which is
                            // what focuses the target.
                            backend.post(new BackendEvent.PointerPressed(ownerWindow(), 50, 50, 1, 1, 0));
                            backend.post(new BackendEvent.PointerReleased(ownerWindow(), 50, 50, 1, 1, 0));
                            // Comfortably past `SPURIOUS_EXIT_NANOS`, 250ms **on the wall
                            // clock**, which is the one the launcher reads for it: an exit
                            // inside that window after a tooltip opens is the one opening
                            // a popup provokes, and is swallowed. A sleep can only
                            // overshoot, so this exit is always the user's.
                            later(400, () -> {
                                backend.post(new BackendEvent.PointerExited(ownerWindow()));
                                afterTheNextPump(() -> {
                                    stillUpAfterLeaving[0] = tooltipIsUp();
                                    Goldberry.stop();
                                });
                            });
                        }))));

        assertTrue(upAfterTheClick[0], "the tooltip never opened, so the rest of this proves nothing");
        assertFalse(
                stillUpAfterLeaving[0],
                "the pointer left and the tooltip stayed: the keyboard-focus"
                        + " fallback caught a focus the mouse had put there");
    }

    /// And the half that must **not** regress: a tooltip shows on keyboard
    /// focus, and the fix above is one `focusedFromKeyboard()` away from removing
    /// it entirely.
    ///
    /// Tab moves the focus, nothing is hovered, and the tooltip opens anyway.
    @Test
    @Timeout(20)
    @DisplayName("but tabbing to it still opens one, with no pointer involved")
    void keyboardFocusStillShowsIt() {
        var shown = new boolean[1];
        Goldberry.launch(new TestApp(
                new Focusable(Attributes.NONE.tooltip("Save the document")).id("target"),
                host -> runtime.afterTheFirstFrame(host, () -> {
                    backend.post(new BackendEvent.KeyPressed(ownerWindow(), Key.TAB.sdlKeycode(), 0, false));
                    afterTheNextPump(() -> elapsed(DEFAULT_DELAY.plus(A_TICK), () -> {
                        shown[0] = tooltipIsUp();
                        Goldberry.stop();
                    }));
                })));

        assertTrue(shown[0], "a keyboard user gets the same tooltips a pointer user does");
    }

    /// The cursor the owner window is showing, and what it is hovering — the two
    /// things a tooltip appearing must not disturb.
    @Test
    @Timeout(20)
    @DisplayName("a tooltip appearing does not take the hover off what it describes")
    void doesNotDisturbTheHover() {
        var cursorAfter = new Cursor[1];
        var hoveredAfter = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save the document")).id("target"),
                host -> hover(
                        host,
                        () -> elapsed(DEFAULT_DELAY.plus(A_TICK), () -> {
                            cursorAfter[0] = ownerWindow().cursor();
                            hoveredAfter[0] = tooltipIsUp();
                            Goldberry.stop();
                        }))));

        assertTrue(hoveredAfter[0], "the tooltip is up");
        assertEquals(Cursor.POINTER, cursorAfter[0], "and the pointer still shows what it is over");
    }

    /// **A stylesheet can change the delay**, which the design system's `tooltip`
    /// row has always pinned and nothing could read: 500ms to show, 100ms moving
    /// between tooltips.
    ///
    /// The entry that asked for this called it blocked twice over — "nothing
    /// above the cascade can read a resolved custom property", and whether the
    /// design system should carry a duration that is not motion. Both expired:
    /// `BuildContext.duration` reads one, and the component metrics had carried
    /// the number all along, so a delay is a token like any other metric.
    ///
    /// Asserted at **60ms against a default of 500**, read a tick past 60 — where
    /// the token's answer is open and the default's is not, so the test fails
    /// against the old code rather than merely passing against the new.
    @Test
    @Timeout(20)
    @DisplayName("a stylesheet may set the delay, and the launcher honours it")
    void theDelayIsAToken() {
        var shown = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save")).id("target"),
                host -> hover(
                        host,
                        () -> elapsed(Duration.ofMillis(60).plus(A_TICK), () -> {
                            shown[0] = tooltipIsUp();
                            Goldberry.stop();
                        })),
                "\ntarget { --gb-tooltip-delay: 60ms }\n"));

        assertTrue(shown[0], "61ms is past a 60ms token and short of the 500ms default");
    }

    /// A token that is **not a duration** leaves the default alone.
    ///
    /// `ComputedStyle.durationMillis` is the one parser, so `--gb-tooltip-delay:
    /// 60px` is refused here for the reason `transition: color 200` is refused
    /// there — guessing the unit would make the one stylesheet that meant
    /// something else silently wrong. Read at 250 ms, where a 60 of any unit
    /// would be open and the default is not; and then at 501, where the default
    /// is, so the token has been ignored rather than refused altogether.
    @Test
    @Timeout(20)
    @DisplayName("and a token that is not a duration is ignored rather than guessed at")
    void aLengthIsNotADelay() {
        var early = new boolean[1];
        var onDefault = new boolean[1];
        Goldberry.launch(new TestApp(
                new Target(Attributes.NONE.tooltip("Save")).id("target"),
                host -> hover(
                        host,
                        () -> elapsed(Duration.ofMillis(250), () -> {
                            early[0] = tooltipIsUp();
                            elapsed(DEFAULT_DELAY.minus(Duration.ofMillis(250)).plus(A_TICK), () -> {
                                onDefault[0] = tooltipIsUp();
                                Goldberry.stop();
                            });
                        })),
                "\ntarget { --gb-tooltip-delay: 60px }\n"));

        assertFalse(early[0], "a length is not a delay, so the 500ms default should still be waiting");
        assertTrue(onDefault[0], "and the default is what it waits");
    }

    /// **The second number, which had never been built.** The `tooltip` row says
    /// 500ms to show and **100ms moving between**, and every move scheduled the
    /// full 500 — so a user reading along a toolbar was served the whole sentence
    /// of hover intent again at every button.
    ///
    /// The window is the assertion: the pointer moves to the second target and
    /// the tooltip is read a tick short of 100 ms later, when it is not up, and a
    /// tick past, when it is — short of the 500ms first-hover delay either way.
    /// It fails against the old code.
    @Test
    @Timeout(20)
    @DisplayName("moving from one tooltip to another waits the shorter delay")
    void movingBetweenTooltipsIsQuicker() {
        var early = new boolean[1];
        var onTime = new boolean[1];
        Goldberry.launch(new TestApp(
                new Pair(
                        new Target(Attributes.NONE.tooltip("The first")).id("first"),
                        new Target(Attributes.NONE.tooltip("The second")).id("second")),
                host -> hover(
                        host,
                        () -> elapsed(DEFAULT_DELAY.plus(A_TICK), () -> {
                            assertTrue(tooltipIsUp(), "the first tooltip is up, so this is a move between two");
                            backend.post(new BackendEvent.PointerMoved(ownerWindow(), 350, 50, 0));
                            afterTheNextPump(() -> elapsed(MOVE_DELAY.minus(A_TICK), () -> {
                                early[0] = tooltipIsUp();
                                elapsed(A_TICK.multipliedBy(2), () -> {
                                    onTime[0] = tooltipIsUp();
                                    Goldberry.stop();
                                });
                            }));
                        }))));

        assertFalse(early[0], "the move closed the first tooltip, and 99ms is short of the 100ms move-between delay");
        assertTrue(onTime[0], "101ms is past it, and short of the 500ms first hover");
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
                host -> hover(
                        host,
                        () -> elapsed(Duration.ofMillis(250), () -> {
                            shown[0] = tooltipIsUp();
                            Goldberry.stop();
                        }))));

        assertFalse(shown[0], "nothing was showing to move between, so this is a first hover");
    }

    /// Runs `then` after the loop's next pump: [DrivenRuntime#afterTheNextPump].
    private void afterTheNextPump(Runnable then) {
        runtime.afterTheNextPump(then);
    }

    /// Moves the clock `by` and runs `then` once the loop has fired whatever
    /// that made due: [DrivenRuntime#elapsed].
    private void elapsed(Duration by, Runnable then) {
        runtime.elapsed(by, then);
    }

    /// Rests the pointer on the target after the first frame and runs `then`
    /// once the window has taken the hover, which is when the tooltip's delay
    /// started on the clock. The cursor says the hover was taken: the target's
    /// `cursor: pointer` reaches the window in the same dispatch.
    private void hover(Host host, Runnable then) {
        runtime.afterTheFirstFrame(host, () -> {
            backend.post(new BackendEvent.PointerMoved(ownerWindow(), 50, 50, 0));
            afterTheNextPump(() -> {
                assertEquals(Cursor.POINTER, ownerWindow().cursor(), "the move did not land on the target");
                then.run();
            });
        });
    }

    /// A **wall-clock** wait, for the one thing the launcher times itself: the
    /// spurious-exit window after a tooltip opens. [DrivenRuntime#later].
    private static void later(long millis, Runnable action) {
        DrivenRuntime.later(millis, action);
    }
}
