package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.css.Affine;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout;
import io.github.digitalsmile.goldberry.natives.yoga.Overflow;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.natives.yoga.YogaConfig;
import java.util.Objects;
import java.util.function.Consumer;

/// The retained render tree — ADR-0004's third tree, kept between frames.
///
/// Hold one per window for the life of the window, hand it each frame's [Box]
/// tree, and paint it:
///
/// ```java
/// try (var render = RenderTree.create()) {
///     window.onPaint(frame -> {
///         render.update(frame, renderer.render(tree));
///         render.paint(frame);
///     });
/// }
/// ```
///
/// ## What it is for
///
/// [BoxPainter] built a Yoga tree, read it and freed it, every frame. That is
/// what [ADR-0053](../../../../../../book/src/adr/0053-the-render-tree-is-a-box-tree-for-now.md)
/// chose deliberately and said would need replacing, "and it should be made with
/// a measurement". The measurement is in
/// [ADR-0069](../../../../../../book/src/adr/0069-the-render-tree-is-retained.md).
///
/// Two things were being thrown away and rebuilt:
///
/// - **The measure callbacks.** An upcall stub is a confined `Arena` and a
///   `MethodHandle` bound into native code — 11 µs each, against 0.3 µs to call
///   through one, paid per text node per frame (ADR-0037).
/// - **Yoga's layout cache.** A tree that is freed has no cache; and a tree that
///   is kept but has every style re-set on it every frame has one that never
///   hits, because Yoga dirties a node when a style is *set*, not when it
///   changes. [RenderObject#apply] guards every setter for exactly that reason.
///
/// ## What it deliberately is not
///
/// It is **not** something a widget touches. Widgets describe themselves as
/// immutable boxes and this tree is reconciled against that description, so the
/// declarative contract is untouched and this layer could be deleted without
/// changing a single widget — the toolkit would simply be slower. That is also
/// what makes it testable: the same box tree through either path must produce the
/// same pixels, and a golden image says so.
///
/// Confined to the UI thread and must be closed: it owns Yoga nodes and the
/// native memory behind their measure callbacks.
public final class RenderTree implements AutoCloseable {

    private final YogaConfig config = YogaConfig.create();
    private final Thread owner = Thread.currentThread();

    private RenderObject root;

    /// The scale the config is currently set to.
    ///
    /// Yoga rounds computed positions onto a pixel grid and the grid is the
    /// config's, which is what makes a 1px border one crisp device pixel at any
    /// display scale. Changing it invalidates every position in the tree and
    /// Yoga has no way to be told so — a config change does not dirty a node —
    /// so a scale change rebuilds. That is a window being dragged to another
    /// monitor, not a frame.
    private float pointScale;

    private boolean closed;

    private RenderTree() {
    }

    public static RenderTree create() {
        return new RenderTree();
    }

    /// Reconciles this tree against `box` and lays it out to fill `frame`.
    ///
    /// Call once per frame, before [#paint] or [#forEachPlacedBox].
    public void update(Frame frame, Box box) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(box, "box");
        requireUsable();

        reconcile(box, frame.scale().factor());

