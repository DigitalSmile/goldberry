package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Paragraph;
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
        Text text,
        List<Box> children) {

    /// Text filling a box, and the colour to draw it in.
    ///
    /// One component rather than two on [Box], because they are meaningless
    /// apart: a paragraph with no colour cannot be drawn and a colour with no
    /// paragraph is not text.
    ///
    /// @param paragraph the text, already shaped
    /// @param argb      `0xAARRGGBB`, not premultiplied
    public record Text(Paragraph paragraph, int argb) {
        public Text {
            Objects.requireNonNull(paragraph, "paragraph");
        }
    }

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
        // Yoga asks a measured node for its size and never lays its children
        // out, so a box that is both would silently lose them. Refused here
        // rather than at layout time, where the box that caused it is harder to
        // find. Text beside other content is a child box holding the text.
        if (text != null && !children.isEmpty()) {
            throw new IllegalArgumentException(
                    "a box with text may not also have " + children.size() + " child(ren):"
                            + " its size comes from the text, so Yoga would never lay the"
                            + " children out. Put the text in a child of its own.");
        }
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
                null,
                List.of());
    }

    /// A box whose size comes from the text in it.
    ///
    /// The box becomes a **measured leaf**: Yoga proposes a width, the paragraph
    /// wraps at it, and the height that comes back is what the flexbox algorithm
    /// sizes the box around. That is the whole reason text can take part in
    /// layout rather than being drawn over the top of it.
    ///
    /// @param argb `0xAARRGGBB`, not premultiplied
    public static Box text(Paragraph paragraph, int argb) {
        return of().text(new Text(paragraph, argb));
    }

    public Box text(Text value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                padding, gap, flexGrow, value, children);
    }

    /// A box filled with `argb` — `0xAARRGGBB`, not premultiplied, exactly as
    /// [io.github.digitalsmile.goldberry.Frame] takes it.
    public static Box filled(int argb) {
        return of().background(argb);
    }

    public Box background(int argb) {
        return new Box(argb, direction, justifyContent, alignItems, width, height,
                padding, gap, flexGrow, text, children);
    }

    public Box direction(FlexDirection value) {
        return new Box(background, value, justifyContent, alignItems, width, height,
                padding, gap, flexGrow, text, children);
    }

    public Box justifyContent(Justify value) {
        return new Box(background, direction, value, alignItems, width, height,
                padding, gap, flexGrow, text, children);
    }

    public Box alignItems(Align value) {
        return new Box(background, direction, justifyContent, value, width, height,
                padding, gap, flexGrow, text, children);
    }

    public Box size(StyleLength w, StyleLength h) {
        return new Box(background, direction, justifyContent, alignItems, w, h,
                padding, gap, flexGrow, text, children);
    }

    public Box padding(StyleLength value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                value, gap, flexGrow, text, children);
    }

    public Box gap(StyleLength value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                padding, value, flexGrow, text, children);
    }

    /// Share of the free space this box takes along its parent's main axis.
    public Box grow(double value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                padding, gap, value, text, children);
    }

    public Box children(Box... value) {
        return new Box(background, direction, justifyContent, alignItems, width, height,
                padding, gap, flexGrow, text, List.of(value));
    }
}
