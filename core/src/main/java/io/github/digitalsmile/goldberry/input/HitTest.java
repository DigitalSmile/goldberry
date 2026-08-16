package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.Frame;
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
    public record Region(Object owner, float left, float top, float width, float height) {

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
                regions.add(new Region(box.owner(), layout.left(), layout.top(),
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
}
