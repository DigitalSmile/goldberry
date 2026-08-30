package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;

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

    private BoxPainter() {}

    /// Lays `root` out to fill `frame` and paints it, keeping nothing.
    ///
    /// The **one-shot** form: it builds a [RenderTree], uses it once and closes
    /// it. Right for a golden image, a test or anything else that paints a tree a
    /// single time, and wrong for a window — an application that paints sixty
    /// times a second wants one `RenderTree` held for the life of the window, so
    /// that Yoga's nodes, its layout cache and the measure callbacks survive
    /// between frames
    /// (ADR-0069).
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
                    frame.transform(matrix.a(), matrix.b(), matrix.c(), matrix.d(), matrix.e(), matrix.f());
                    current[0] = matrix;
                }
                paintOne(frame, path, placed.box(), placed.layout(), matrix);
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
    /// Public because [io.github.digitalsmile.goldberry.paint.tree.RenderTree] is
    /// in a package of its own now: the render tree paints one retained box at a
    /// time and this is the single-box painter it calls. See
    /// ADR-0172.
    ///
    /// For a box drawn where it was laid out. A box under a `transform` — every
    /// box inside a `scroll`, whose content is translated rather than moved —
    /// goes through [#paintOne(Frame, BlendPath, Box, ComputedLayout, Affine)].
    public static void paintOne(Frame frame, BlendPath path, Box box, ComputedLayout layout) {
        paintOne(frame, path, box, layout, Affine.IDENTITY);
    }

    /// The same, told **what matrix the context is already carrying**.
    ///
    /// Every call here draws in the context's current user space, so the ambient
    /// matrix is already applied to all of them and none of them needs to know
    /// it — with one exception. A `canvas` sets a transform of its own, to move
    /// the origin to its content corner, and
    /// [Frame#transform] *assigns* rather than composes
    /// (ADR-0068):
    /// the six numbers replace whatever was there. So a canvas that spelled its
    /// own translation alone would **discard its ancestors'** — and a chart
    /// inside a `scroll` would stay where it was laid out while the panel moved
    /// under it, correctly clipped to a viewport it was no longer drawn in.
    ///
    /// @param ambient what the frame's transform was set to before this box —
    ///                [Affine#IDENTITY] for the overwhelming majority of boxes
    public static void paintOne(Frame frame, BlendPath path, Box box, ComputedLayout layout, Affine ambient) {
        var decoration = box.decoration();
        var x = layout.left();
        var y = layout.top();
        var width = layout.width();
        var height = layout.height();

        if ((box.background() >>> 24) != 0) {
            if (!decoration.corners().isSquare()) {
                path.reset();
                RoundRect.addTo(path, 0, 0, width, height, decoration.corners());
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
            RoundRect.addTo(
                    path,
                    inset,
                    inset,
                    width - decoration.borderWidth(),
                    height - decoration.borderWidth(),
                    decoration.corners().shrunkBy(inset));
            frame.strokePath(
                    x,
                    y,
                    path,
                    decoration.borderWidth(),
                    BlendStrokeCap.BUTT,
                    BlendStrokeJoin.MITER_CLIP,
                    decoration.borderColor());
        }

        if (box.text() != null) {
            // **Inside the padding.** Yoga sizes a measured leaf as its measured
            // content *plus* its padding, so a box with `padding: 4px 8px` around
            // text is 16px wider than its text -- and painting at the box's own
            // origin puts every one of those pixels on the right and the bottom,
            // with the text hanging off the top-left corner. A tooltip was the
            // first widget in the catalog to put padding on a text box rather
            // than on a container around one, and it looked exactly like that
            // (ADR-0111).
            //
            // Wrapped at the width the layout pass settled on, less the padding:
            // the same width the measure function was last asked about, so the
            // paragraph's memo answers without re-wrapping and the lines drawn
            // are exactly the lines that were measured.
            var left = resolve(box.padding().left(), width);
            var top = resolve(box.padding().top(), height);
            var right = resolve(box.padding().right(), width);
            box.text()
                    .paragraph()
                    .paint(
                            frame,
                            x + left,
                            y + top,
                            Math.max(0, width - left - right),
                            box.text().argb());
        }

        if (box.icon() != null) {
            // **Centred in the box**, which is a no-op in the common case and the
            // whole point in the other one. `Box.icon` sizes the box to the icon,
            // so the two are usually the same rectangle and this offset is zero.
            // Where a stylesheet said otherwise -- `item-lead` is 16 square,
            // because a menu's leading column has to be one width whether it
            // holds a tick or an icon (ADR-0113) -- the icon is whatever size the
            // application built it, and drawing it at the corner put a 20px glyph
            // 4px above and left of the tick it lines up with. An icon parked in
            // the corner of its slot is the report "the row with the icon looks
            // wrong"; centring is what a slot means
            // (ADR-0143).
            var glyph = box.icon().icon().size();
            box.icon()
                    .icon()
                    .draw(
                            frame,
                            x + (width - glyph) / 2,
                            y + (height - glyph) / 2,
                            box.icon().argb());
        }

        if (box.mark() != null) {
            paintMark(frame, path, box.mark(), x, y, width, height);
        }

        if (box.painting() != null) {
            paintCanvas(frame, box, x, y, width, height, ambient);
        }

        if (decoration.hasOutline()) {
            // Outward by the offset plus half the width, so the *inside* edge of
            // the ring sits exactly `outline-offset` from the border box —
            // which is what a 2px ring at a 2px offset means, and what makes two
            // controls 8px apart not have their rings touch.
            var out = decoration.outlineOffset() + decoration.outlineWidth() / 2;
            path.reset();
            RoundRect.addTo(
                    path,
                    -out,
                    -out,
                    width + out * 2,
                    height + out * 2,
                    // A ring around a rounded corner is concentric with it, so
                    // its radius grows by the same distance it moved out. A
                    // square corner stays square: a ring that rounded itself
                    // around a sharp box would not follow the control.
                    decoration.corners().grownBy(out));
            frame.strokePath(
                    x,
                    y,
                    path,
                    decoration.outlineWidth(),
                    BlendStrokeCap.BUTT,
                    BlendStrokeJoin.MITER_CLIP,
                    decoration.outlineColor());
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
            Frame frame, BlendPath path, Box.Mark mark, double x, double y, double width, double height) {

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
            case CHEVRON_END -> {
                // Narrower than it is tall, like the glyph: a chevron drawn to a
                // square box reads as an arrowhead.
                path.moveTo(width * 0.38, height * 0.26);
                path.lineTo(width * 0.66, height * 0.5);
                path.lineTo(width * 0.38, height * 0.74);
            }
            case CHEVRON_DOWN -> {
                // The same drawing as CHEVRON_END with the axes swapped, so a
                // chevron in a select and a chevron in a menu are the same
                // glyph seen from two sides rather than two glyphs.
                path.moveTo(width * 0.26, height * 0.38);
                path.lineTo(width * 0.5, height * 0.66);
                path.lineTo(width * 0.74, height * 0.38);
            }
            case CHEVRON_UP -> {
                // CHEVRON_DOWN reflected about the box's middle, so an ascending
                // caret and a descending one are the same glyph either way up --
                // a sort indicator whose two states were different weights would
                // read as two different marks.
                path.moveTo(width * 0.26, height * 0.62);
                path.lineTo(width * 0.5, height * 0.34);
                path.lineTo(width * 0.74, height * 0.62);
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
                Arc.addTo(
                        path,
                        width / 2,
                        height / 2,
                        Math.min(width, height) / 2 - mark.thickness() / 2,
                        mark.start(),
                        mark.sweep());
            }
            case POINTER -> {
                // A line out from the middle at the mark's angle -- which way the
                // knob is turned. The angles are the mark's for the same reason
                // an arc's are: this is a shape whose geometry *is* a value
                // (ADR-0089).
                var radius = Math.min(width, height) / 2;
                var cos = Math.cos(mark.start());
                var sin = Math.sin(mark.start());
                path.moveTo(
                        width / 2 + radius * Box.Mark.POINTER_INNER * cos,
                        height / 2 + radius * Box.Mark.POINTER_INNER * sin);
                path.lineTo(
                        width / 2 + radius * Box.Mark.POINTER_OUTER * cos,
                        height / 2 + radius * Box.Mark.POINTER_OUTER * sin);
            }
            case DOT -> {
                // Filled rather than stroked, so a radio's dot is solid at any
                // size instead of becoming a ring as the box grows.
                var radius = Math.min(width, height) * 0.25;
                RoundRect.addTo(path, width / 2 - radius, height / 2 - radius, radius * 2, radius * 2, radius);
                frame.fillPath(x, y, path, mark.argb());
                return;
            }
            // The four enclosed glyphs. Each is a stroked outline with a symbol
            // inside it, and three of the four end in a **dot** -- which is a
            // filled circle rather than part of the stroke, because a zero-length
            // subpath is not reliably a round cap. So they draw themselves and
            // return, the way DOT does.
            case CIRCLE_INFO -> {
                enclosure(path, width, height, mark);
                // The stem of the `i`, below its dot: Lucide's info runs 16 -> 12
                // in the 24 box, which is 0.67 -> 0.5 of the height.
                path.moveTo(width * 0.5, height * 0.67);
                path.lineTo(width * 0.5, height * 0.5);
                strokeMark(frame, path, mark, x, y);
                dot(frame, path, mark, x, y, width * 0.5, height * 0.33);
                return;
            }
            case CIRCLE_CHECK -> {
                enclosure(path, width, height, mark);
                // Lucide's `m9 12 2 2 4-4`, in the same proportions the bare
                // CHECK uses -- tucked in, because this tick has a ring round it
                // and the bare one has the whole box.
                path.moveTo(width * 0.375, height * 0.5);
                path.lineTo(width * 0.458, height * 0.583);
                path.lineTo(width * 0.625, height * 0.417);
                strokeMark(frame, path, mark, x, y);
                return;
            }
            case CIRCLE_ALERT -> {
                enclosure(path, width, height, mark);
                // The bar of the `!`, above its dot -- CIRCLE_INFO upside down,
                // which is exactly what Lucide draws and is why the two are
                // legible as different things at 20px.
                path.moveTo(width * 0.5, height * 0.33);
                path.lineTo(width * 0.5, height * 0.5);
                strokeMark(frame, path, mark, x, y);
                dot(frame, path, mark, x, y, width * 0.5, height * 0.67);
                return;
            }
            case TRIANGLE_ALERT -> {
                enclosure(path, width, height, mark);
                path.moveTo(width * 0.5, height * 0.375);
                path.lineTo(width * 0.5, height * 0.542);
                strokeMark(frame, path, mark, x, y);
                dot(frame, path, mark, x, y, width * 0.5, height * 0.71);
                return;
            }
        }
        // Round caps and joins: the tick's corner is the one place in the toolkit
        // where a mitre would put a spike outside the 16px glyph.
        strokeMark(frame, path, mark, x, y);
    }

    /// The ring or the triangle an enclosed glyph sits in, appended to `path`.
    ///
    /// Inset by half the stroke for [Box.Mark.Kind#ARC]'s reason: the outline's
    /// *outer* edge is the box, so a 20px banner glyph draws inside its 20px slot
    /// instead of a stroke's width outside it all the way round.
    private static void enclosure(BlendPath path, double width, double height, Box.Mark mark) {
        var inset = mark.thickness() / 2;
        if (mark.kind() == Box.Mark.Kind.TRIANGLE_ALERT) {
            // Lucide's triangle-alert has rounded corners drawn as arcs; a round
            // *join* gives the same reading at 20px for three line segments
            // instead of six curves. The apex is high and the base wide, because
            // a triangle inscribed in its box is what says "not a circle" at a
            // glance -- which is the whole job of this kind.
            path.moveTo(width * 0.5, inset);
            path.lineTo(width - inset, height - inset);
            path.lineTo(inset, height - inset);
            path.closeSubPath();
            return;
        }
        Arc.addTo(path, width / 2, height / 2, Math.min(width, height) / 2 - inset, 0, 2 * Math.PI);
    }

    /// The dot under an `i` or over a `!`.
    ///
    /// Filled at the stroke's own radius, so it reads as the same pen that drew
    /// the bar above it. Resets the path first: the outline and the symbol have
    /// already been stroked by the time this runs.
    private static void dot(Frame frame, BlendPath path, Box.Mark mark, double x, double y, double cx, double cy) {

        var radius = mark.thickness() / 2;
        path.reset();
        RoundRect.addTo(path, cx - radius, cy - radius, radius * 2, radius * 2, radius);
        frame.fillPath(x, y, path, mark.argb());
    }

    /// Strokes whatever is in `path` as a mark — see the note on caps and joins
    /// at the end of [#paintMark].
    private static void strokeMark(Frame frame, BlendPath path, Box.Mark mark, double x, double y) {
        frame.strokePath(x, y, path, mark.thickness(), BlendStrokeCap.ROUND, BlendStrokeJoin.ROUND, mark.argb());
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
    /// A padding edge in pixels.
    ///
    /// Percentages are resolved against `base`, which is CSS's rule for padding —
    /// *every* edge is a percentage of the containing block's **width**, and the
    /// caller passes the right base rather than this guessing. Anything that is
    /// not a number is nothing: `auto` padding does not exist and `undefined`
    /// means none.
    private static double resolve(StyleLength length, double base) {
        return switch (length) {
            case StyleLength.Points points -> points.value();
            case StyleLength.Percent percent -> percent.value() / 100.0 * base;
            case StyleLength.Keyword ignored -> 0;
        };
    }

    /// Hands the frame to an application's own painter — §1's `canvas`.
    ///
    /// Three things happen around the call and each is load-bearing.
    ///
    /// **Saved and restored**, because this is the one painter in the toolkit
    /// that is not trusted to unset what it set. `resetClip` would not do: it
    /// goes back to the whole frame rather than to the clip in force before, so a
    /// canvas inside a `scroll` would paint over the viewport's edge — which is
    /// the whole reason `bl_context_save` is on the export list
    /// (ADR-0193).
    ///
    /// **Clipped to the content box**, so a painter's arithmetic mistake is a
    /// picture that is wrong inside its own rectangle rather than one that has
    /// drawn over the rest of the window.
    ///
    /// **Translated**, so the painter works in its own coordinates from `(0, 0)`
    /// and never has to know where the layout put it. That is what makes the same
    /// painter usable in a `row`, in a `scroll` and in a golden test.
    ///
    /// Inside the padding, for [ADR-0111]'s reason: a box with `padding: 8px`
    /// around a canvas means eight pixels of surface, and painting at the box's
    /// own origin would put all of them on the right and the bottom.
    private static void paintCanvas(
            Frame frame, Box box, double x, double y, double width, double height, Affine ambient) {

        var left = resolve(box.padding().left(), width);
        var top = resolve(box.padding().top(), height);
        var right = resolve(box.padding().right(), width);
        var bottom = resolve(box.padding().bottom(), height);
        var contentWidth = width - left - right;
        var contentHeight = height - top - bottom;
        if (!(contentWidth > 0) || !(contentHeight > 0)) {
            // A canvas laid out to nothing is not an error -- a collapsed split
            // pane or a zero-height row produces one -- and `clipTo` refuses a
            // non-positive size, so the painter is simply not called.
            return;
        }

        frame.save();
        try {
            // The clip first, and in the *ambient* space: a clip lands in the
            // context's current user space, which is the space this box's own
            // rectangle is written in. So a canvas inside a scrolled subtree is
            // clipped where it is drawn, and the viewport's clip -- set at
            // identity, outside the walk -- still cuts it, because Blend2D
            // intersects.
            frame.clipTo(x + left, y + top, contentWidth, contentHeight);
            // Then the painter's own origin, **composed onto what was already
            // there** rather than assigned over it: translate the painter's
            // (0, 0) to the content corner, then everything the ancestors do.
            var painting = Affine.translate(x + left, y + top).then(ambient);
            frame.transform(painting.a(), painting.b(), painting.c(), painting.d(), painting.e(), painting.f());
            box.painting()
                    .paint(
                            frame,
                            new io.github.digitalsmile.goldberry.render.model.LogicalSize(
                                    (float) contentWidth, (float) contentHeight));
        } finally {
            // In a finally, because a painter that throws is an application bug
            // and must not also be a window that draws wrong from then on. The
            // exception still propagates: swallowing it would make a canvas that
            // silently drew nothing, which is worse to diagnose than a stack
            // trace.
            frame.restore();
        }
    }

    /// @param clip what an `overflow` above this box confines it to, or
    ///             [Clip#NONE] when nothing does — which is every box in a tree
    ///             with no scroll view in it
    public record Placed(Box box, ComputedLayout layout, Affine transform, Clip clip) {

        public Placed {
            Objects.requireNonNull(box, "box");
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(clip, "clip");
        }

        /// An unclipped box, which is what a caller building one by hand means.
        public Placed(Box box, ComputedLayout layout, Affine transform) {
            this(box, layout, transform, Clip.NONE);
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
