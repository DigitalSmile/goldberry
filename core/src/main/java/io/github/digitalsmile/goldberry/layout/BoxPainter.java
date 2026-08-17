package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout;
import io.github.digitalsmile.goldberry.natives.yoga.Edge;
import io.github.digitalsmile.goldberry.natives.yoga.Gutter;
import io.github.digitalsmile.goldberry.natives.yoga.YogaConfig;
import io.github.digitalsmile.goldberry.natives.yoga.YogaNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

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

    /// Lays `root` out to fill `frame` and paints it.
    public static void paint(Frame frame, Box root) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(root, "root");

        // One path, reset between shapes, rather than one per rounded corner per
        // box per frame. A `BlendPath` is a native allocation and an `Arena`; a
        // window of forty rounded controls would otherwise make eighty of them
        // every frame, to draw the same four arcs.
        try (var path = BlendPath.create()) {
            forEachBox(frame, root, (box, layout) -> paintOne(frame, path, box, layout));
        }
    }

    /// Everything one box puts on the screen, in the order CSS paints it:
    /// background, border, content, then the focus ring on top.
    ///
    /// The ring is last because it is drawn *outside* the border box and must
    /// survive whatever the box itself drew — and it is drawn per box rather than
    /// once at the end because a box tree has no z-order yet, so "last" and "on
    /// top" are the same thing only within a box (ADR-0053).
    private static void paintOne(Frame frame, BlendPath path, Box box, ComputedLayout layout) {
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

    /// Lays `root` out to fill `frame` and hands each box its **absolute**
    /// position, in logical coordinates.
    ///
    /// Absolute, because Yoga reports every box relative to its parent and
    /// almost nothing wants that: painting, hit-testing and damage all work in
    /// the frame's own coordinates. Accumulating it here means each caller does
    /// not.
    public static void forEachBox(Frame frame, Box root, BiConsumer<Box, ComputedLayout> visitor) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(visitor, "visitor");

        var size = frame.size();
        try (var config = YogaConfig.create()) {
            // The whole point of the config, and the reason fractional DPI works
            // at all on the layout side.
            config.setPointScaleFactor(frame.scale().factor());

            var boxes = new ArrayList<Box>();
            try (var tree = build(config, root, boxes)) {
                tree.calculateLayout(size.width(), size.height());
                visit(tree, boxes, 0, 0, 0, 1.0, visitor);
            }
        }
    }

    /// Builds the Yoga tree, recording each box in the same order the nodes are
    /// created so the two can be walked together afterwards.
    private static YogaNode build(YogaConfig config, Box box, List<Box> order) {
        var node = YogaNode.create(config);
        order.add(box);

        node.setFlexDirection(box.direction());
        node.setJustifyContent(box.justifyContent());
        node.setAlignItems(box.alignItems());
        node.setWidth(box.width());
        node.setHeight(box.height());
        // Per edge rather than Edge.ALL, because `padding: 0 12px` is what a
        // control wants and Yoga resolves the more specific edge over ALL only if
        // both are set. Setting the four is one call more and no ambiguity.
        var padding = box.padding();
        node.setPadding(Edge.TOP, padding.top());
        node.setPadding(Edge.RIGHT, padding.right());
        node.setPadding(Edge.BOTTOM, padding.bottom());
        node.setPadding(Edge.LEFT, padding.left());
        node.setGap(Gutter.ALL, box.gap());
        node.setFlexGrow((float) box.flexGrow());

        // Text makes the node a measured leaf. Yoga calls this back from C,
        // several times per pass as it tries widths (ADR-0017), and the callback
        // is owned by the node -- closing the tree closes it.
        if (box.text() != null) {
            node.setMeasureFunction(box.text().paragraph().measureFunction());
        }

        for (var child : box.children()) {
            node.addChild(build(config, child, order));
        }
        return node;
    }

    /// Walks node and box tree in step, turning Yoga's parent-relative positions
    /// into absolute ones and accumulating `opacity` down the tree.
    ///
    /// The visitor is handed a box whose colours **already include** every
    /// opacity above it, so nothing downstream has to know that `opacity`
    /// inherits its effect. A box at 1.0 under a parent at 1.0 is handed through
    /// unchanged and allocates nothing, which is every box in an ordinary frame.
    ///
    /// @param alpha the product of every ancestor's opacity and this box's own
    /// @return the index after this subtree, so siblings stay aligned with the
    ///         list built during construction
    private static int visit(
            YogaNode node, List<Box> order, int index,
            double parentLeft, double parentTop, double parentAlpha,
            BiConsumer<Box, ComputedLayout> visitor) {

        var box = order.get(index);
        var layout = node.layout();
        var left = parentLeft + layout.left();
        var top = parentTop + layout.top();
        var alpha = parentAlpha * box.opacity();

        visitor.accept(box.fade(alpha), new ComputedLayout(
                (float) left, (float) top, layout.width(), layout.height()));

        var next = index + 1;
        for (var child : node.children()) {
            // The *unfaded* box is what the children were built from, so the
            // accumulated alpha travels as a number rather than being applied
            // twice on the way down.
            next = visit(child, order, next, left, top, alpha, visitor);
        }
        return next;
    }
}
