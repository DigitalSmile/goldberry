package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import java.util.Objects;

/// A rectangle with a flexbox style and children — the smallest thing that can
/// be laid out and painted.
///
/// This is not the widget model. The three-tree design in ADR-0004 is still
/// open, and inventing it here would be inventing it twice. What this is: the
/// join between the two engines that were bound separately — Yoga decides where
/// the rectangles go, Blend2D draws them — so that the seam between them is
/// exercised by something before the widget layer lands on top of it.
///
/// Immutable, and built by chaining: every method returns a new box. A tree of
/// these is a value, which is what makes it safe to hold one across frames and
/// what the eventual widget tree will also be.
public record Box(
        int background,
        FlexDirection direction,
        Justify justifyContent,
        Align alignItems,
        StyleLength width,
        StyleLength height,
        StyleLength padding,
        StyleLength gap,
        double flexGrow,
        List<Box> children) {

    /// Fully transparent — a box that lays out and paints nothing.
    public static final int TRANSPARENT = 0x00000000;

    public Box {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(justifyContent, "justifyContent");
        Objects.requireNonNull(alignItems, "alignItems");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(padding, "padding");
        Objects.requireNonNull(gap, "gap");
        if (!Double.isFinite(flexGrow) || flexGrow < 0) {
            throw new IllegalArgumentException("flex-grow must be a non-negative number");
        }
        children = List.copyOf(children == null ? List.of() : children);
    }

    /// An empty box that takes its size from its style and fills nothing.
    public static Box of() {
        return new Box(
                TRANSPARENT,
                FlexDirection.ROW,
                Justify.FLEX_START,
                Align.STRETCH,
                StyleLength.UNDEFINED,
                StyleLength.UNDEFINED,
                StyleLength.points(0),
                StyleLength.points(0),
                0,
                List.of());
    }

    /// A box filled with `argb` — `0xAARRGGBB`, not premultiplied, exactly as
    /// [io.github.digitalsmile.goldberry.Frame] takes it.
    public static Box filled(int argb) {
        return of().background(argb);
    }

    public Box background(int argb) {
        return new Box(argb, direction, justifyContent, alignItems, width, height,
                padding, gap, flexGrow, children);
    }

    public Box direction(FlexDirection value) {
        return new Box(background, value, justifyContent, alignItems, width, height,
                padding, gap, flexGrow, children);
    }

    public Box justifyContent(Justify value) {
        return new Box(background, direction, value, alignItems, width, height,
                padding, gap, flexGrow, children);
    }

    public Box alignItems(Align value) {
        return new Box(background, direction, justifyContent, value, width, height,
                padding, gap, flexGrow, children);
    }

    public Box size(StyleLength w, StyleLength h) {
        return new Box(background, direction, justifyContent, alignItems, w, h,
                padding, gap, flexGrow, children);
    }

    public Box padding(StyleLength value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                value, gap, flexGrow, children);
    }

    public Box gap(StyleLength value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                padding, value, flexGrow, children);
    }

    /// Share of the free space this box takes along its parent's main axis.
    public Box grow(double value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                padding, gap, value, children);
    }

    public Box children(Box... value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                padding, gap, flexGrow, List.of(value));
    }
}
