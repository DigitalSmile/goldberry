package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// A real viewport, laid out and painted, for the tests in this package.
///
/// Every question about a `scroll` is a question about pixels — did the row
/// move, did it move the least it could, is it inside the clip — so these tests
/// cannot be run against a widget in isolation: they need a render tree, a
/// router holding the regions it produced, and a virtual clock, because a
/// programmatic scroll glides and a test that did not advance one would assert
/// on the frame before it arrived ([ADR-0363]).
///
/// Shared by [ScrollControllerTest] and [ScrollScopeTest], which ask the same
/// question — did the viewport move — through the two different handles on it,
/// and by `TourTest`, which asks it through a tour that was told nothing.
///
/// Public and outside its own test's file for that third caller: `settle()`
/// needs [ScrollGlide]'s duration, which is package-private, so the harness
/// cannot move to the shared test package and the callers have to come here.
public final class ScrollHarness implements AutoCloseable {

    /// Short enough that a thirty-row document overflows it several times over.
    public static final int VIEWPORT_HEIGHT = 100;

    public static final int WIDTH = 200;

    /// The clock the renderer reads, so [#settle] can land a glide without
    /// sleeping.
    final Clock.Virtual clock = Clock.virtual();

    /// The router holding this frame's regions — reachable because a wheel is
    /// one of the things that has to cancel a glide.
    final PointerRouter router = new PointerRouter();

    /// The renderer, for the tests that turn reduced motion on.
    final WidgetRenderer renderer;

    private final TestFrames.Target target;
    private final RenderTree render;
    private final ElementTree tree;

    public ScrollHarness(Widget root) {
        this(root, "");
    }

    public ScrollHarness(Widget root, String css) {
        target = TestFrames.of(WIDTH, VIEWPORT_HEIGHT, 1.0f, 0);
        renderer = new WidgetRenderer(
                        List.of(
                                Controls.baseStylesheet(),
                                Theme.NORD_DARK.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, css)),
                        TestFont.get())
                .clock(clock);
        tree = new ElementTree(root);
        render = RenderTree.create();
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, WIDTH, VIEWPORT_HEIGHT));
        // Twice: the first frame is the one that measures, and anything reading
        // its own geometry is right only from the second.
        frame();
        frame();
    }

    /// Every region the last frame produced, kept so that a test can hand a real
    /// one to something that takes regions rather than fabricating a rectangle
    /// with no clip on it.
    private List<HitTest.Region> regions = List.of();

    public void frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        regions = HitTest.capture(render);
        router.updateRegions(regions);
    }

    /// A frame, and another once a programmatic scroll's glide has had time to
    /// arrive (ADR-0363).
    public void settle() {
        frame();
        clock.advance(ScrollGlide.DURATION_MILLIS + 16);
        frame();
    }

    /// The hit-test region the last frame produced for `id` — the painted
    /// rectangle **and the clip that confines it**, which is the pair a reveal
    /// needs and the pair a fabricated rectangle cannot carry.
    public HitTest.Region region(String id) {
        var owner = element(id);
        for (var region : regions) {
            if (region.owner() == owner) {
                return region;
            }
        }
        throw new AssertionError("nothing was painted for id " + id);
    }

    /// Where the node with `id` is painted, in window coordinates.
    public LogicalRect rowRect(String id) {
        var found = new ArrayList<LogicalRect>();
        render.forEachPlacedBox(placed -> {
            if (placed.box().owner() instanceof Element element && id.equals(element.id())) {
                var m = placed.transform();
                var l = placed.layout();
                found.add(LogicalRect.of(
                        (float) (m.a() * l.left() + m.c() * l.top() + m.e()),
                        (float) (m.b() * l.left() + m.d() * l.top() + m.f()),
                        l.width(),
                        l.height()));
            }
        });
        assertEquals(1, found.size(), "expected exactly one node with id " + id);
        return found.getFirst();
    }

    /// The element with `id`, which is what a walk **up** from a target starts
    /// at — [ScrollScope]'s whole subject.
    public Element element(String id) {
        var found = element(tree.root(), id);
        if (found == null) {
            throw new AssertionError("no element with id " + id);
        }
        return found;
    }

    private static Element element(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = element(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public void close() {
        render.close();
        target.end();
    }
}
