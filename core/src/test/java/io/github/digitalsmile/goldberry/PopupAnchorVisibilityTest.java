package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// What a popup does when the widget it hangs off **stops being drawn**.
///
/// [PopupLifecycleTest] covers the half that was built first: a `popover`
/// anchored by id travels with an anchor that scrolls ([ADR-0270]). The anchor in
/// that test scrolls inside a viewport that does not clip, so it never leaves.
/// Here it does, and the question is what the popup is supposed to do about it —
/// which is `close`, for the reasons in [ADR-0433].
///
/// The scenery is a real clipping viewport: an `overflow: hidden` box with a
/// translated child inside it, which is what a `scroll` is — Yoga lays the
/// content out where it always was and the viewport moves it ([ADR-0114],
/// ADR-0116).
class PopupAnchorVisibilityTest {

    /// A box that **clips its children**, which the `Scrolled` in
    /// [PopupLifecycleTest] deliberately does not.
    ///
    /// The clip is on this node and the translation is on the one inside it,
    /// which is the only arrangement that behaves like a scroll view: a clip set
    /// on the translating box would move with the content it is supposed to be
    /// cutting off (`RenderTree.clipFor` maps the clip through the accumulated
    /// matrix — ADR-0114).
    private record Clipped(List<Widget> kids, Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Clipped(Widget... kids) {
            this(List.of(kids), new Attributes("clipbox", Set.of(), "clipbox"));
        }

        @Override
        public String cssType() {
            return "clipbox";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).overflow(Overflow.HIDDEN).children(children.toArray(Box[]::new));
        }
    }

    /// The content of the viewport: translated by a bound offset, so setting the
    /// property scrolls between two frames with no resize and no event that says
    /// so ([ADR-0062], [ADR-0122]).
    private record Scrolled(Property<Float> offset, List<Widget> kids, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        Scrolled(Property<Float> offset, Widget... kids) {
            this(offset, List.of(kids), new Attributes("strip", Set.of(), "strip"));
        }

        @Override
        public io.github.digitalsmile.goldberry.bind.Observable<?> binding() {
            return offset;
        }

        @Override
        public String cssType() {
            return "strip";
        }

        @Override
        public String id() {
            return attributes.id();
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of()
                    .style(style)
                    .transform(Transform.of(new Transform.Function.Translate(
                            Transform.Length.ZERO, Transform.Length.px(-offset.get()))))
                    .children(children.toArray(Box[]::new));
        }
    }

    /// A node with a size of its own — the anchor, and the popup's content.
    private record Sized(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        Sized(String id) {
            this(new Attributes(id, Set.of(), id));
        }

        @Override
        public String cssType() {
            return "sized";
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
            return Box.of().style(style);
        }
    }

    private static final class TestApp implements Application {

        private final Widget root;
        private final Consumer<Host> onStart;

        TestApp(Widget root, Consumer<Host> onStart) {
            this.root = root;
            this.onStart = onStart;
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
            // The viewport is 100 tall and the anchor 80, sitting at its top. An
            // offset of 60 leaves 20 logical pixels of anchor showing; an offset
            // of 200 leaves none, and the anchor is a long way above the
            // viewport's top edge.
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    clipbox { width: 300px; height: 100px; flex-direction: column; background: #204060 }
                    strip { flex-direction: column; flex-shrink: 0 }
                    sized { width: 200px; height: 80px; flex-shrink: 0; background: #eceff4 }
                    """));
        }

        @Override
        public void start(Host host) {
            onStart.accept(host);
        }

        @Override
        public void stop() {
            // Nothing: every assertion here is made while the loop is still up.
        }
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

    /// A zero-delay timer per turn, scheduled on the loop so it cannot outlive a
    /// run bounded by `--frames`.
    private static void afterTurns(Host host, int turns, Runnable action) {
        if (turns <= 0) {
            action.run();
            return;
        }
        host.after(Duration.ZERO, () -> afterTurns(host, turns - 1, action));
    }

    /// Opens a popup by `id`, scrolls the strip by `offset`, and reports whether
    /// the popup was still open four frames later.
    ///
    /// Four turns: past the frame the binding asked for, and past the
    /// re-placement at the end of it.
    private boolean survivesAScrollOf(float offset) {
        var opened = new boolean[1];
        var stillOpen = new boolean[1];
        var scrolled = Property.of(0f);
        var root = new Clipped(new Scrolled(scrolled, new Sized("target")));

        Goldberry.launch(
                new TestApp(
                        root,
                        host -> afterTurns(host, 2, () -> {
                            var popup = host.popup(new Sized("menu"), "target", Placement.BELOW)
                                    .orElse(null);
                            if (popup == null) {
                                Goldberry.stop();
                                return;
                            }
                            opened[0] = true;
                            scrolled.set(offset);
                            afterTurns(host, 4, () -> {
                                stillOpen[0] = popup.isOpen();
                                Goldberry.stop();
                            });
                        })),
                new String[] {"--size=400x300", "--frames=400"});

        assertTrue(opened[0], "the popup never opened, so there is nothing to say about it");
        return stillOpen[0];
    }

    /// The case the whole decision turns on. A menu left pointing at a widget
    /// that is no longer drawn is the defect; the placement would clamp it back
    /// into the work area beside something it does not belong to, which is the
    /// `pin` behaviour [ADR-0433] rejects.
    @Test
    @Timeout(20)
    @DisplayName("a popup closes when its anchor scrolls out of the viewport that clips it")
    void closesWhenTheAnchorLeavesItsViewport() {
        assertFalse(survivesAScrollOf(200), "the anchor was entirely above the viewport and the menu stayed open");
    }

    /// **Partly visible is visible.** The threshold is an intersection and not a
    /// containment: a menu hanging off the last twenty pixels of a row still
    /// points at something the user can see, and closing it there would make a
    /// small scroll destructive.
    @Test
    @Timeout(20)
    @DisplayName("a popup survives a scroll that leaves part of the anchor showing")
    void survivesWhileSomeOfTheAnchorShows() {
        assertTrue(survivesAScrollOf(60), "20 logical pixels of the anchor were still drawn and the menu closed");
    }

    /// And it **follows** while it survives, which is [ADR-0270]'s promise and
    /// the thing the new rule must not have broken.
    @Test
    @Timeout(20)
    @DisplayName("a popup still follows an anchor that is only partly clipped")
    void followsWhileTheAnchorIsPartlyClipped() {
        var before = new LogicalPoint[1];
        var after = new LogicalPoint[1];
        var scrolled = Property.of(0f);
        var root = new Clipped(new Scrolled(scrolled, new Sized("target")));

        Goldberry.launch(
                new TestApp(
                        root,
                        host -> afterTurns(host, 2, () -> {
                            var popup = host.popup(new Sized("menu"), "target", Placement.BELOW)
                                    .orElse(null);
                            if (popup == null) {
                                Goldberry.stop();
                                return;
                            }
                            before[0] = popup.offset();
                            scrolled.set(60f);
                            afterTurns(host, 4, () -> {
                                after[0] = popup.offset();
                                Goldberry.stop();
                            });
                        })),
                new String[] {"--size=400x300", "--frames=400"});

        assertNotNull(before[0], "the popup never opened, so there is nothing to say about it");
        assertNotNull(after[0], "the popup closed, and this scroll was supposed to leave it open");
        assertEquals(
                before[0].y() - 60,
                after[0].y(),
                0.5,
                "the anchor was drawn 60px higher and the menu should have gone with it");
    }

    /// A popup placed against a **rectangle** is not closed, because it was never
    /// following anything: the rectangle is all there ever was, and there is
    /// nothing to re-resolve and nothing to discover has gone. The same guard
    /// [ADR-0270] put on the following, seen from the other end — and the honest
    /// limit of [ADR-0433], which is a rule about anchors that have names.
    @Test
    @Timeout(20)
    @DisplayName("a popup anchored to a rectangle is left alone when the widget under it scrolls away")
    void aRectangleAnchoredPopupIsNotClosed() {
        var opened = new boolean[1];
        var stillOpen = new boolean[1];
        var scrolled = Property.of(0f);
        var root = new Clipped(new Scrolled(scrolled, new Sized("target")));

        Goldberry.launch(
                new TestApp(
                        root,
                        host -> afterTurns(host, 2, () -> {
                            var popup = host.popup(new Sized("menu"), LogicalRect.of(0, 0, 200, 80), Placement.BELOW)
                                    .orElse(null);
                            if (popup == null) {
                                Goldberry.stop();
                                return;
                            }
                            opened[0] = true;
                            scrolled.set(200f);
                            afterTurns(host, 4, () -> {
                                stillOpen[0] = popup.isOpen();
                                Goldberry.stop();
                            });
                        })),
                new String[] {"--size=400x300", "--frames=400"});

        assertTrue(opened[0], "the popup never opened, so there is nothing to say about it");
        assertTrue(stillOpen[0], "nothing was following anything, so nothing should have closed");
    }

    /// The stack above goes too.
    ///
    /// A submenu is anchored to a rectangle inside the menu it came from, so it
    /// has no name to be re-resolved and would not notice its root going. Left
    /// alone it would be a panel of commands floating over nothing — the orphan
    /// [ADR-0433] closes the stack to avoid.
    @Test
    @Timeout(20)
    @DisplayName("closing a popup whose anchor left takes the popups opened after it")
    void theStackAboveGoesWithIt() {
        var opened = new boolean[1];
        var rootOpen = new boolean[1];
        var branchOpen = new boolean[1];
        var scrolled = Property.of(0f);
        var root = new Clipped(new Scrolled(scrolled, new Sized("target")));

        Goldberry.launch(
                new TestApp(
                        root,
                        host -> afterTurns(host, 2, () -> {
                            var menu = host.popup(new Sized("menu"), "target", Placement.BELOW)
                                    .orElse(null);
                            if (menu == null) {
                                Goldberry.stop();
                                return;
                            }
                            // A submenu's shape: against a rectangle, opened while the
                            // menu it hangs off is already up.
                            var branch = host.popup(
                                            new Sized("branch"), LogicalRect.of(40, 40, 200, 24), Placement.AFTER)
                                    .orElse(null);
                            if (branch == null) {
                                Goldberry.stop();
                                return;
                            }
                            opened[0] = true;
                            scrolled.set(200f);
                            afterTurns(host, 4, () -> {
                                rootOpen[0] = menu.isOpen();
                                branchOpen[0] = branch.isOpen();
                                Goldberry.stop();
                            });
                        })),
                new String[] {"--size=400x300", "--frames=400"});

        assertTrue(opened[0], "one of the two popups never opened, so there is nothing to say about them");
        assertFalse(rootOpen[0], "the anchored menu should have closed with its anchor");
        assertFalse(branchOpen[0], "and the submenu standing on it should not have outlived it");
    }
}
