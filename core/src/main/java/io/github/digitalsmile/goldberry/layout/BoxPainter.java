package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.css.Affine;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Lays a [Box] tree out with Yoga and paints it with Blend2D.
///
/// This is where the two engines meet. Everything either of them needed was
/// bound separately — Yoga's node API in ADR-0029, Blend2D's context in
/// ADR-0031 — and this is the first code that makes one feed the other:
///
/// 1. Build a Yoga tree mirroring the boxes.
/// 2. Set the config's point scale factor from the frame's display scale, so
///    computed edges land on **physical** pixels rather than logical ones.
/// 3. Lay out at the frame's logical size.
/// 4. Walk the result, accumulating each box's absolute position, and fill.
///
/// Step 2 is the one worth pausing on. Yoga rounds computed positions to a pixel
/// grid, and the grid it uses is the config's. Left at 1, every edge in a 1.5×
/// window lands on a whole *logical* pixel — which is one and a half physical
/// ones, so half the edges fall mid-pixel and the compositor smears them.
/// Setting it to the display scale is what makes a 1px border one crisp device
/// pixel at any scale, and it is the piece that had no consumer until now.
///
/// Nothing here is retained. A layout pass builds a tree, reads it, and frees
/// it — which is the wrong shape for a real toolkit and the right shape for a
/// join that exists to be exercised. Retaining it is the render tree's job, and
/// the render tree is blocked on ADR-0004.
public final class BoxPainter {

    private BoxPainter() {
    }

