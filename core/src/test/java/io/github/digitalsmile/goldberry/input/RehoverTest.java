package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// What the router does when the element under a **still** pointer leaves the
/// tree — [ADR-0303].
///
/// [FocusTrapTest] is the same test for the keyboard, and this file exists
/// because the pointer had the identical hole with a different symptom: a
/// tooltip that stayed open over content that had been replaced, until the user
/// moved the mouse.
///
/// The router is driven directly, as [PointerRouterTest] does, because the rule
/// under test is about `updateRegions` and the element tree rather than about a
/// window or a paint.
class RehoverTest {

    /// What the leaves were told, in order. Static because the widgets are built
    /// from a `State`, which a test cannot hand an enclosing instance to.
    private static final List<String> LOG = new ArrayList<>();

    /// A leaf that records the pointer events it is told about, and runs
    /// `onExit` when it is told the pointer left.
    ///
    /// The action is what makes this more than a log: the widget that found
    /// [ADR-0317]'s remaining hole reacts to `EXITED` by calling `setState`, and
    /// nothing under it can be tested without one that does.
    private static final class Item implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final Runnable onExit;

        Item(String name) {
            this(name, () -> {});
        }

        Item(String name, Runnable onExit) {
            this.name = name;
            this.onExit = onExit;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public void onPointer(PointerEvent event) {
            LOG.add(name + ":" + event.kind());
            if (event.kind() == PointerEvent.Kind.EXITED) {
                onExit.run();
            }
        }
    }

    /// A box that holds others, so there is an ancestor chain to check.
    ///
    /// It handles the pointer as well, so that an ancestor's `EXITED` shows up
    /// in [#LOG] — a chain is told one element at a time, and what a dead
    /// *container* is told is the half a leaf cannot show.
    private static final class Box implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final List<Widget> children;

        Box(String name, Widget... children) {
            this.name = name;
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public void onPointer(PointerEvent event) {
            LOG.add(name + ":" + event.kind());
        }
    }

    /// What `doomed` does when it is told the pointer left it.
    ///
    /// Static, and read when it runs rather than when the widget is built, for
    /// [#LOG]'s reason: the widgets come from a `State` a test cannot reach into.
    /// The default is the sequence [ADR-0303] said could not arise — a handler
    /// that calls `setState` on the state that is going away.
    private static Consumer<DoomedState> onDoomedExit = DoomedState::bump;