        var size = frame.size();
        // Yoga skips a subtree with nothing dirty in it, so on a static frame
        // this call is close to free — which is the whole return on the guards
        // in `RenderObject.apply`.
        root.node().calculateLayout(size.width(), size.height());
    }

    /// Brings the retained tree in line with `box`, at `scale`. The half of
    /// [#update] that is not the layout pass, shared with [#measure].
    private void reconcile(Box box, float scale) {
        if (root != null && scale != pointScale) {
            // Every computed edge in the tree was rounded to the old grid. There
            // is no call that says "re-round everything", so the tree goes.
            root.close();
            root = null;
        }
        if (root == null) {
            config.setPointScaleFactor(scale);
            pointScale = scale;
            root = new RenderObject(config, box.text() != null);
        } else if (!root.accepts(box)) {
            // The root changed kind — a text root became a container, or the
            // application swapped in an unrelated tree.
            root.close();
            root = new RenderObject(config, box.text() != null);
        }
        root.update(box, config);
    }

    /// Lays the tree out against `available` and reports the size it wants, with
    /// no surface to paint into.
    ///
    /// **What a popup is sized by.** A menu is as tall as its items and as wide as
    /// its longest label, and neither is known until Yoga has been run — but a
    /// platform window has to be given a size *before* there is anything to paint
    /// into it. Every other layout pass in the toolkit starts from a [Frame],
    /// which is a surface that already exists.
    ///
    /// An available dimension of [Float#NaN] is Yoga's "undefined": the tree
    /// takes its content size along that axis. A number is a **bound and not a
    /// target** — the tree may come back smaller, and a paragraph wraps at it
    /// rather than overflowing. Passing undefined for the width of a tree with
    /// text in it therefore measures it as one unwrapped line, which is right for
    /// a menu item and wrong for a paragraph, and is the caller's choice to make.
    ///
    /// Two floats rather than a [LogicalSize], for exactly that reason: a size is
    /// a size, and refuses `NaN`. "Undefined" is not a size and this is the one
    /// place the toolkit needs to say it.
    ///
    /// A **definite** dimension will be filled by a root that grows, because
    /// `flex-grow` on a root with a definite main size is growth into it. So a
    /// caller measuring something that fills its window must leave the axis it
    /// fills undefined, or it measures the window rather than the content.
    ///
    /// This is a **real layout pass** and leaves the tree laid out at that size,
    /// so the [#update] that follows re-lays it out at the size the surface turned
    /// out to be. Yoga skips what has not changed, so the second pass is cheap
    /// rather than free.
    ///
    /// @param box       the tree to measure
    /// @param scale     the display scale to round edges against — the same one
    ///                  the eventual frame will use, or the measurement is against
    ///                  a different pixel grid than the paint
    /// @param availableWidth  the width to lay out in, or `NaN` for content size
    /// @param availableHeight the height to lay out in, or `NaN`
    /// @return the size the root came out as, in logical pixels
    public LogicalSize measure(Box box, DisplayScale scale,
            float availableWidth, float availableHeight) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(scale, "scale");
        requireUsable();

        reconcile(box, scale.factor());
        root.node().calculateLayout(availableWidth, availableHeight);
        var layout = root.node().layout();
        return new LogicalSize(layout.width(), layout.height());
    }

    /// Paints the tree, but **only inside `damage`**.
    ///
    /// The other half of damage tracking: `damage(frame)` says which region
    /// changed, and this rasterizes only that region instead of the whole frame.
    /// On a window where one control is hovering, that is the difference between
    /// repainting 960×640 and repainting 80×32.
    ///
    /// **Only correct where the buffer kept last frame's pixels**, which is a
    /// promise the backend makes and this class cannot check — ask
    /// [io.github.digitalsmile.goldberry.Window#canRepaintPartially()] and fall
    /// back to [#paint(Frame)] when it says no
    /// ([ADR-0072](../../../../../../book/src/adr/0072-a-partial-repaint-needs-a-promise.md)).
    ///
    /// An **empty** list means nothing changed and nothing is drawn at all, which
    /// is the same meaning it has for `present` and is the best case rather than
    /// a degenerate one: a window sitting still costs no rasterization.
    ///
    /// Several rectangles are clipped to **one** — their union. Blend2D's clip is
    /// a rectangle, so honouring them separately would mean one full pass per
    /// rectangle, and the tree walk is what a pass costs. `damage` already merges
    /// overlapping regions and gives up past a handful, so the union is close to
    /// the rectangles themselves in every case that reaches here.
    public void paint(Frame frame, java.util.List<DamageRect> damage) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(damage, "damage");
        if (damage.isEmpty()) {
            layersRepainted = 0;
            layersComposited = 0;
            return;
        }
        var bounds = damage.getFirst();
        for (var rect : damage) {
            bounds = union(bounds, rect);
        }
        var scale = frame.scale().factor();
        // Outward to whole logical pixels, because the clip is stated in logical
        // coordinates and a fractional edge would exclude the outermost row of
        // antialiasing inside the region that changed.
        var clip = Clip.of(
                Math.floor(bounds.x() / scale), Math.floor(bounds.y() / scale),
                Math.ceil(bounds.width() / scale) + 1, Math.ceil(bounds.height() / scale) + 1);
        frame.clipTo(clip.left(), clip.top(), clip.width(), clip.height());
        try {
            // The damage is the *base* of the clip stack, not merely the first
            // clip: a scroll view inside the damaged region narrows it further
            // and must widen back to the damage when its subtree ends, rather
            // than to the whole frame (ADR-0114).
            paint(frame, clip);
        } finally {
            // Context state, so it goes back: the next thing drawn on this frame
            // did not ask to be clipped.
            frame.resetClip();
        }
    }

    /// Paints the tree laid out by the last [#update].
    public void paint(Frame frame) {
        paint(frame, Clip.NONE);
    }

    /// The same, confined to `base` — the damage rectangle when there is one.
    private void paint(Frame frame, Clip base) {
        Objects.requireNonNull(frame, "frame");
        requireUsable();
        if (root == null) {
            throw new IllegalStateException(
                    "this render tree has never been updated, so there is nothing to paint;"
                            + " call update(frame, box) first");
        }
        // One path, reset between shapes, rather than one per rounded corner per
        // box per frame. A `BlendPath` is a native allocation and an `Arena`.
        layersRepainted = 0;
        layersComposited = 0;
        try (var path = BlendPath.create()) {
            var state = new Painting(frame, path, base);
            paint(root, 0, 0, 1.0, Affine.IDENTITY, base, state);
            state.untransform();
            // Back to the clip the frame arrived with, for the reason the
            // transform goes back to identity: the next thing drawn on this
            // frame -- an application's `onPaint`, or an overlay above the
            // tree -- did not ask to be confined to the last scroll view the
            // walk happened to end inside.
            state.clipTo(state.base());
        }
    }

    /// What the frame's transform is currently set to, so a run of untransformed
    /// boxes — every box in an ordinary frame — costs no calls at all.
    private static final class Painting {

        private final Frame frame;
        private final BlendPath path;
        private Affine current = Affine.IDENTITY;

        /// The clip the frame arrived with — the damage rectangle, or
        /// [Clip#NONE] for a full repaint. Every restore goes back to *this*
        /// rather than to no clip at all, because `resetClip` on the context
        /// means the whole surface and a subtree that finished must not be able
        /// to widen the damage it was painted inside (ADR-0114).
        private final Clip base;

        /// What the context is currently clipped to. Tracked here for the same
        /// reason `current` tracks the transform: it is context state, Blend2D
        /// offers no stack, and a run of unclipped boxes must cost no calls.
        private Clip clip;

        Painting(Frame frame, BlendPath path, Clip base) {
            this.frame = frame;
            this.path = path;
            this.base = base;
            this.clip = base;
        }

        void transform(Affine matrix) {
            if (!matrix.equals(current)) {
                frame.transform(matrix.a(), matrix.b(), matrix.c(),
                        matrix.d(), matrix.e(), matrix.f());
                current = matrix;
            }
        }

        void untransform() {
            if (!current.isIdentity()) {
                // A painter that left the last subtree's matrix on the context
                // would hand it to whatever draws next.
                frame.resetTransform();
                current = Affine.IDENTITY;
            }
        }

        /// Confines the context to `next`, whatever it was confined to before.
        ///
        /// Two native calls rather than one, and unavoidably so: `clipTo`
        /// *intersects* with the clip in force, so narrowing and widening cannot
        /// both be expressed by it, and `resetClip` is the only way back out.
        /// The accumulated rectangle is therefore recomputed in Java and
        /// assigned whole.
        ///
        /// **The transform goes to identity first.** Blend2D applies a clip in
        /// the context's current user space, and `next` is in the frame's
        /// coordinates — a clip set while a translated subtree's matrix was in
        /// force would land at the translation twice over. The matrix the next
        /// box needs is re-assigned by [#transform] on its way through, which
        /// costs nothing it was not already paying.
        void clipTo(Clip next) {
            if (next.equals(clip)) {
                return;
            }
            untransform();
            frame.resetClip();
            if (!next.isNone()) {
                frame.clipTo(next.left(), next.top(), next.width(), next.height());
            }
            clip = next;
        }

        Clip base() {
            return base;
        }
    }

    /// Draws one node and everything under it.
    ///
    /// Recursive over the render objects rather than over a flattened list,
    /// because a promoted node interrupts the walk: its subtree is drawn into a
    /// layer of its own and the layer is composited back, so "the boxes in paint
    /// order" is no longer one sequence.
    private void paint(
            RenderObject object, double parentLeft, double parentTop,
            double parentAlpha, Affine parentTransform, Clip parentClip, Painting state) {

        var box = object.box();
        var layout = object.node().layout();
        var left = parentLeft + layout.left();
        var top = parentTop + layout.top();
        var transform = compose(parentTransform, box.transform(), left, top,
                layout.width(), layout.height());

        // Before the promoted branch, not after it. A layer is rasterized whole
        // and composited with one blit, and that blit is the only thing an
        // enclosing scroll view can confine -- so a promoted node inside a
        // viewport that had not set the clip yet would blit its whole raster
        // straight over the viewport's edge (ADR-0114).
        state.clipTo(parentClip);

        if (object.isPromoted()) {
            compositeThroughLayer(object, left, top, parentAlpha, transform, state);
            return;
        }

        var alpha = parentAlpha * box.opacity();
        state.transform(transform);
        BoxPainter.paintOne(state.frame, state.path, box.fade(alpha),
                new ComputedLayout((float) left, (float) top, layout.width(), layout.height()));

        // The box itself is drawn under its *parent's* clip and the children
        // under this one's. That order is what CSS means: `overflow: hidden`
        // clips the content of a box, not the box -- so a viewport still paints
        // its own background and its own border, and only what is inside it is
        // cut off.
        var clip = clipFor(box, transform, parentClip, left, top, layout);
        if (clip.isEmpty()) {
            // Scrolled entirely out of sight. Nothing under here can produce a
            // pixel, so the walk stops -- which is the one place a clip saves
            // the *traversal* as well as the rasterization (ADR-0114).
            return;
        }
        // Document order, then whatever asked to be drawn last. Two passes and no
        // sort: the flag is rare, the lists are short, and a comparator would put
        // an ordering *among* elevated siblings that ADR-0123 deliberately does
        // not define.
        for (var child : object.children()) {
            if (!child.box().elevated()) {
                paint(child, left, top, alpha, transform, clip, state);
            }
        }
        for (var child : object.children()) {
            if (child.box().elevated()) {
                paint(child, left, top, alpha, transform, clip, state);
            }
        }
        state.clipTo(parentClip);
    }

    /// The clip a box's children are painted under.
    ///
    /// The parent's unchanged for the overwhelming majority of boxes, which is
    /// what keeps this free: an ordinary frame allocates nothing here and
    /// changes no context state.
    ///
    /// The rectangle is the box's **padding box** rather than its border box,
    /// which is CSS's rule — content scrolls under the border, not over it — and
    /// is what makes a viewport with a 1px edge keep that edge crisp while the
    /// rows inside it slide past.
    private static Clip clipFor(
            Box box, Affine transform, Clip parent,
            double left, double top, ComputedLayout layout) {

        if (box.overflow() == Overflow.VISIBLE) {
            return parent;
        }
        var padding = box.padding();
        var own = Clip.of(
                left + edge(padding.left(), layout.width()),
                top + edge(padding.top(), layout.height()),
                layout.width() - edge(padding.left(), layout.width())
                        - edge(padding.right(), layout.width()),
                layout.height() - edge(padding.top(), layout.height())
                        - edge(padding.bottom(), layout.height()));
        return parent.intersect(own.map(transform));
    }

    /// One padding edge in logical pixels, against the box's own size.
    private static double edge(StyleLength length, double base) {
        return switch (length) {
            case StyleLength.Points points -> points.value();
            case StyleLength.Percent percent -> percent.value() / 100.0 * base;
            case StyleLength.Keyword ignored -> 0;
        };
    }

    /// Renders a promoted subtree into its own raster and composites it back.
    ///
    /// Three things happen here that do not happen on the ordinary path:
    ///
    /// 1. The subtree is drawn at **full strength**. Its own `opacity` is applied
    ///    once to the finished raster, which is what makes it a group.
    /// 2. It is drawn **untransformed**, into a layer whose origin is the
    ///    subtree's own bounding box. The transform is applied to the blit
    ///    instead — so a node animating a transform re-blits a raster it already
    ///    has rather than re-rasterizing itself through a new matrix.
    /// 3. The raster is **kept**. If nothing under this node changed, the layer
    ///    is still what it was and this is a blit and nothing else.
    private void compositeThroughLayer(
            RenderObject object, double left, double top,
            double parentAlpha, Affine transform, Painting state) {

        var frame = state.frame;
        var scale = frame.scale();
        var bounds = bounds(object, left, top);
        if (bounds == null) {
            return;
        }

        var layer = object.layerFor(bounds, scale);
        layersComposited++;
        if (!layer.isValid()) {
            layersRepainted++;
            // The subtree, drawn into the layer's own coordinates: its bounding
            // box's top-left corner becomes (0, 0), which is the one piece of
            // arithmetic a layer costs.
            layer.paint(scale, into -> {
                try (var path = BlendPath.create()) {
                    // [Clip#NONE] and not the clip in force outside: the layer
                    // is rasterized at full extent and it is the *blit* that
                    // gets confined. A clip inside this subtree still applies,
                    // and lands in the layer's own coordinates for free --
                    // every rectangle below is derived from the shifted origin
                    // passed here.
                    var inner = new Painting(into, path, Clip.NONE);
                    paintIntoLayer(object, left - bounds.left(), top - bounds.top(),
                            1.0, Affine.IDENTITY, inner);
                    inner.untransform();
                }
            });
        }

        state.transform(transform);
        frame.drawLayer(bounds.left(), bounds.top(), layer,
                parentAlpha * object.box().opacity());
    }

    /// The same walk, inside a layer: the promoted node itself is drawn here
    /// rather than treated as promoted again, and its own opacity is left for the
    /// composite.
    private void paintIntoLayer(
            RenderObject object, double left, double top,
            double alpha, Affine transform, Painting state) {

        var box = object.box();
        var layout = object.node().layout();
        state.transform(transform);
        var computed = new ComputedLayout((float) left, (float) top,
                layout.width(), layout.height());
        BoxPainter.paintOne(state.frame, state.path, box.fade(alpha), computed);

        // The promoted node's own `overflow` still clips its children, inside
        // the layer and in the layer's coordinates.
        var clip = clipFor(box, transform, Clip.NONE, left, top, computed);
        if (clip.isEmpty()) {
            return;
        }
        for (var child : object.children()) {
            if (!child.box().elevated()) {
                paint(child, left, top, alpha, transform, clip, state);
            }
        }
        for (var child : object.children()) {
            if (child.box().elevated()) {
                paint(child, left, top, alpha, transform, clip, state);
            }
        }
    }

    /// The rectangle a promoted subtree actually covers, in logical coordinates.
    ///
    /// Not just the node's own box. Three things reach outside it and each one
    /// would be **clipped away** by a layer sized to the border box, which is a
    /// visible bug rather than a rounding difference:
    ///
    /// - a focus ring, which CSS draws outside the border box by design;
    /// - a child transformed out from under its parent;
    /// - a child that simply overflows, which flexbox allows.
    ///
    /// Rounded outward to whole logical pixels afterwards, because the layer is a
    /// raster and half a pixel of it cannot be allocated.
    ///
    /// Returns null when the subtree covers nothing, which is a zero-sized node
    /// and not an error.
    private Bounds bounds(RenderObject object, double left, double top) {
        var box = new double[] {
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        accumulate(object, left, top, Affine.IDENTITY, box, true);

        if (box[0] > box[2] || box[1] > box[3]) {
            return null;
        }
        var l = Math.floor(box[0]);
        var t = Math.floor(box[1]);
        return new Bounds(l, t, Math.ceil(box[2]) - l, Math.ceil(box[3]) - t);
    }

    /// Grows `into` to cover this node's painted extent and its children's.
    ///
    /// @param root whether this is the promoted node itself, whose own transform
    ///             is applied to the composite rather than inside the layer
    private static void accumulate(
            RenderObject object, double left, double top, Affine transform,
            double[] into, boolean root) {

        var box = object.box();
        var layout = object.node().layout();
        var matrix = root
                ? transform
                : compose(transform, box.transform(), left, top,
                        layout.width(), layout.height());

        // Outward by the ring's offset and width, which is where `outline` is
        // drawn and is the one part of a box that is deliberately outside it.
        var out = box.decoration().hasOutline()
                ? box.decoration().outlineOffset() + box.decoration().outlineWidth()
                : 0;
        var l = left - out;
        var t = top - out;
        var r = left + layout.width() + out;
        var b = top + layout.height() + out;

        // All four corners, because a rotation turns a rectangle into one that is
        // not axis-aligned and taking two corners would miss half of it.
        cover(into, matrix, l, t);
        cover(into, matrix, r, t);
        cover(into, matrix, r, b);
        cover(into, matrix, l, b);

        for (var child : object.children()) {
            var childLayout = child.node().layout();
            accumulate(child, left + childLayout.left(), top + childLayout.top(),
                    matrix, into, false);
        }
    }

    private static void cover(double[] into, Affine matrix, double x, double y) {
        var mappedX = matrix.mapX(x, y);
        var mappedY = matrix.mapY(x, y);
        into[0] = Math.min(into[0], mappedX);
        into[1] = Math.min(into[1], mappedY);
        into[2] = Math.max(into[2], mappedX);
        into[3] = Math.max(into[3], mappedY);
    }

    /// A rectangle in logical coordinates, on whole-pixel boundaries.
    record Bounds(double left, double top, double width, double height) {
    }

    /// Hands each node its absolute position and accumulated transform.
    ///
    /// Absolute, because Yoga reports every box relative to its parent and almost
    /// nothing wants that: painting, hit testing and damage all work in the
    /// frame's own coordinates.
    ///
    /// @throws IllegalStateException if [#update] has not run
    public void forEachPlacedBox(Consumer<BoxPainter.Placed> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        requireUsable();
        if (root == null) {
            throw new IllegalStateException(
                    "this render tree has never been updated, so there is no layout to walk;"
                            + " call update(frame, box) first");
        }
        visit(root, 0, 0, 1.0, Affine.IDENTITY, Clip.NONE, visitor);
    }

    /// Walks the tree, turning Yoga's parent-relative positions into absolute
    /// ones and accumulating `opacity` and `transform`.
    ///
    /// The visitor is handed a box whose colours **already include** every opacity
    /// above it, so nothing downstream has to know that `opacity` inherits its
    /// effect. The transform accumulates the same way and for the same reason —
    /// a transformed box takes its subtree with it — but cannot be folded into the
    /// box, so it travels beside it (ADR-0068).
    ///
    /// **The positions are the untransformed ones, all the way down.** A child's
    /// place comes from Yoga, which never saw a transform; the matrix is applied
    /// to the result. That is CSS's rule, and it is what makes a hover scale cost
    /// no layout pass and move no sibling.
    private static void visit(
            RenderObject object, double parentLeft, double parentTop,
            double parentAlpha, Affine parentTransform, Clip parentClip,
            Consumer<BoxPainter.Placed> visitor) {

        var box = object.box();
        var layout = object.node().layout();
        var left = parentLeft + layout.left();
        var top = parentTop + layout.top();
        var alpha = parentAlpha * box.opacity();
        var transform = compose(parentTransform, box.transform(), left, top,
                layout.width(), layout.height());

        var computed = new ComputedLayout((float) left, (float) top,
                layout.width(), layout.height());
        visitor.accept(new BoxPainter.Placed(box.fade(alpha), computed, transform, parentClip));

        // Exactly the clip the painter computes, from the same inputs in the
        // same order. Two walks that have to agree is how a pointer starts
        // landing where the ink is not -- the argument HitTest already makes
        // about the transform's inverse -- so the arithmetic is shared rather
        // than repeated (ADR-0114).
        var clip = clipFor(box, transform, parentClip, left, top, computed);
        if (clip.isEmpty()) {
            return;
        }
        // The same two passes the painter makes, and it must be the same two: this
        // walk is what the hit test is built from, and a box drawn on top that was
        // not *clicked* first would be a header you can see and point through
        // (ADR-0123).
        //
        // The *unfaded* box is what the children were built from, so the
        // accumulated alpha travels as a number rather than being applied twice on
        // the way down.
        for (var child : object.children()) {
            if (!child.box().elevated()) {
                visit(child, left, top, alpha, transform, clip, visitor);
            }
        }
        for (var child : object.children()) {
            if (child.box().elevated()) {
                visit(child, left, top, alpha, transform, clip, visitor);
            }
        }
    }

    /// A box's own transform put where the box is, then composed under its
    /// ancestors'.
    ///
    /// Returns the parent's matrix unchanged when this box has no transform,
    /// which is the path every box in an ordinary frame takes and which allocates
    /// nothing.
    static Affine compose(
            Affine parent, Transform own, double left, double top, double width, double height) {

        if (own.isNone()) {
            return parent;
        }
        var local = own.matrix(width, height);
        if (local.isIdentity()) {
            return parent;
        }
        // A transform is written in the box's own coordinates -- `translate(10px)`
        // is ten pixels right of wherever the box is, and `transform-origin:
        // 50% 50%` is the middle of *this* box. So the origin moves to (0, 0),
        // the matrix applies there, and the origin goes back.
        return Affine.translate(-left, -top)
                .then(local)
                .then(Affine.translate(left, top))
                .then(parent);
    }

    /// The regions of the frame that differ from the one before it.
    ///
    /// Call after [#update]. Every node that changed contributes the union of
    /// where it **was** and where it **is** — both, because a node that moved
    /// leaves a hole behind it, and damaging only its new position is the classic
    /// partial-repaint artefact where the old drawing stays on screen.
    ///
    /// Returns `DamageRect.all` on the first frame, and whenever the answer would
    /// be more rectangles than presenting the whole frame is worth: a hover at one
    /// corner and a caret at the other genuinely are two small regions, but past a
    /// handful the bookkeeping costs more than the upload it saves.
    ///
    /// **What this is and is not.** These rectangles say what an upload has to
    /// carry; the frame is still *painted* in full. Painting less needs the
    /// rasterizer clipped to the damage — another export — and a promise from the
    /// backend SPI that the buffer it lends back holds last frame's pixels, which
    /// it does not currently make
    /// ([ADR-0071](../../../../../../book/src/adr/0071-a-layer-is-a-subtrees-raster.md)).
    public java.util.List<DamageRect> damage(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        requireUsable();
        var whole = DamageRect.all(frame.pixelSize());
        if (root == null) {
            return java.util.List.of(whole);
        }

        var rects = new java.util.ArrayList<DamageRect>();
        var everything = new boolean[] {false};
        collectDamage(root, 0, 0, Affine.IDENTITY, frame, rects, everything);

        if (everything[0] || rects.size() > MAX_DAMAGE_RECTS) {
            return java.util.List.of(whole);
        }

        // Clamped **here**, on the way out, rather than only where each rectangle
        // is computed. A node's remembered rectangle was measured against
        // whatever the frame was last time, and a window being dragged smaller
        // gives a frame that is one pixel narrower than the one before it — so
        // `union(before, now)` produces a rectangle that fits neither frame and
        // the backend rightly refuses it:
        //
        //     damage 2066x1103+0+0 falls outside the 2065x1102 px frame
        //
        // Nothing is lost by clipping: the part of `before` that is outside the
        // new frame is not on screen any more, so there is nothing there to
        // repaint.
        var clamped = new java.util.ArrayList<DamageRect>(rects.size());
        for (var rect : rects) {
            var fitted = clampTo(rect, frame.pixelSize());
            if (fitted != null) {
                clamped.add(fitted);
            }
        }
        // An empty list is a real answer and not a missing one: the SPI reads it
        // as "nothing changed, present nothing", which is exactly right for a
        // window sitting still.
        return java.util.List.copyOf(clamped);
    }

    /// `rect` cut down to `size`, or null if none of it is inside.
    private static DamageRect clampTo(DamageRect rect, PhysicalSize size) {
        var x = Math.clamp(rect.x(), 0, size.width());
        var y = Math.clamp(rect.y(), 0, size.height());
        var right = Math.clamp(rect.x() + rect.width(), 0, size.width());
        var bottom = Math.clamp(rect.y() + rect.height(), 0, size.height());
        return right > x && bottom > y
                ? new DamageRect(x, y, right - x, bottom - y)
                : null;
    }

    /// Past this many regions, the whole frame is cheaper to present than the
    /// list is to track. Small on purpose: the cases worth splitting are one
    /// control repainting and a caret blinking, not a table redrawing.
    private static final int MAX_DAMAGE_RECTS = 8;

    private void collectDamage(
            RenderObject object, double parentLeft, double parentTop, Affine parentTransform,
            Frame frame, java.util.List<DamageRect> into, boolean[] everything) {

        var box = object.box();
        var layout = object.node().layout();
        var left = parentLeft + layout.left();
        var top = parentTop + layout.top();
        var transform = compose(parentTransform, box.transform(), left, top,
                layout.width(), layout.height());

        var now = toPhysical(bounds(object, left, top), frame);
        var before = object.lastRect();
        object.rememberRect(now);

        if (before == null) {
            // Never drawn: there is no "where it was", so the safe answer is that
            // the whole frame is suspect. In practice this is the first frame,
            // where it is also the true answer.
            //
            // The walk **carries on regardless**, and that is not tidiness: it is
            // what records every descendant's rectangle. Returning here instead
            // left the children with no remembered position, so the next frame
            // found them null too and reported the whole window — forever, on
            // every frame, with damage tracking that looked implemented and did
            // nothing.
            everything[0] = true;
        } else if (object.hasSelfChanged() || !before.equals(now)) {
            add(into, union(before, now));
        }
        for (var child : object.children()) {
            collectDamage(child, left, top, transform, frame, into, everything);
        }
    }

    /// Adds `rect`, merging it into an existing one they overlap rather than
    /// keeping both — two rectangles that touch are one upload.
    private static void add(java.util.List<DamageRect> into, DamageRect rect) {
        for (var i = 0; i < into.size(); i++) {
            if (intersects(into.get(i), rect)) {
                into.set(i, union(into.get(i), rect));
                return;
            }
        }
        into.add(rect);
    }

    private static boolean intersects(DamageRect a, DamageRect b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }

    private static DamageRect union(DamageRect a, DamageRect b) {
        var x = Math.min(a.x(), b.x());
        var y = Math.min(a.y(), b.y());
        return new DamageRect(x, y,
                Math.max(a.x() + a.width(), b.x() + b.width()) - x,
                Math.max(a.y() + a.height(), b.y() + b.height()) - y);
    }

    /// Logical bounds to a physical rectangle, rounded **outward** and clamped to
    /// the frame.
    ///
    /// Outward, because a rectangle that rounded inward would leave the outermost
    /// row of antialiased pixels out of the damage and therefore un-uploaded — a
    /// one-pixel fringe of the previous frame around everything that moved.
    private static DamageRect toPhysical(Bounds bounds, Frame frame) {
        var size = frame.pixelSize();
        if (bounds == null) {
            return new DamageRect(0, 0, 0, 0);
        }
        var scale = frame.scale().factor();
        var x = Math.clamp((int) Math.floor(bounds.left() * scale), 0, size.width());
        var y = Math.clamp((int) Math.floor(bounds.top() * scale), 0, size.height());
        var right = Math.clamp(
                (int) Math.ceil((bounds.left() + bounds.width()) * scale), 0, size.width());
        var bottom = Math.clamp(
                (int) Math.ceil((bounds.top() + bounds.height()) * scale), 0, size.height());
        return new DamageRect(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    /// Whether anything in the tree changed on the last [#update].
    ///
    /// What decides whether a promoted subtree is blitted from the raster it has
    /// or drawn again. Exposed because that is the thing a test about layer
    /// caching has to assert on: inferring it from pixels would pass whether the
    /// raster was reused or redrawn, since both produce the same image.
    public boolean rootChanged() {
        return root != null && root.hasChanged();
    }

    /// How many promoted layers were **rasterized** during the last [#paint],
    /// as opposed to blitted from the raster they already had.
    ///
    /// The outcome rather than the flag behind it, which is what makes it worth
    /// exposing: §1.7's whole claim for layer promotion is that a frame of an
    /// opacity or transform animation costs a composite and not a repaint, and
    /// this is that claim as a number. A test asserting on pixels would pass
    /// whether the raster was reused or redrawn, because both produce the same
    /// image — which is exactly why the bug where a fading group re-rasterized
    /// itself every frame survived its own test suite.
    public int layersRepainted() {
        return layersRepainted;
    }

    /// How many promoted layers were composited at all, repainted or not.
    public int layersComposited() {
        return layersComposited;
    }

    private int layersRepainted;
    private int layersComposited;

    /// How many render objects are alive — diagnostics, and what a test asserts
    /// when it wants to know a subtree was reused rather than rebuilt.
    public int size() {
        return root == null ? 0 : count(root);
    }

    private static int count(RenderObject object) {
        var total = 1;
        for (var child : object.children()) {
            total += count(child);
        }
        return total;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (root != null) {
            root.close();
            root = null;
        }
        config.close();
    }

    private void requireUsable() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a RenderTree belongs to the thread that created it, and this is not it —"
                            + " a Yoga tree may not span threads");
        }
        if (closed) {
            throw new IllegalStateException("this render tree has been closed");
        }
    }
}