    /// Lays `root` out to fill `frame` and paints it, keeping nothing.
    ///
    /// The **one-shot** form: it builds a [RenderTree], uses it once and closes
    /// it. Right for a golden image, a test or anything else that paints a tree a
    /// single time, and wrong for a window — an application that paints sixty
    /// times a second wants one `RenderTree` held for the life of the window, so
    /// that Yoga's nodes, its layout cache and the measure callbacks survive
    /// between frames
    /// ([ADR-0069](../../../../../../book/src/adr/0069-the-render-tree-is-retained.md)).
    ///
    /// There is one implementation and two lifetimes, rather than two
    /// implementations — which is what ADR-0053 rejected and what would otherwise
    /// leave the goldens testing a path applications do not take.
    public static void paint(Frame frame, Box root) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(root, "root");
        try (var tree = RenderTree.create()) {
            tree.update(frame, root);
            tree.paint(frame);
        }
    }

    /// Draws whatever `walk` hands over, in the order it hands it over.
    ///
    /// The drawing routine itself, separated from where the boxes came from so
    /// that a retained tree and a throwaway one share it.
    static void paintPlaced(Frame frame, Consumer<Consumer<Placed>> walk) {
        // One path, reset between shapes, rather than one per rounded corner per
        // box per frame. A `BlendPath` is a native allocation and an `Arena`; a
        // window of forty rounded controls would otherwise make eighty of them
        // every frame, to draw the same four arcs.
        try (var path = BlendPath.create()) {
            // What the frame's transform is currently set to, so a run of
            // untransformed boxes -- which is every box in an ordinary frame --
            // costs no calls at all rather than a reset each. Held in a
            // one-element array because the visitor is a lambda and this is the
            // one piece of state the walk cannot carry as a parameter: the
            // context is shared by every box, not scoped to one subtree.
            var current = new Affine[] {Affine.IDENTITY};
            walk.accept(placed -> {
                var matrix = placed.transform();
                if (!matrix.equals(current[0])) {
                    frame.transform(matrix.a(), matrix.b(), matrix.c(),
                            matrix.d(), matrix.e(), matrix.f());
                    current[0] = matrix;
                }
                paintOne(frame, path, placed.box(), placed.layout());
            });
            if (!current[0].isIdentity()) {
                // A painter that left the last subtree's matrix on the context
                // would hand it to whatever draws next -- an application's own
                // `onPaint` work, or the next paint on the same frame -- which is
                // a bug that would show up somewhere else.
                frame.resetTransform();
            }
        }
    }

    /// Everything one box puts on the screen, in the order CSS paints it:
    /// background, border, content, then the focus ring on top.
    ///
    /// The ring is last because it is drawn *outside* the border box and must
    /// survive whatever the box itself drew — and it is drawn per box rather than
    /// once at the end because a box tree has no z-order yet, so "last" and "on
    /// top" are the same thing only within a box (ADR-0053).
    static void paintOne(Frame frame, BlendPath path, Box box, ComputedLayout layout) {
        var decoration = box.decoration();
        var x = layout.left();
        var y = layout.top();
        var width = layout.width();
        var height = layout.height();

        if ((box.background() >>> 24) != 0) {
            if (decoration.radius() > 0) {
                path.reset();
                RoundRect.addTo(path, 0, 0, width, height, decoration.radius());
                frame.fillPath(x, y, path, box.background());
            } else {
                // The square case keeps the call it always had. A rectangle is
                // Blend2D's fastest primitive and the overwhelming majority of
                // boxes are still rectangles.
                frame.fillRect(x, y, width, height, box.background());
            }
        }

        if (decoration.hasBorder()) {
            // Stroked down the middle of the path, so the path is inset by half
            // the width to put the ink *inside* the border box — which is what
            // `border-box` sizing means and what makes a 1px border on a 32px
            // control leave 30px of content rather than 32.
            var inset = decoration.borderWidth() / 2;
            path.reset();
            RoundRect.addTo(path, inset, inset,
                    width - decoration.borderWidth(), height - decoration.borderWidth(),
                    Math.max(0, decoration.radius() - inset));
            frame.strokePath(x, y, path, decoration.borderWidth(),
                    BlendStrokeCap.BUTT, BlendStrokeJoin.MITER_CLIP, decoration.borderColor());
        }

        if (box.text() != null) {
            // Wrapped at the width the layout pass settled on -- the same
            // width the measure function was last asked about, so the
            // paragraph's memo answers without re-wrapping and the lines
            // drawn are exactly the lines that were measured.
            box.text().paragraph().paint(frame, x, y, width, box.text().argb());
        }

        if (box.icon() != null) {
            // At the box's own origin, not centred in it: the box was sized
            // to the icon by `Box.icon`, so they are the same rectangle
            // unless a stylesheet said otherwise -- and if it did, the icon
            // stays put rather than drifting to a centre nobody asked for.
            box.icon().icon().draw(frame, x, y, box.icon().argb());
        }

        if (box.mark() != null) {
            paintMark(frame, path, box.mark(), x, y, width, height);
        }

        if (decoration.hasOutline()) {
            // Outward by the offset plus half the width, so the *inside* edge of
            // the ring sits exactly `outline-offset` from the border box —
            // which is what a 2px ring at a 2px offset means, and what makes two
            // controls 8px apart not have their rings touch.
            var out = decoration.outlineOffset() + decoration.outlineWidth() / 2;
            path.reset();
            RoundRect.addTo(path, -out, -out, width + out * 2, height + out * 2,
                    // A ring around a rounded corner is concentric with it, so
                    // its radius grows by the same distance it moved out. A
                    // square corner stays square: a ring that rounded itself
                    // around a sharp box would not follow the control.
                    decoration.radius() > 0 ? decoration.radius() + out : 0);
            frame.strokePath(x, y, path, decoration.outlineWidth(),
                    BlendStrokeCap.BUTT, BlendStrokeJoin.MITER_CLIP, decoration.outlineColor());
        }
    }

    /// A checkbox's tick, its mixed-state dash, or a radio's dot, drawn to fill
    /// the box it is on.
    ///
    /// The proportions are of the box rather than absolute, so the same three
    /// shapes are right at the design system's 16px glyph and at whatever size an
    /// application's stylesheet asks for. They were chosen against the 24×24
    /// Lucide grid the rest of the toolkit's iconography sits on (§1.6), so a
    /// tick beside a Lucide icon reads as the same drawing.
    private static void paintMark(
            Frame frame, BlendPath path, Box.Mark mark,
            double x, double y, double width, double height) {

        path.reset();
        switch (mark.kind()) {
            case CHECK -> {
                path.moveTo(width * 0.22, height * 0.52);
                path.lineTo(width * 0.42, height * 0.72);
                path.lineTo(width * 0.78, height * 0.30);
            }
            case DASH -> {
                path.moveTo(width * 0.24, height * 0.5);
                path.lineTo(width * 0.76, height * 0.5);
            }
            case PLUS -> {
                path.moveTo(width * 0.5, height * 0.22);
                path.lineTo(width * 0.5, height * 0.78);
                path.moveTo(width * 0.22, height * 0.5);
                path.lineTo(width * 0.78, height * 0.5);
            }
            case CROSS -> {
                // The same inset as the tick's, so a × and a ✓ in the same
                // column are the same size.
                path.moveTo(width * 0.26, height * 0.26);
                path.lineTo(width * 0.74, height * 0.74);
                path.moveTo(width * 0.74, height * 0.26);
                path.lineTo(width * 0.26, height * 0.74);
            }
            case ARC -> {
                // Inset by half the stroke so the ring's *outer* edge is the box
                // rather than its centre line -- a 16px spinner that stroked on
                // the box's edge would draw 1px outside it all the way round.
                //
                // The angles are the mark's rather than this method's, because
                // this is the one shape that has to show a value: a knob's arc
                // indicator is the same ring as a spinner's, cut to a fraction
                // (ADR-0089). A zero sweep draws nothing, which is what a knob at
                // its minimum wants and is `Arc.addTo`'s own early return.
                Arc.addTo(path, width / 2, height / 2,
                        Math.min(width, height) / 2 - mark.thickness() / 2,
                        mark.start(), mark.sweep());
            }
            case POINTER -> {
                // A line out from the middle at the mark's angle -- which way the
                // knob is turned. The angles are the mark's for the same reason
                // an arc's are: this is a shape whose geometry *is* a value
                // (ADR-0089).
                var radius = Math.min(width, height) / 2;
                var cos = Math.cos(mark.start());
                var sin = Math.sin(mark.start());
                path.moveTo(width / 2 + radius * Box.Mark.POINTER_INNER * cos,
                        height / 2 + radius * Box.Mark.POINTER_INNER * sin);
                path.lineTo(width / 2 + radius * Box.Mark.POINTER_OUTER * cos,
                        height / 2 + radius * Box.Mark.POINTER_OUTER * sin);
            }
            case DOT -> {
                // Filled rather than stroked, so a radio's dot is solid at any
                // size instead of becoming a ring as the box grows.
                var radius = Math.min(width, height) * 0.25;
                RoundRect.addTo(path, width / 2 - radius, height / 2 - radius,
                        radius * 2, radius * 2, radius);
                frame.fillPath(x, y, path, mark.argb());
                return;
            }
        }
        // Round caps and joins: the tick's corner is the one place in the toolkit
        // where a mitre would put a spike outside the 16px glyph.
        frame.strokePath(x, y, path, mark.thickness(),
                BlendStrokeCap.ROUND, BlendStrokeJoin.ROUND, mark.argb());
    }

    /// One box, where it ended up, and what moves it.
    ///
    /// @param box       the box, with every ancestor's `opacity` already applied
    ///                  to its colours
    /// @param layout    its **absolute** rectangle in logical coordinates, before
    ///                  any transform — which is what CSS means by a transform:
    ///                  layout runs first and the matrix moves the result
    /// @param transform every ancestor's transform and its own, composed, in the
    ///                  frame's coordinates; [Affine#IDENTITY] for the
    ///                  overwhelming majority of boxes
    public record Placed(Box box, ComputedLayout layout, Affine transform) {

        public Placed {
            Objects.requireNonNull(box, "box");
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(transform, "transform");
        }

        /// Whether this box is drawn where it was laid out.
        public boolean isPlain() {
            return transform.isIdentity();
        }
    }

    /// Lays `root` out to fill `frame` and hands each box its **absolute**
    /// position, in logical coordinates.
    ///
    /// Absolute, because Yoga reports every box relative to its parent and
    /// almost nothing wants that: painting, hit-testing and damage all work in
    /// the frame's own coordinates. Accumulating it here means each caller does
    /// not.
    ///
    /// The transform is dropped. Callers that need it — the painter, which sets
    /// it on the context, and hit testing, which inverts it — use
    /// [#forEachPlacedBox]; callers that only want rectangles keep the simpler
    /// signature.
    public static void forEachBox(Frame frame, Box root, BiConsumer<Box, ComputedLayout> visitor) {
        Objects.requireNonNull(visitor, "visitor");
        forEachPlacedBox(frame, root, placed -> visitor.accept(placed.box(), placed.layout()));
    }

    /// The same walk, with each box's accumulated transform.
    ///
    /// One-shot, like [#paint(Frame, Box)]: the Yoga tree it builds is freed
    /// before this returns. A caller doing this every frame wants a [RenderTree].
    public static void forEachPlacedBox(Frame frame, Box root, Consumer<Placed> visitor) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(visitor, "visitor");
        try (var tree = RenderTree.create()) {
            tree.update(frame, root);
            tree.forEachPlacedBox(visitor);
        }
    }
}
