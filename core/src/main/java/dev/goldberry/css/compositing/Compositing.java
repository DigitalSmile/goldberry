package dev.goldberry.css.compositing;

import java.util.List;
import java.util.Objects;

/// How a box is drawn over what is under it: `filter`, `clip-path`,
/// `mix-blend-mode` and `isolation`. None of them inherits.
///
/// Any of them makes the box a group: it and everything under it are drawn
/// into a raster of their own, the filters are applied to that raster in order,
/// the clip is cut out of it, and it is composited back with the blend mode,
/// CSS's order. `isolation: isolate` asks
/// for the group alone, so that what blends inside it blends with the group
/// rather than with what is under the box.
///
/// @param clip    the part of the box that is drawn
/// @param blend   how the group combines with what is under it
/// @param isolate whether the box is a group even with nothing else to make it
///                one
/// @param filter  the `filter` list, in the order written; empty for `none`
public record Compositing(ClipPath clip, BlendMode blend, boolean isolate, List<FilterFunction> filter) {

    /// Drawn as it is, over what is under it.
    public static final Compositing NONE = new Compositing(ClipPath.NONE, BlendMode.NORMAL, false, List.of());

    public Compositing {
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(blend, "blend");
        filter = List.copyOf(Objects.requireNonNull(filter, "filter"));
    }

    /// Whether the box must be drawn as a group of its own.
    public boolean needsLayer() {
        return isolate || blend != BlendMode.NORMAL || !(clip instanceof ClipPath.None) || !filter.isEmpty();
    }

    /// How far the filters draw past the box and its children, on each side:
    /// `left`, `top`, `right`, `bottom`, in logical pixels.
    public double[] reach() {
        return FilterFunction.reach(filter);
    }

    /// This, clipped to `value`.
    public Compositing clip(ClipPath value) {
        return new Compositing(value, blend, isolate, filter);
    }

    /// This, blended by `value`.
    public Compositing blend(BlendMode value) {
        return new Compositing(clip, value, isolate, filter);
    }

    /// This, isolated or not.
    public Compositing isolate(boolean value) {
        return new Compositing(clip, blend, value, filter);
    }

    /// This, filtered by `value`.
    public Compositing filter(List<FilterFunction> value) {
        return new Compositing(clip, blend, isolate, value);
    }
}
