package dev.goldberry.offscreen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.frame.FrameSequence;
import dev.goldberry.image.Image;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;

/// One tree, mounted once, photographed as many times as you like.
///
/// ```java
/// try (var strip = Offscreen.of(400, 120)
///         .stylesheets(Controls.stylesheets(Theme.NORD_DARK))
///         .strip(new Banner("saved"))) {
///     var frames = new ArrayList<Image>();
///     while (strip.isAnimating() && strip.frames() < 60) {
///         frames.add(strip.frame());
///         strip.advance(16);
///     }
/// }
/// ```
///
/// [Offscreen] is one call and one picture, and a caller wanting frame 3 of a
/// transition cannot get there from it: the clock it runs is virtual and private,
/// and the tree it mounts is unmounted before it returns. Rendering the same widget
/// four times at four settle times does not produce four frames of one animation
/// either — it produces four *first* frames, each from a tree that has just been
/// mounted, and a `spinner` at 48 ms is not the same picture as a spinner that has
/// been spinning for 48 ms. What is wanted is a lifetime, and this is it.
///
/// ## What lives between frames, and what does not
///
/// **Everything above the pixels lives.** The element tree is mounted once — that
/// is the whole point, and it is what makes a `State`'s animation, a scroll offset
/// and a `text-area`'s learnt width carry from one frame to the next. The render
/// tree is retained, so Yoga re-lays out only what moved; the renderer is kept,
/// so its shaping cache holds every paragraph it has already shaped; the router
/// is kept, so a `Measured` widget is told its region changed rather than told it
/// again; and the clock is the one object the caller actually drives.
///
/// **The buffer does not.** Each [#frame()] allocates its own, and that is not
/// tidiness: an [Image] handed back is a *view* over the pixels it was rendered
/// into and never a copy of them, so a strip that reused one buffer
/// would repaint every picture it had already given away. Ten frames of a
/// 400&times;120 strip is 1.9 MB, and the alternative is one buffer and ten
/// identical images.
///
/// ## What it is not
///
/// Not a window, and not a frame loop. Nothing here asks for the next frame, waits
/// for a refresh or reads a real clock: a strip advances exactly when it is told
/// to and by exactly as much, which is what makes the tenth frame of a transition
/// the same picture on every machine and in every run. [#isAnimating()] is how a
/// caller finds out there is any point taking another.
///
/// Confined to the thread it was opened on, as every render is. Must be closed: a
/// mounted tree holds `State` objects with `dispose` hooks and a retained render
/// tree holds native Yoga nodes.
///
/// Read more: [Testing an application](https://goldberry.dev/docs/guide/testing.html#pictures).
public final class Filmstrip implements AutoCloseable {

    private final PhysicalSize size;

    private final DisplayScale scale;

    private final PixelFormat format;

    private final int background;

    /// The book this strip opened for itself, or null when the caller's own was
    /// handed over — the one object whose ownership [#close()] has to get right.
    private final @Nullable Fonts ownFonts;

    private final WidgetRenderer renderer;

    private final Clock.Virtual clock;

    private final ElementTree tree;

    private final RenderTree render;

    private final PointerRouter router;

    private final FrameSequence sequence;

    private int frames;

    private boolean closed;

    /// What the last pass captured — where everything was laid out, in paint
    /// order.
    private List<HitTest.Region> regions = List.of();

    /// Opens a strip over a mounted tree.
    ///
    /// Package-private: [Offscreen#strip(Widget)] is the door, because everything
    /// this needs — the size, the scale, the stylesheets, the fonts, the background
    /// — is already a knob on the builder, and a second copy of those six setters
    /// would be two builders to keep in step.
    Filmstrip(
            PhysicalSize size,
            DisplayScale scale,
            PixelFormat format,
            int background,
            @Nullable Fonts ownFonts,
            WidgetRenderer renderer,
            Clock.Virtual clock,
            Widget root) {

        this(size, scale, format, background, ownFonts, renderer, clock, new PointerRouter(), new ElementTree(root));
    }

