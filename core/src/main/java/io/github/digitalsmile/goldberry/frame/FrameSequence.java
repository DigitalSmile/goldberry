package io.github.digitalsmile.goldberry.frame;

import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;

/// The steps a frame runs, in the one order that is right, in one place.
///
/// Two things in this toolkit turn a widget tree into pixels — `Launcher`, for a
/// window, and
/// [io.github.digitalsmile.goldberry.offscreen.Offscreen],
/// for a buffer — and they ran the same list of steps in the same order from two
/// copies of it. Every one of those steps is there because leaving it out
/// produced a picture that was wrong in a way nobody noticed for weeks
/// (ADR-0284), and a second copy is a second place for one to go missing: the
/// golden harness's own copy was missing [ElementTree#flush], and the nine images
/// it committed showed an arrangement no window ever drew.
///
/// So the order lives here, and what differs between a window and a buffer stays
/// outside: damage, frame statistics, the HUD's stage timings and the model sweep
/// are the launcher's, and nothing in this class knows they exist (ADR-0423).
///
/// Not part of the published API. This is a seam between two callers inside
/// `:core`, and an application that wants a picture wants `Offscreen`.
///
/// ## What it does not own
///
/// The rasterization. A window paints the damaged rectangles because the backend
/// promises last frame's pixels are still there (ADR-0072) and a buffer has no
/// last frame to promise anything about, so the two callers paint differently on
/// purpose — and a full paint is one call with no ordering constraint around it,
/// which is nothing for a shared owner to protect. Extraction buys safety exactly
/// where order is load-bearing, and it is load-bearing in [#layOut] and in
/// [#captureRegions].
public final class FrameSequence {

    private final ElementTree tree;

    private final RenderTree render;

    private final PointerRouter router;

    /// Whether [#layOut] has ever run — see [#captureRegions].
    private boolean laidOut;

    private FrameSequence(ElementTree tree, RenderTree render, PointerRouter router) {
        this.tree = tree;
        this.render = render;
        this.router = router;
    }

    /// The sequence over one window's or one render's three objects.
    ///
    /// All three outlive a frame: the element tree holds state, the render tree is
    /// retained across frames (ADR-0069), and the router remembers what it last
    /// told each self-measuring widget. A sequence is therefore built once beside
    /// them rather than per frame.
    public static FrameSequence over(ElementTree tree, RenderTree render, PointerRouter router) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(render, "render");
        Objects.requireNonNull(router, "router");
        return new FrameSequence(tree, render, router);
    }

    /// Prepare, flush, render, lay out — the four steps before anything is drawn,
    /// and the whole reason this class exists.
    ///
    /// Each step is in front of the next one for a reason that is not taste:
    ///
    /// 1. **prepare** hands the tree this renderer's cascade *before* it is built,
    ///    because a build may ask about a custom property and a resolver handed
    ///    over afterwards would be a frame late (ADR-0254).
    /// 2. **flush** settles every `setState` since the last frame, once, however
    ///    many of them there were (ADR-0052). This is the call the golden harness
    ///    did not have.
    /// 3. **render** cascades the element tree into a box tree — the term ADR-0070
    ///    measured as the largest in a frame.
    /// 4. **update** reconciles the retained render tree against that description
    ///    and lays it out. One layout pass, and both the paint and the hit-test
    ///    snapshot read that one result (ADR-0069).
    ///
    /// @param renderer the renderer for this frame. Passed in rather than held,
    ///                 because a theme swap builds a new one (ADR-0067) and a
    ///                 sequence that cached it would paint the old colours
    /// @return when each step finished, for a caller that reports it
    public Stages layOut(Frame frame, WidgetRenderer renderer) {
        return layOut(frame, renderer, System.nanoTime());
    }

    /// [#layOut(Frame, WidgetRenderer)] for a caller whose frame started earlier
    /// than this call.
    ///
    /// The launcher's build budget begins before the model sweep that precedes the
    /// build, and a refactor that quietly moved that sweep out of the number would
    /// be a refactor that changed a measurement — so the start of the frame is the
    /// caller's to say.
    public Stages layOut(Frame frame, WidgetRenderer renderer, long beganAt) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(renderer, "renderer");

        renderer.prepare(tree);
        if (tree.needsBuild()) {
            tree.flush();
        }
        var builtAt = System.nanoTime();

        var boxes = renderer.render(tree);
        var styledAt = System.nanoTime();

        render.update(frame, boxes);
        var laidOutAt = System.nanoTime();
        laidOut = true;
        return new Stages(beganAt, builtAt, styledAt, laidOutAt);
    }

    /// Captures the laid-out rectangles and gives them to the router.
    ///
    /// Order again, and this one is [ADR-0119]: the window's own bounds go in
    /// *before* the regions, because a `Located` widget is told what clips it and
    /// "nothing clips me" has to resolve to a real rectangle.
    ///
    /// The router is not here for input. It is what delivers `Measured` to a widget
    /// that sizes itself from the region it was laid out into, so a caller that
    /// skipped this would photograph every `text-area` and `masonry` on its first
    /// guess — which is why an offscreen render, with no pointer anywhere near it,
    /// runs this step too.
    ///
    /// @return the capture, for a caller that keeps it. A menu opens under where
    ///         its button *was drawn* rather than where a fresh layout would put
    ///         it, and that is this list (ADR-0054)
    /// @throws IllegalStateException if nothing has been laid out yet: a capture of
    ///         a tree with no layout in it is a list of rectangles at the origin,
    ///         which is not an error anywhere further down
    public List<HitTest.Region> captureRegions(Frame frame, PointerRouter router) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(router, "router");
        if (!laidOut) {
            throw new IllegalStateException(
                    "the regions are captured from a laid-out tree, and layOut has not run on this sequence yet");
        }
        var regions = HitTest.capture(render);
        router.windowBounds(
                LogicalRect.of(0, 0, frame.size().width(), frame.size().height()));
        router.updateRegions(regions);
        return regions;
    }

    /// [#captureRegions(Frame, PointerRouter)] into the router this sequence was
    /// built over.
    public List<HitTest.Region> captureRegions(Frame frame) {
        return captureRegions(frame, router);
    }

    /// When each step of one [#layOut] finished, in `System.nanoTime` terms.
    ///
    /// Measured on every frame rather than behind a flag, because the stages are
    /// what a `hud` shows: a number on screen from a frame that happened to be
    /// traced would be a different frame's. Four `nanoTime` calls against a frame
    /// costing hundreds of microseconds is not a cost worth a branch (ADR-0146).
    ///
    /// @param beganAt   when the caller says the frame started
    /// @param builtAt   after prepare and flush
    /// @param styledAt  after the cascade and the box tree
    /// @param laidOutAt after the layout pass
    public record Stages(long beganAt, long builtAt, long styledAt, long laidOutAt) {

        public long buildNanos() {
            return builtAt - beganAt;
        }

        public long styleNanos() {
            return styledAt - builtAt;
        }

        public long layoutNanos() {
            return laidOutAt - styledAt;
        }
    }
}
