package io.github.digitalsmile.goldberry.render;

import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// Where a frame placed a GPU layer: what
/// [Frame#gpuLayer][io.github.digitalsmile.goldberry.paint.Frame#gpuLayer]
/// records, in paint order, for the compositor (`docs/gpu-plan.md`, D4; ADR-0481).
///
/// Both rectangles are in the frame's physical pixels and rounded by one rule,
/// each edge to the nearest pixel, so the hole punched in the frame and the quad
/// the compositor draws cover the same pixels.
///
/// @param content what is drawn
/// @param target  the rectangle the content fills, whole: its size is the size
///                the content is rendered at. It may reach outside the frame
/// @param scissor the part of `target` that can be seen, inside the frame and
///                inside every clip the content was painted under; never empty
public record GpuPlacement(GpuContent content, PhysicalRect target, PhysicalRect scissor) {

    /// Checks the scissor is a non-empty part of the target.
    ///
    /// @throws IllegalArgumentException when the target or the scissor is
    ///                                  empty, or the scissor reaches outside
    ///                                  the target
    public GpuPlacement {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(scissor, "scissor");
        if (target.isEmpty() || scissor.isEmpty()) {
            throw new IllegalArgumentException(
                    "a placed GPU layer covers something: target " + target + ", scissor " + scissor);
        }
        if (scissor.x() < target.x()
                || scissor.y() < target.y()
                || scissor.right() > target.right()
                || scissor.bottom() > target.bottom()) {
            throw new IllegalArgumentException("the scissor " + scissor + " reaches outside the target " + target);
        }
    }

    /// Whether the visible part of this layer shares a pixel with `rect`.
    public boolean overlaps(DamageRect rect) {
        return scissor.x() < rect.x() + rect.width()
                && rect.x() < scissor.right()
                && scissor.y() < rect.y() + rect.height()
                && rect.y() < scissor.bottom();
    }
}