    /// Opens a strip over a tree the caller mounted, routed by a router the
    /// caller made.
    ///
    /// What a [Session] is built on: its tree has a host, and the host answers
    /// focus and modality from the router, so both exist before this does. The
    /// strip owns them from here on and unmounts the tree when it closes.
    Filmstrip(
            PhysicalSize size,
            DisplayScale scale,
            PixelFormat format,
            int background,
            @Nullable Fonts ownFonts,
            WidgetRenderer renderer,
            Clock.Virtual clock,
            PointerRouter router,
            ElementTree tree) {

        this.size = size;
        this.scale = scale;
        this.format = format;
        this.background = background;
        this.ownFonts = ownFonts;
        this.renderer = renderer;
        this.clock = clock;
        this.tree = tree;
        this.render = RenderTree.create();
        this.router = router;
        this.sequence = FrameSequence.over(tree, render, router);
        try {
            mount();
        } catch (RuntimeException e) {
            // A constructor that threw half-way would leave a mounted tree, a
            // native Yoga tree and possibly a font book with nobody holding a
            // reference to close them -- and the caller has no strip to close.
            release(e);
            throw e;
        }
    }

    /// The pass that mounts the tree and tells it what it came out as, **without
    /// moving the clock**.
    ///
    /// [Offscreen#render] runs two of these with the settle time in between, and
    /// the second one is what this deliberately does not do. A strip's first frame
    /// is frame zero: every arriving widget at the start of its entrance, which for
    /// a still picture is the useless answer and for a strip is the only correct
    /// one.
    ///
    /// What the pass *is* still needed for is the feedback a laid-out frame
    /// gives: a `text-area` or a `masonry` learns its own width from the
    /// rectangles a laid-out frame produced, and a strip whose first frame had
    /// never fed them back would photograph the whole animation of a widget
    /// correcting a first guess it should never have been showing.
    private void mount() {
        pass();
    }

    /// Builds, lays out and captures the regions, and paints nothing.
    ///
    /// The mount pass, and what a [Session] runs after each piece of input: a
    /// window paints a frame between two events, and the second event is
    /// answered against the rectangles that frame produced. The rectangles are
    /// what is needed, and they come out of the layout.
    void pass() {
        var buffer = PixelBuffer.allocate(size, format);
        var frame = Frame.over(buffer, scale);
        try {
            sequence.layOut(frame, renderer);
            regions = sequence.captureRegions(frame);
        } finally {
            // Nothing was painted into it, and it is still a Blend2D context with
            // an image behind it.
            frame.end();
        }
    }

    /// The picture at the current clock.
    ///
    /// One full paint — there is no previous buffer for a damaged one to be an
    /// optimization over, which is the same reason [Offscreen] paints in full.
    /// The build that precedes it picks up whatever the last frame's
    /// regions dirtied, so a widget that rearranged itself in response to its own
    /// measurements is photographed rearranged.
    ///
    /// @throws IllegalStateException if this strip has been closed
    public Image frame() {
        requireOpen();
        var buffer = PixelBuffer.allocate(size, format);
        var frame = Frame.over(buffer, scale);
        try {
            fillBackground(frame);
            sequence.layOut(frame, renderer);
            render.paint(frame);
        } finally {
            // Before a pixel is read: a buffer read from a context that has not
            // ended is half-drawn.
            frame.end();
        }
        // After the paint and after the join, exactly as a window does it: what the
        // regions describe is the frame that was drawn. A `Measured`
        // widget told about them here acts on the *next* frame, which is what makes
        // a strip's feedback arrive on the same schedule a window's does.
        regions = sequence.captureRegions(frame);
        frames++;
        return Image.of(buffer);
    }

