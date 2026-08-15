package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.Frame;
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

        forEachBox(frame, root, (box, layout) -> {
            if ((box.background() >>> 24) != 0) {
                frame.fillRect(
                        layout.left(), layout.top(), layout.width(), layout.height(),
                        box.background());
            }
            if (box.text() != null) {
                // Wrapped at the width the layout pass settled on -- the same
                // width the measure function was last asked about, so the
                // paragraph's memo answers without re-wrapping and the lines
                // drawn are exactly the lines that were measured.
                box.text().paragraph().paint(
                        frame, layout.left(), layout.top(), layout.width(),
                        box.text().argb());
            }
        });
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
                visit(tree, boxes, 0, 0, 0, visitor);
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
        node.setPadding(Edge.ALL, box.padding());
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
    /// into absolute ones.
    ///
    /// @return the index after this subtree, so siblings stay aligned with the
    ///         list built during construction
    private static int visit(
            YogaNode node, List<Box> order, int index,
            double parentLeft, double parentTop,
            BiConsumer<Box, ComputedLayout> visitor) {

        var box = order.get(index);
        var layout = node.layout();
        var left = parentLeft + layout.left();
        var top = parentTop + layout.top();

        visitor.accept(box, new ComputedLayout(
                (float) left, (float) top, layout.width(), layout.height()));

        var next = index + 1;
        for (var child : node.children()) {
            next = visit(child, order, next, left, top, visitor);
        }
        return next;
    }
}
