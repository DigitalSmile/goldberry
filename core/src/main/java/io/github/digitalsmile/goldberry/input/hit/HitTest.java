package io.github.digitalsmile.goldberry.input.hit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.Clip;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// Which node is under a point.
///
/// Hit testing runs against a **snapshot taken while painting**, not against a
/// fresh layout pass. That is not an optimization — it is the only correct
/// answer. A pointer event is about what the user can see, and what they can see
/// is the last frame that was painted. Laying out again to answer would test
/// against a frame that does not exist yet, and every drag would be one frame
/// ahead of the thing it is dragging.
public final class HitTest {

    private HitTest() {}

    /// One node's rectangle on screen, in logical coordinates.
    ///
    /// ## Why the *inverse* is stored and not the transform
    ///
    /// A transformed box is drawn by mapping its rectangle forward through a
    /// matrix. Asking whether a pointer is inside it is the opposite question, and
    /// the opposite question has the cleaner answer: map the **pointer** backward
    /// into the box's own coordinates and test it against the plain rectangle. The
    /// alternative — mapping the four corners forward and testing point-in-polygon
    /// — is more arithmetic, is wrong for a box that has been sheared into a
    /// non-convex screen region under nested transforms, and would be a second
    /// implementation of geometry the painter already did.
    ///
    /// So the inverse is computed **once, while painting**, from the very matrix
    /// that was handed to Blend2D. Not recomputed on the input path from the same
    /// inputs by different code: two inversions that have to agree exactly is how
    /// a pointer starts landing where the ink is not, and the failure is silent —
    /// the control looks right and simply does not respond where it looks like it
    /// should
    /// (ADR-0068).
    ///
    /// Null for an untransformed box, which is almost all of them, and the test
    /// then costs the four comparisons it always did.
    ///
    /// @param owner   what the renderer tagged the box with — an `Element` in the
    ///                widget stack, or null for a box nobody claimed
    /// @param cursor  the shape the pointer takes over this rectangle (§7.3),
    ///                recorded here rather than looked up later because the style
    ///                that decided it is gone by the next frame
    /// @param inverse undoes the transform this box was painted with, or null if
    ///                it was painted where it was laid out
    /// @param clip    what an `overflow` above this box confines it to, in the
    ///                frame's coordinates, or [Clip#NONE] when nothing does
    /// @param content the rectangle **inside the padding** — where a `canvas`
    ///                painter's own origin is, and where its input has to land
    ///                (ADR-0281). The same as the box's own rectangle for a box
    ///                with no padding, which is most of them
    public record Region(
            Object owner,
            Cursor cursor,
            float left,
            float top,
            float width,
            float height,
            Affine inverse,
            Clip clip,
            LogicalRect content) {

        public Region {
            Objects.requireNonNull(cursor, "cursor");
            Objects.requireNonNull(clip, "clip");
            content = content == null ? LogicalRect.of(left, top, width, height) : content;
        }

        /// A region whose content box is its whole rectangle.
        public Region(
                Object owner,
                Cursor cursor,
                float left,
                float top,
                float width,
                float height,
                Affine inverse,
                Clip clip) {
            this(owner, cursor, left, top, width, height, inverse, clip, LogicalRect.of(left, top, width, height));
        }

        /// A rectangle nothing clips.
        public Region(
                Object owner,
                Cursor cursor,
                float left,
                float top,
                float width,
                float height,
                @Nullable Affine inverse) {
            this(owner, cursor, left, top, width, height, inverse, Clip.NONE);
        }

        /// An untransformed rectangle.
        public Region(Object owner, Cursor cursor, float left, float top, float width, float height) {
            this(owner, cursor, left, top, width, height, null, Clip.NONE);
        }

        /// A rectangle that asks for no particular cursor — which is most of
        /// them.
        public static Region of(Object owner, float left, float top, float width, float height) {
            return new Region(owner, Cursor.DEFAULT, left, top, width, height, null);
        }

        /// Where this box was **painted**, which is not where it was laid out
        /// when something above it was transformed.
        ///
        /// [#bounds()] is the layout rectangle. Anything reasoning about what the
        /// user can *see* wants this one instead: a row inside a scrolled list is
        /// laid out where it always was and drawn a long way from there
        /// (ADR-0123).
        ///
        /// **A popup anchors to this one too**, which it did not always: a menu
        /// belongs under where its button was drawn, and the flow rectangle only
        /// agreed with that until something scrolled ([ADR-0270]).
        ///
        /// A region stores the **inverse** of its matrix, because undoing a
        /// transform is what hit testing needs and inverting once while painting
        /// is what stops two inversions disagreeing (ADR-0068). Going forwards
        /// means inverting it back, which is exact for the translations this is
        /// ever asked about.
        public LogicalRect painted() {
            if (inverse == null) {
                return bounds();
            }
            var forward = inverse.invert();
            if (forward == null) {
                return bounds();
            }
            return LogicalRect.of(
                    (float) (forward.a() * left + forward.c() * top + forward.e()),
                    (float) (forward.b() * left + forward.d() * top + forward.f()),
                    (float) (forward.a() * width),
                    (float) (forward.d() * height));
        }

        /// Whether **any** of this box survived the clip above it.
        ///
        /// [#contains] asks this of one point; this asks it of the rectangle, and
        /// it is the question a popup has to keep asking about the anchor it is
        /// following. A row scrolled past the top of its viewport is still laid
        /// out where it always was and still reported here — the walk only stops
        /// at a subtree whose *own* clip went empty, and a row is not one
        /// ([ADR-0114]) — so a menu hanging off it would go on pointing at a
        /// widget nobody can see ([ADR-0433]).
        ///
        /// **The clip, and not the window.** [Clip#NONE] is what a box with no
        /// clipping ancestor is painted under, and it admits everything: this
        /// answers "has it left the viewport that confines it", which is what a
        /// scroll does to an anchor, and says nothing about a box that left the
        /// window by some other route. The window is the caller's to know.
        ///
        /// A box of no area is not visible either, which falls out of the
        /// half-open arithmetic rather than being a case.
        public boolean isVisible() {
            var rect = painted();
            return !clip.intersect(Clip.of(rect.left(), rect.top(), rect.width(), rect.height()))
                    .isEmpty();
        }

        /// This rectangle as a plain one, in the window's logical coordinates.
        ///
        /// The rectangle **layout** produced, without the owner, the cursor or
        /// the transform. Not what a popup anchors to: see [#painted()], which
        /// is the same rectangle for every box nothing transformed and the right
        /// one for the boxes something did ([ADR-0270]).
        public LogicalRect bounds() {
            return LogicalRect.of(left, top, width, height);
        }

        public boolean contains(float x, float y) {
            // The clip first, and in the **frame's** coordinates rather than the
            // box's. A row scrolled out of its viewport is still laid out where
            // it always was -- Yoga never saw the scroll, which is a transform
            // on the content -- so its own rectangle happily contains a pointer
            // that is nowhere near it on screen. Testing the clip is what makes
            // "not visible" and "not clickable" the same thing, which is
            // ARCHITECTURE §11's promise and was not true of anything before
            // this (ADR-0114).
            if (!clip.isNone() && !clip.contains(x, y)) {
                return false;
            }
            if (inverse == null) {
                return x >= left && x < left + width && y >= top && y < top + height;
            }
            // Back into the coordinates the box was laid out in, where it is
            // still the axis-aligned rectangle Yoga produced.
            var localX = inverse.mapX(x, y);
            var localY = inverse.mapY(x, y);
            return localX >= left && localX < left + width && localY >= top && localY < top + height;
        }
    }

