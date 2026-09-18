package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// `onPointerEnter` and `onPointerExit` on [Attributes] — `docs/gaps.md` G33,
/// [ADR-0327].
///
/// The router is driven directly, as [RehoverTest] does, because what is under
/// test is the enter/exit derivation and not a window or a paint.
class HoverHookTest {

    /// An ordinary node: not a [Handles], which is the whole point — the hook has
    /// to reach a widget that implements no input interface at all.
    private record Plain(String name, List<Widget> kids, Attributes attributes)
            implements Widget.Leaf, Attributed<Plain>, Styled {

        Plain(String name, Widget... kids) {
            this(name, List.of(kids), Attributes.NONE.id(name));
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Plain withAttributes(Attributes value) {
            return new Plain(name, kids, value);
        }

        @Override
        public String cssType() {
            return name;
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
        public Object key() {
            return attributes.key();
        }
    }

    /// A node that is both attributed and a handler, so the two paths can be seen
    /// not to shadow each other.
    private record Button(String name, List<String> log, Attributes attributes)
            implements Widget.Leaf, Attributed<Button>, Styled, Handles {

        @Override
        public Button withAttributes(Attributes value) {
            return new Button(name, log, value);
        }

        @Override
        public void onPointer(PointerEvent event) {
            log.add("handles:" + event.kind());
        }

        @Override
        public String cssType() {
            return name;
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
        public Object key() {
            return attributes.key();
        }
    }

    private final List<String> log = new ArrayList<>();
    private PointerRouter router;
    private ElementTree tree;

    private Element find(String id) {
        return find(tree.root(), id);
    }

    private static Element find(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// The window is 100x100: `row` is the left half and holds `left` and
    /// `right`, a quarter each; `outside` is the right half and holds nothing.
    private void mount(Widget root) {
        tree = new ElementTree(root);
        router = new PointerRouter();
        router.updateRegions(List.of(
                HitTest.Region.of(find("window"), 0, 0, 100, 100),
                HitTest.Region.of(find("row"), 0, 0, 50, 100),
                HitTest.Region.of(find("left"), 0, 0, 25, 100),
                HitTest.Region.of(find("right"), 25, 0, 25, 100),
                HitTest.Region.of(find("outside"), 50, 0, 50, 100)));
    }

    private Widget row(Attributes rowAttributes) {
        return new Plain(
                "window",
                List.of(
                        new Plain("row", List.of(new Plain("left"), new Plain("right")), rowAttributes),
                        new Plain("outside")),
                Attributes.NONE.id("window"));
    }

    @BeforeEach
    void setUp() {
        log.clear();
    }

    @Test
    @DisplayName("entering a node runs its hook, and leaving runs the other")
    void enterAndExitAreBothRaised() {
        mount(row(
                Attributes.NONE.id("row").onPointerEnter(() -> log.add("enter")).onPointerExit(() -> log.add("exit"))));

        router.pointerMoved(10, 50);
        assertEquals(List.of("enter"), log);

        router.pointerMoved(75, 50);
        assertEquals(List.of("enter", "exit"), log);
    }

    @Test
    @DisplayName("the hook is about the subtree: moving between children raises nothing")
    void movingWithinTheSubtreeIsSilent() {
        mount(row(
                Attributes.NONE.id("row").onPointerEnter(() -> log.add("enter")).onPointerExit(() -> log.add("exit"))));

        router.pointerMoved(10, 50);
        router.pointerMoved(30, 50);
        router.pointerMoved(40, 50);

        // One arrival, because `row` never stopped being hovered: `:hover` is the
        // ancestor chain, and so is this.
        assertEquals(List.of("enter"), log);
        assertEquals("right", router.hovered().id());
    }

    @Test
    @DisplayName("a node that carries no hook is simply not told")
    void aNodeWithoutHooksIsUntouched() {
        mount(row(Attributes.NONE.id("row")));

        router.pointerMoved(10, 50);
        router.pointerMoved(75, 50);

        assertTrue(log.isEmpty());
    }

    @Test
    @DisplayName("the hook consumes nothing, so a press inside still lands")
    void theHookDoesNotConsume() {
        var button = new Button(
                "left",
                log,
                Attributes.NONE
                        .id("left")
                        .onPointerEnter(() -> log.add("enter"))
                        .onPointerExit(() -> log.add("exit")));
        tree = new ElementTree(new Plain(
                "window",
                List.of(
                        new Plain("row", List.of(button, new Plain("right")), Attributes.NONE.id("row")),
                        new Plain("outside")),
                Attributes.NONE.id("window")));
        router = new PointerRouter();
        router.updateRegions(List.of(
                HitTest.Region.of(find("window"), 0, 0, 100, 100),
                HitTest.Region.of(find("row"), 0, 0, 50, 100),
                HitTest.Region.of(find("left"), 0, 0, 25, 100),
                HitTest.Region.of(find("right"), 25, 0, 25, 100),
                HitTest.Region.of(find("outside"), 50, 0, 50, 100)));

        router.pointerMoved(10, 50);
        router.pointerPressed(10, 50, PointerEvent.Button.PRIMARY, 1);
        router.pointerReleased(10, 50, PointerEvent.Button.PRIMARY, 1);

        assertTrue(log.contains("enter"), log.toString());
        assertTrue(log.contains("handles:PRESSED"), log.toString());
        assertTrue(log.contains("handles:CLICKED"), log.toString());
        // And the widget's own handler heard the arrival too — the hook is beside
        // it rather than instead of it.
        assertTrue(log.contains("handles:ENTERED"), log.toString());
        assertFalse(log.contains("exit"), log.toString());
    }

    /// The router lets go of an element that leaves the tree ([ADR-0303]), and the
    /// walk that does it is the same one that raises these — so a node unmounted
    /// under the pointer hears its exit on the frame the router notices, rather
    /// than never. That is what lets a hover-hold timer be cancelled from the
    /// hook rather than from two places.
    @Test
    @DisplayName("a node unmounted under the pointer still hears its exit")
    void anUnmountedSubtreeIsToldItLostThePointer() {
        mount(row(
                Attributes.NONE.id("row").onPointerEnter(() -> log.add("enter")).onPointerExit(() -> log.add("exit"))));

        router.pointerMoved(10, 50);
        assertEquals(List.of("enter"), log);

        // The row goes, and the next frame hands over the regions it painted --
        // which is what `updateRegions` is and where `rehover` runs from.
        tree.update(new Plain("window", new Plain("outside")));
        tree.flush();
        router.updateRegions(List.of(
                HitTest.Region.of(find("window"), 0, 0, 100, 100), HitTest.Region.of(find("outside"), 50, 0, 50, 100)));

        assertEquals(List.of("enter", "exit"), log);
    }

    /// Where the line is, once [ADR-0317]'s rule reached this walk: the widget's
    /// own `onPointer` is **not** called on a node that has left the tree — its
    /// state is disposed and `setState` throws — while the hook beside it still
    /// runs, because that one is the application's half of a pair it opened on
    /// the enter and nothing else will close it.
    @Test
    @DisplayName("an unmounted node's hook runs and its widget's own handler does not")
    void theHookOutlivesTheWidgetsHandler() {
        var button = new Button(
                "left",
                log,
                Attributes.NONE
                        .id("left")
                        .onPointerEnter(() -> log.add("enter"))
                        .onPointerExit(() -> log.add("exit")));
        mount(new Plain(
                "window",
                List.of(
                        new Plain("row", List.of(button, new Plain("right")), Attributes.NONE.id("row")),
                        new Plain("outside")),
                Attributes.NONE.id("window")));

        router.pointerMoved(10, 50);
        log.clear();

        tree.update(new Plain("window", new Plain("outside")));
        tree.flush();
        router.updateRegions(List.of(
                HitTest.Region.of(find("window"), 0, 0, 100, 100), HitTest.Region.of(find("outside"), 50, 0, 50, 100)));

        assertEquals(List.of("exit"), log, "the hook is owed an exit; the disposed widget is not");
    }

    @Test
    @DisplayName("a hook is carried through every wither on Attributes")
    void withersKeepTheHooks() {
        Runnable in = () -> {};
        Runnable out = () -> {};
        var attributes = Attributes.NONE.onPointerEnter(in).onPointerExit(out);

        for (var carried : List.of(
                attributes.id("x"),
                attributes.classes("a", "b"),
                attributes.key(7),
                attributes.tooltip("t"),
                attributes.contextMenu("m"),
                attributes.name("n"))) {
            assertSame(in, carried.onPointerEnter());
            assertSame(out, carried.onPointerExit());
        }
    }
}
