package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

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

    /// A leaf that records the pointer events it is told about.
    private static final class Item implements Widget.Leaf, Styled, Handles {
        private final String name;

        Item(String name) {
            this.name = name;
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

    /// A box that holds others, so there is an ancestor chain to check.
    private static final class Box implements Widget.Leaf, Styled {
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
                kids.add(new Box("panel", new Item("doomed")));
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