    /// Lays `root` out against `frame` and records every box's rectangle.
    ///
    /// In paint order — parents before children — which is what makes
    /// [#at] able to take the last match as the topmost.
    ///
    /// A box whose transform cannot be inverted is **dropped**. That is
    /// `scale(0)` and the handful of matrices like it: they collapse the box to a
    /// line or a point, so there is nothing on screen for a pointer to be inside
    /// of, and every point in the plane would otherwise map into it.
    public static List<Region> capture(Frame frame, Box root) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(root, "root");
        var regions = new ArrayList<Region>();
        BoxPainter.forEachPlacedBox(frame, root, placed -> collect(regions, placed));
        return List.copyOf(regions);
    }

    /// The rectangles of the tree `tree` last laid out.
    ///
    /// **This is the form an application wants.** The `(Frame, Box)` overload
    /// lays the tree out again to answer, so a window using it pays for two full
    /// layout passes per frame — one to paint and one to know where it painted.
    /// A [RenderTree] has already done the pass, and both questions read the same
    /// answer (ADR-0069).
    ///
    /// @throws IllegalStateException if the tree has never been updated
    public static List<Region> capture(RenderTree tree) {
        Objects.requireNonNull(tree, "tree");
        var regions = new ArrayList<Region>();
        tree.forEachPlacedBox(placed -> collect(regions, placed));
        return List.copyOf(regions);
    }

    private static void collect(List<Region> regions, BoxPainter.Placed placed) {
        var layout = placed.layout();
        Affine inverse = null;
        if (!placed.isPlain()) {
            inverse = placed.transform().invert();
            if (inverse == null) {
                return;
            }
        }
        // The content box, resolved exactly as `BoxPainter.paintCanvas` resolves
        // it -- through `Length.resolve`, which is where that arithmetic lives so
        // that the painter and this cannot drift (ADR-0281).
        var padding = placed.box().padding();
        var left = Length.resolve(padding.left(), layout.width());
        var top = Length.resolve(padding.top(), layout.height());
        var right = Length.resolve(padding.right(), layout.width());
        var bottom = Length.resolve(padding.bottom(), layout.height());
        regions.add(new Region(
                placed.box().owner(),
                placed.box().cursor(),
                layout.left(),
                layout.top(),
                layout.width(),
                layout.height(),
                inverse,
                placed.clip(),
                LogicalRect.of(
                        layout.left() + left,
                        layout.top() + top,
                        Math.max(0, layout.width() - left - right),
                        Math.max(0, layout.height() - top - bottom))));
    }

    /// The topmost node containing `(x, y)`, in logical coordinates.
    ///
    /// Scanned backwards, because [#capture] records parents before children and
    /// the later a box was painted the more of it the user can see. A box with no
    /// owner is skipped rather than returned: it is scenery, and a pointer event
    /// delivered to it would have nowhere to go.
    public static Optional<Object> at(List<Region> regions, float x, float y) {
        Objects.requireNonNull(regions, "regions");
        for (var i = regions.size() - 1; i >= 0; i--) {
            var region = regions.get(i);
            if (region.owner() != null && region.contains(x, y)) {
                return Optional.of(region.owner());
            }
        }
        return Optional.empty();
    }

    /// The shape the pointer should take at `(x, y)`.
    ///
    /// Scanned backwards like [#at], and the first rectangle that asks for
    /// something other than [Cursor#DEFAULT] wins — so a `cursor: pointer` on a
    /// button applies to the label inside it without the label having to repeat
    /// it. That is inheritance in the sense a user means it, arrived at by
    /// walking the stack of rectangles rather than the element tree, which is the
    /// only structure input has at this point (ADR-0054).
    ///
    /// A box with no owner still counts. Scenery is not clickable, but it is
    /// visible, and a decorative overlay that says `cursor: wait` means it.
    public static Cursor cursorAt(List<Region> regions, float x, float y) {
        Objects.requireNonNull(regions, "regions");
        for (var i = regions.size() - 1; i >= 0; i--) {
            var region = regions.get(i);
            if (region.cursor() != Cursor.DEFAULT && region.contains(x, y)) {
                return region.cursor();
            }
        }
        return Cursor.DEFAULT;
    }
}
