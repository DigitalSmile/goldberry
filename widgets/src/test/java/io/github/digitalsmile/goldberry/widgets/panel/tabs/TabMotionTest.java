package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A tab arriving and a tab leaving, on a clock a test drives.
///
/// The whole lifecycle is a function of the frame clock rather than a transition
/// ([ADR-0109]), which is what makes it testable at all: `clock.advance(80)` is
/// the frame at exactly half of a 160ms arrival, on every machine.
class TabMotionTest {

    private Clock.Virtual clock;
    private WidgetRenderer renderer;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        clock = Clock.virtual();
        renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, "")),
                TestFont.get())
                .clock(clock);
    }

    /// An application, in miniature: it holds the list of tabs and rebuilds when
    /// the list changes.
    ///
    /// The change is driven through `setState` rather than by replacing the
    /// strip's widget directly, because that is what an application does — a tab
    /// added or closed is a **structural** change, and the toolkit rebuilds a
    /// subtree when something it watches changes (ADR-0109).
    private record Harness(List<String> initial)
            implements io.github.digitalsmile.goldberry.widget.Widget.Stateful {

        @Override
        public io.github.digitalsmile.goldberry.widget.State<?> createState() {
            return new HarnessState();
        }
    }

    private static final class HarnessState
            extends io.github.digitalsmile.goldberry.widget.State<Harness> {

        private List<String> values;

        @Override
        protected void initState() {
            values = widget().initial();
        }

        void show(String... next) {
            setState(() -> values = List.of(next));
        }

        @Override
        public io.github.digitalsmile.goldberry.widget.Widget build(
                io.github.digitalsmile.goldberry.widget.BuildContext context) {
            return strip(values.toArray(String[]::new));
        }
    }

    /// The harness's state, for a test that wants to change the list.
    private static HarnessState stateOf(ElementTree tree) {
        return (HarnessState) tree.root().state().orElseThrow();
    }

    private static Tabs strip(String... values) {
        var tabs = new ArrayList<io.github.digitalsmile.goldberry.widget.Widget>();
        for (var value : values) {
            tabs.add(new Tab(value, value));
        }
        return new Tabs("a", tabs,
                null, ignored -> { }, ignored -> { }, null,
                io.github.digitalsmile.goldberry.widget.Attributes.NONE);
    }

    /// The headers a strip is currently drawing, by value — including any that are
    /// on their way out.
    private static List<String> drawn(ElementTree tree) {
        var found = new ArrayList<String>();
        collect(tree.root(), found);
        return found;
    }

    private static void collect(Element element, List<String> into) {
        if (element.widget() instanceof Tab tab) {
            into.add(tab.value());
        }
        element.children().forEach(child -> collect(child, into));
    }

    /// The opacity of the box drawn for `value`, which is what an arrival and a
    /// departure move.
    private static double opacityOf(Box box, String value, List<String> order) {
        var index = order.indexOf(value);
        // list → rule, then the headers.
        return box.children().getFirst().children().get(index + 1).opacity();
    }

    /// Nothing animates on the first build: a window opening should show its tabs,
    /// not play six arrivals at once.
    @Test
    @DisplayName("the tabs a strip opens with are simply there")
    void firstBuildDoesNotAnimate() {
        var tree = new ElementTree(new Harness(List.of("a", "b")));
        var box = renderer.render(tree);

        assertEquals(List.of("a", "b"), drawn(tree));
        assertEquals(1.0, opacityOf(box, "a", List.of("a", "b")));
        assertEquals(1.0, opacityOf(box, "b", List.of("a", "b")));
        assertFalse(renderer.isAnimating(), "and the loop goes back to sleep");
    }

    /// A tab added afterwards fades up over 160ms, and the loop stays awake for
    /// exactly that long.
    @Test
    @DisplayName("a tab added later arrives over its duration")
    void arrival() {
        var tree = new ElementTree(new Harness(List.of("a")));
        renderer.render(tree);

        stateOf(tree).show("a", "b");
        tree.flush();
        var atStart = renderer.render(tree);

        assertEquals(List.of("a", "b"), drawn(tree));
        assertEquals(0.0, opacityOf(atStart, "b", List.of("a", "b")),
                "it starts from nothing");
        assertTrue(renderer.isAnimating(), "and asks for the frames to finish");

        clock.advance(80);
        var halfway = renderer.render(tree);
        var partial = opacityOf(halfway, "b", List.of("a", "b"));
        assertTrue(partial > 0.4 && partial < 0.6, "half of 160ms is half way: " + partial);

        clock.advance(80);
        var done = renderer.render(tree);
        assertEquals(1.0, opacityOf(done, "b", List.of("a", "b")));

        // One more frame before the loop sleeps, and the reason is worth knowing:
        // whether a node animates is read *before* it is drawn, and drawing is
        // what advances the phase — so the frame that finishes an arrival still
        // reports itself as animating, and the frame after it does not.
        assertTrue(renderer.isAnimating());
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "and then the loop goes back to sleep");
    }

    /// The one the strip needs state for: the application has already dropped the
    /// tab, and it is still drawn — fading — until its departure is over.
    @Test
    @DisplayName("a closed tab is still drawn while it leaves, then dropped")
    void departure() {
        var tree = new ElementTree(new Harness(List.of("a", "b")));
        renderer.render(tree);

        // What the application does when `close` is answered: the tab is gone
        // from its list.
        stateOf(tree).show("a");
        tree.flush();
        var leaving = renderer.render(tree);

        assertEquals(List.of("a", "b"), drawn(tree),
                "still drawn, though the application no longer has it");
        assertEquals(1.0, opacityOf(leaving, "b", List.of("a", "b")), "starting from where it was");
        assertTrue(renderer.isAnimating());

        clock.advance(80);
        var halfway = renderer.render(tree);
        var partial = opacityOf(halfway, "b", List.of("a", "b"));
        assertTrue(partial > 0.4 && partial < 0.6, "half way out: " + partial);

        // Past the end: the tab asks to be dropped, which marks a rebuild — so it
        // goes on the *next* build rather than in the middle of this render.
        clock.advance(90);
        renderer.render(tree);
        tree.flush();
        renderer.render(tree);

        assertEquals(List.of("a"), drawn(tree), "and then it is gone");
        assertFalse(renderer.isAnimating(), "with nothing left asking for frames");
    }

    /// §1.7 asks for movement to be removed rather than shortened, so a tab under
    /// reduced motion is simply there — and the loop never wakes for it.
    @Test
    @DisplayName("reduced motion removes the animation rather than shortening it")
    void reducedMotion() {
        renderer.reducedMotion(true);
        var tree = new ElementTree(new Harness(List.of("a")));
        renderer.render(tree);

        stateOf(tree).show("a", "b");
        tree.flush();
        var box = renderer.render(tree);

        assertEquals(1.0, opacityOf(box, "b", List.of("a", "b")), "there, immediately");
    }
}