    /// Moves the clock on by `millis` and returns this strip.
    ///
    /// Nothing is drawn and nothing is built: a clock nobody has read is a number,
    /// and the next [#frame()] is what turns it into a picture. Two advances with no
    /// frame between them are one advance, which is how a caller skips a boring
    /// stretch of a long transition cheaply.
    ///
    /// @throws IllegalArgumentException if negative — a clock that runs backwards
    ///         would put a transition into a state no frame loop can produce, and
    ///         a picture of that is not a picture of the animation
    /// @throws IllegalStateException if this strip has been closed
    public Filmstrip advance(int millis) {
        requireOpen();
        if (millis < 0) {
            throw new IllegalArgumentException("a strip advances forwards, and " + millis + " does not");
        }
        clock.advance(millis);
        return this;
    }

    /// Where the clock is, in milliseconds since the tree was mounted.
    public double nowMillis() {
        return clock.nowMillis();
    }

    /// How many pictures have been taken.
    public int frames() {
        return frames;
    }

    /// What the last pass captured.
    List<HitTest.Region> regions() {
        return regions;
    }

    ElementTree tree() {
        return tree;
    }

    PointerRouter router() {
        return router;
    }

    RenderTree render() {
        return render;
    }

    Clock.Virtual clock() {
        return clock;
    }

    boolean isClosed() {
        return closed;
    }

    /// Whether anything in the tree is still moving.
    ///
    /// The frame loop's own idle test, borrowed: a window asks this to decide whether to
    /// request another frame, and a strip asks it to decide whether another frame
    /// would show anything new. Answered from the last pass, so it is meaningful
    /// from the moment the strip is open — the mount pass is a pass.
    ///
    /// **Not a promise that the picture has settled.** A tree with no transition in
    /// it and a widget that moves on its own — a clock face, a chart streaming
    /// points — animates without CSS ever being involved, and this says false for
    /// it. A caller with an upper bound on frames wants both.
    public boolean isAnimating() {
        return renderer.isAnimating();
    }

    /// Unmounts the tree and releases everything the strip was keeping.
    ///
    /// Idempotent. The order is not tidiness: no frame is live
    /// by the time this runs, because [#frame()] ends its own, so what is left is
    /// the render tree before the element tree — a `State` that owns a `Font` closes
    /// it in `dispose`, and Blend2D is still holding it until the joins are done.
    ///
    /// Every [Image] already handed out stays valid: an image is a view over its own
    /// buffer and no two frames share one, so closing a strip invalidates none of
    /// its pictures.
    @Override
    public void close() {
        release(null);
    }

    /// [#close()]'s body, reachable from a constructor that is failing.
    ///
    /// `incoming` is what went wrong on the way in, or null for an ordinary close.
    /// A fault while releasing is suppressed onto it rather than thrown over it,
    /// because the reason the strip is being torn down is the one worth reading —
    /// and it is the constructor that rethrows, so this must not.
    ///
    /// Every step is attempted whatever the one before it did. A leaked Yoga node
    /// is better than a leaked Yoga node *and* an un-unmounted tree, which is the
    /// same judgement [Fonts#close()] makes about a leaked face.
    private void release(@Nullable RuntimeException incoming) {
        if (closed) {
            return;
        }
        closed = true;
        var failure = incoming;
        try {
            render.close();
        } catch (RuntimeException e) {
            failure = suppress(failure, e);
        }
        try {
            tree.unmount();
        } catch (RuntimeException e) {
            failure = suppress(failure, e);
        }
        if (ownFonts != null) {
            try {
                ownFonts.close();
            } catch (RuntimeException e) {
                failure = suppress(failure, e);
            }
        }
        if (incoming == null && failure != null) {
            throw failure;
        }
    }

    private static RuntimeException suppress(@Nullable RuntimeException failure, RuntimeException raised) {
        if (failure == null) {
            return raised;
        }
        failure.addSuppressed(raised);
        return failure;
    }

    private void fillBackground(Frame frame) {
        if (background != 0) {
            frame.fill(background);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this Filmstrip has been closed; its tree is unmounted");
        }
    }

    @Override
    public String toString() {
        return "Filmstrip[" + size + " at " + scale.factor() + "x, " + frames + " frame(s), " + clock.nowMillis() + "ms"
                + (closed ? ", closed" : "") + "]";
    }
}
