package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Which node is under a point.
///
/// Hit testing runs against a **snapshot taken while painting**, not against a
/// fresh layout pass. That is not an optimization — it is the only correct
/// answer. A pointer event is about what the user can see, and what they can see
/// is the last frame that was painted. Laying out again to answer would test
/// against a frame that does not exist yet, and every drag would be one frame
/// ahead of the thing it is dragging.
public final class HitTest {

    private HitTest() {
    }

    /// One node's rectangle on screen, in logical coordinates.
    ///
    /// @param owner what the renderer tagged the box with — an
    ///              `Element` in the widget stack, or null for a box nobody
    ///              claimed
    /// @param cursor the shape the pointer takes over this rectangle (§7.3),
    ///               recorded here rather than looked up later because the
    ///               style that decided it is gone by the next frame
    public record Region(
            Object owner, Cursor cursor, float left, float top, float width, float height) {

        public Region {
            Objects.requireNonNull(cursor, "cursor");
        }

        /// A rectangle that asks for no particular cursor — which is most of
        /// them.
        public static Region of(Object owner, float left, float top, float width, float height) {
            return new Region(owner, Cursor.DEFAULT, left, top, width, height);
        }

        public boolean contains(float x, float y) {
            return x >= left && x < left + width && y >= top && y < top + height;
        }
    }

    /// Lays `root` out against `frame` and records every box's rectangle.
    ///
    /// In paint order — parents before children — which is what makes
    /// [#at] able to take the last match as the topmost.
    public static List<Region> capture(Frame frame, Box root) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(root, "root");
        var regions = new ArrayList<Region>();
        BoxPainter.forEachBox(frame, root, (box, layout) ->
                regions.add(new Region(box.owner(), box.cursor(), layout.left(), layout.top(),
                        layout.width(), layout.height())));
        return List.copyOf(regions);
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
