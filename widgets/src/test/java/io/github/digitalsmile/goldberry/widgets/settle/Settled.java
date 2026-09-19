package io.github.digitalsmile.goldberry.widgets.settle;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// Runs a widget tree until its geometry stops changing, and says how long it
/// took — [ADR-0420].
///
/// ## Why this is a harness and not an assertion
///
/// [io.github.digitalsmile.goldberry.input.handler.Measured] has three rules and
/// the third is the one that bites: *what it triggers must not change what it
/// reports*. A widget that breaks it does not throw, log or draw anything wrong —
/// it asks for one more frame, forever. On a desktop that is a fan; in a test
/// suite that rendered one frame it is a pass.
///
/// So the check has to be a **fixed point**. Run the real loop — flush, render,
/// lay out, hand the router the rectangles — and keep running it until two
/// consecutive frames lay out identically. A widget that obeys rule 3 reaches
/// that in one distinct layout or two. One that does not never reaches it, and the
/// interesting failure is not "it is slow" but "frame 5 is frame 3 again", which
/// is what [#settle()] reports rather than merely timing out.
///
/// The signature is **layout rectangles only**. A transform is paint and cannot
/// feed back into layout, so a sweeping progress bar and a turning spinner are
/// still and this harness is not fooled into calling an animation an oscillation.
/// The clock is virtual and never advanced, for the same reason.
///
/// ## What it cannot see
///
/// A consumer that never reaches a frame here is not covered. `Measured` is
/// delivered by the **router**, from the regions a laid-out frame produced, so a
/// widget that needs a `Host` — a popup, a tour — has no window in a widget test
/// and is fed by hand or not at all. [ADR-0420] lists which.
public final class Settled implements AutoCloseable {

    /// How many frames a settling tree is allowed before this gives up.
    ///
    /// Twelve, where the slowest honest consumer in the catalog takes two. It is
    /// deliberately not tight: the failure this exists to catch is a tree that
    /// never settles, and a limit chosen close to the observed maximum turns a
    /// widget gaining one legitimate frame into a red test with a misleading name.
    public static final int LIMIT = 12;

    private final TestFrames.Target target;
    private final RenderTree render = RenderTree.create();
    private final PointerRouter router = new PointerRouter();
    private final WidgetRenderer renderer;
    private final ElementTree tree;

    private int consumers;

    private Settled(Widget root, List<Stylesheet> sheets, int width, int height, Host host) {
        target = TestFrames.of(width, height, 1.0f, 0);
        // Virtual and never advanced. An animation is a function of this, so
        // holding it still is what keeps a spinner out of the signature.
        renderer = new WidgetRenderer(sheets, TestFont.get()).clock(Clock.virtual());
        tree = host == null ? new ElementTree(root) : new ElementTree(root, host);
        router.focusRoot(tree.root());
        router.windowBounds(LogicalRect.of(0, 0, width, height));
    }

    /// A tree about to be driven to a fixed point.
    public static Settled of(Widget root, List<Stylesheet> sheets, int width, int height) {
        return new Settled(root, sheets, width, height, null);
    }

    /// The same, for a widget that will not build without somewhere to put a
    /// popup — `toast` holds a controller and asks its host for the overlay layer
    /// (ADR-0177), and a tree built without one never reaches a frame.
    public static Settled of(Widget root, List<Stylesheet> sheets, int width, int height, Host host) {
        return new Settled(root, sheets, width, height, host);
    }

    /// One frame, exactly as a window runs it.
    ///
    /// The last step is the one that matters: `Measured` is delivered by the
    /// router from the regions a laid-out frame produced — not by rendering and
    /// not by painting — so a loop that stopped at `render` would drive nothing.
    private List<String> frame() {
        tree.flush();
        render.update(target.frame(), renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
        var geometry = new ArrayList<String>();
        var found = new int[1];
        render.forEachPlacedBox(placed -> {
            var owner = placed.box().owner();
            var type = "?";
            if (owner instanceof Element element) {
                type = element.type();
                if (element.widget() instanceof Measured) {
                    found[0]++;
                }
            }
            var at = placed.layout();
            geometry.add(type + '@' + at.left() + ',' + at.top() + ' ' + at.width() + 'x' + at.height());
        });
        consumers = found[0];
        return List.copyOf(geometry);
    }

    /// How many `Measured` widgets were **placed** in the last frame — so how many
    /// the router had regions for and therefore notified.
    ///
    /// A caller asserting on [#settle()] alone can be fooled: a tree that stopped
    /// containing the consumer it was written for settles in one frame and passes.
    /// This is what tells the difference between *"nothing fed back"* and
    /// *"nothing was asked"*, and it is why four of the cases in
    /// [MeasuredFixedPointTest] are worth anything at all.
    public int consumers() {
        return consumers;
    }

    /// How many **distinct layouts** the tree went through before repeating one.
    ///
    /// `1` means the tree was right the first time: a second frame was rendered
    /// and laid out identically, so nothing fed anything back into geometry. `2` is
    /// what a consumer that genuinely reflows costs — one layout to be measured
    /// against and one to act on it.
    ///
    /// A `1` is therefore weaker evidence than it looks, and [#consumers()] is how
    /// a caller keeps it honest.
    ///
    /// @throws AssertionError if the tree is still moving after [#LIMIT] frames,
    ///         naming the period of the cycle when there is one and the boxes that
    ///         differ when there is not
    public int settle() {
        var seen = new ArrayList<List<String>>();
        for (var i = 0; i < LIMIT; i++) {
            var geometry = frame();
            if (!seen.isEmpty() && seen.getLast().equals(geometry)) {
                return i;
            }
            // A repeat that is *not* the previous frame is the real finding: the
            // layout is going round a loop rather than merely taking its time, and
            // no number of extra frames would help.
            var earlier = seen.indexOf(geometry);
            if (earlier >= 0) {
                throw new AssertionError("the layout oscillates with a period of " + (seen.size() - earlier)
                        + " frames: frame " + seen.size() + " is frame " + earlier + " again."
                        + " Something read geometry to decide a size (ADR-0117 rule 3, ADR-0420)."
                        + differences(seen.getLast(), geometry));
            }
            seen.add(geometry);
        }
        throw new AssertionError("the layout had not settled after " + LIMIT + " frames and is not cycling either,"
                + " so it is drifting rather than oscillating (ADR-0420)."
                + differences(seen.get(LIMIT - 2), seen.getLast()));
    }

    /// The first few boxes that differ between two frames, for the failure
    /// message. A diff of a whole tree is unreadable; the first three lines of one
    /// name the widget.
    private static String differences(List<String> before, List<String> after) {
        var out = new StringBuilder("\nWhat moved:");
        var shown = 0;
        for (var i = 0; i < Math.min(before.size(), after.size()) && shown < 3; i++) {
            if (!before.get(i).equals(after.get(i))) {
                out.append("\n  ").append(before.get(i)).append("\n  → ").append(after.get(i));
                shown++;
            }
        }
        if (before.size() != after.size()) {
            out.append("\n  and the tree changed size: ")
                    .append(before.size())
                    .append(" boxes → ")
                    .append(after.size());
        }
        return shown == 0 && before.size() == after.size() ? "" : out.toString();
    }

    @Override
    public void close() {
        render.close();
        target.end();
    }
}