    /// The panel's own state, which dies with the panel.
    static final class Doomed implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new DoomedState();
        }
    }

    static final class DoomedState extends State<Doomed> {

        private int exits;

        @Override
        public Widget build(BuildContext context) {
            return new Item("doomed", this::exited);
        }

        private void exited() {
            onDoomedExit.accept(this);
        }

        /// What a screen does when the pointer leaves the thing it was showing
        /// something about: it changes, and says so.
        void bump() {
            setState(() -> exits++);
        }
    }

    /// A screen whose second half can be taken away, which is what a click that
    /// rebuilds the tree does.
    static final class Screen implements Widget.Stateful {

        static ScreenState live;

        @Override
        public State<?> createState() {
            return new ScreenState();
        }
    }

    static final class ScreenState extends State<Screen> {

        private boolean showing = true;

        @Override
        protected void initState() {
            Screen.live = this;
        }

        @Override
        public Widget build(BuildContext context) {
            var kids = new ArrayList<Widget>(2);
            kids.add(new Item("keeper"));
            if (showing) {
                kids.add(new Box("panel", new Doomed()));
            }
            return new Box("window", kids.toArray(Widget[]::new));
        }

        void hide() {
            setState(() -> showing = false);
        }
    }

    private PointerRouter router;
    private ElementTree tree;
    private final List<String> pointings = new ArrayList<>();

    /// The window is 100x100. `keeper` is the left half, `panel` the right, and
    /// `doomed` fills the panel.
    private void paint(boolean withPanel) {
        var regions = new ArrayList<HitTest.Region>();
        regions.add(HitTest.Region.of(find("window"), 0, 0, 100, 100));
        regions.add(HitTest.Region.of(find("keeper"), 0, 0, 50, 100));
        if (withPanel) {
            regions.add(HitTest.Region.of(find("panel"), 50, 0, 50, 100));
            regions.add(HitTest.Region.of(find("doomed"), 50, 0, 50, 100));
        }
        router.updateRegions(regions);
    }

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

    @BeforeEach
    void setUp() {
        LOG.clear();
        onDoomedExit = DoomedState::bump;
        router = new PointerRouter();
        tree = new ElementTree(new Screen());
        paint(true);
        router.onPointingChanged(() -> pointings.add(
                router.hovered() == null ? "(none)" : router.hovered().id()));
    }

    /// The click that rebuilds: the panel goes, the tree settles, and the window
    /// hands over the regions of the frame it just painted.
    private void removeThePanel() {
        Screen.live.hide();
        tree.flush();
        paint(false);
    }

    @Test
    @DisplayName("a hovered element that leaves the tree is let go of")
    void anUnmountedHoverIsReleased() {
        router.pointerMoved(75, 50);
        assertEquals("doomed", router.hovered().id());
        var doomed = router.hovered();

        removeThePanel();

        // Rule one of ADR-0180, now for the pointer as well: the router never
        // holds an element that is not in the tree.
        assertFalse(doomed.isMounted());
        assertTrue(router.hovered() == null || router.hovered().isMounted());
    }

    @Test
    @DisplayName("what the pointer is over now is found from the frame just painted")
    void theNewHoverComesFromTheNewRegions() {
        router.pointerMoved(75, 50);

        removeThePanel();

        // Nothing is painted at (75, 50) any more except the window itself, so
        // that is the honest answer -- resolved against the regions of the frame
        // the user is looking at, not the ones that were there when the pointer
        // last moved.
        assertSame(find("window"), router.hovered());
    }

    @Test
    @DisplayName("the pointing listeners are told, which is what closes a tooltip")
    void listenersAreNotified() {
        router.pointerMoved(75, 50);
        pointings.clear();

        removeThePanel();

        // The whole bug: `restate()` runs on every frame and deliberately says
        // nothing, so before this there was no notification between the click and
        // the user's next mouse move -- and the tooltip stayed up for all of it.
        assertEquals(List.of("window"), pointings);
    }

    @Test
    @DisplayName(":hover is taken off the chain that went away")
    void theHoverPseudoClassIsCleared() {
        router.pointerMoved(75, 50);
        var window = find("window");
        assertTrue(window.hasState(PseudoClass.HOVER));

        removeThePanel();

        // The window is still under the pointer, so it keeps `:hover`. The panel
        // is gone and cannot be asked, which is the point of checking the
        // ancestor that survived.
        assertTrue(window.hasState(PseudoClass.HOVER));
        assertTrue(find("keeper") == null || !find("keeper").hasState(PseudoClass.HOVER));
    }

    @Test
    @DisplayName("a hover that survives the rebuild is left alone")
    void aSurvivingHoverIsNotDisturbed() {
        router.pointerMoved(25, 50);
        var keeper = router.hovered();
        pointings.clear();
        LOG.clear();

        removeThePanel();

        // `keeper` is reconciled in place, so nothing entered and nothing exited.
        // A frame hook that re-hit-tested unconditionally would emit a pair of
        // events here for a pointer that has not moved.
        assertSame(keeper, router.hovered());
        assertEquals(List.of(), pointings);
        assertEquals(List.of(), LOG);
    }

    /// The crash [ADR-0303] ruled out, and the reason its "safe by construction"
    /// had to be withdrawn: `markNeedsBuild` is indeed a no-op on an unmounted
    /// element, but `State.setState` throws one line before it, so a widget that
    /// reacts to `EXITED` the way any ordinary one does took the frame down.
    @Test
    @DisplayName("an element that has left the tree is not told the pointer exited it")
    void theDeadAreNotToldTheyExited() {
        router.pointerMoved(75, 50);
        var doomed = router.hovered();
        LOG.clear();

        // Nothing here may throw: `doomed` is unmounted by the rebuild and the
        // frame hook then re-resolves what the pointer is over.
        removeThePanel();

        assertFalse(doomed.isMounted());
        // Not "it was told and survived": there is nobody left to tell. An
        // unmounted element has been disposed -- its state's `dispose` has run
        // and its bindings are closed -- so the notification has no audience and
        // no effect it could have had ([ADR-0317]).
        assertEquals(List.of(), LOG);
    }

    /// The other half of the same guard. The elements of a chain are told one at
    /// a time, so "is it still mounted" has to be asked at the moment of telling
    /// rather than once for the move: the handler that is running is free to
    /// take the rest of the chain with it, which is what closing a panel from
    /// inside it does.
    @Test
    @DisplayName("a handler that unmounts its own container spares the container the news")
    void anAncestorUnmountedMidDispatchIsSkipped() {
        onDoomedExit = _ -> {
            Screen.live.hide();
            tree.flush();
        };
        router.pointerMoved(75, 50);
        var panel = find("panel");
        LOG.clear();

        router.pointerMoved(25, 50);

        assertFalse(panel.isMounted(), "the handler was supposed to take the panel away");
        // `doomed` was live when it was told and `keeper` is live now; `panel`
        // died between the two, one step into the same loop. The move itself
        // still travels the new chain, which is why this filters rather than
        // comparing the whole log.
        assertEquals(
                List.of("doomed:EXITED", "keeper:ENTERED"),
                LOG.stream()
                        .filter(entry -> entry.endsWith("ENTERED") || entry.endsWith("EXITED"))
                        .toList());
    }

    @Test
    @DisplayName("a pointer that is not in the window drops the hover rather than guessing")
    void anAbsentPointerDropsTheHover() {
        router.pointerMoved(75, 50);
        // The platform says the pointer left, but capture and the hit-test
        // snapshot outlive that -- so `hovered` is null already and the rebuild
        // has nothing to do.
        router.pointerExited();
        assertNull(router.hovered());

        removeThePanel();

        assertNull(router.hovered());
    }
}
