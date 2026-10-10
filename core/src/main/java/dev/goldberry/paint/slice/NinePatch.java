package dev.goldberry.paint.slice;

import java.util.List;
import java.util.Objects;

import dev.goldberry.css.Border;
import dev.goldberry.css.image.BorderImage;
import dev.goldberry.css.image.BorderImage.Extent;
import dev.goldberry.css.image.BorderImage.Repeat;
import dev.goldberry.css.image.NineSlice;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;

/// A picture cut in nine, for a painter to draw at any size: the four corners
/// kept whole, the four edges and the middle stretched or tiled between them.
///
/// ```java
/// private static final NinePatch PLATE = NinePatch.of(22, 64, 22, 64);
///
/// new Canvas((frame, size) -> frame.drawNineSlice(namePlate, PLATE, 0, 0, 300, 40));
/// ```
///
/// The stylesheet's `border-image` and this are one cut. What [NineSlice]
/// places for a box, it places here for a rectangle a painter names, with the
/// same rules: the slice lines are in the picture's pixels, each side is drawn
/// as wide as [#widths] says, and when two opposite sides would not fit they
/// all shrink by the same factor, so a corner keeps its shape. A picture
/// stretched to a plate 40 pixels high whose slices add up to 44 is drawn with
/// its ends a little smaller, not squashed.
///
/// A value with no native handle: make one once and draw it every frame.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#the-painter).
///
/// @param slice   the four lines into the picture, in from its top, right,
///                bottom and left edges: a [Length.Points] in the picture's
///                pixels at [#density], or a [Length.Percent] of its width or
///                height
/// @param widths  how wide each side's pieces are drawn, top, right, bottom and
///                left: a [Length.Points] in logical pixels, a
///                [Length.Percent] of the rectangle's height or width, or
///                [Length#AUTO] for the slice's own size at [#density]
/// @param fill    whether the middle is drawn
/// @param across  how the top and bottom edges and the middle fill their length
/// @param down    how the left and right edges and the middle fill theirs
/// @param density the picture's pixels per logical pixel: 1 for a picture drawn
///                for 100%, 2 for its `@2x` variant
public record NinePatch(Insets slice, Insets widths, boolean fill, Repeat across, Repeat down, int density) {

    public NinePatch {
        Objects.requireNonNull(slice, "slice");
        Objects.requireNonNull(widths, "widths");
        Objects.requireNonNull(across, "across");
        Objects.requireNonNull(down, "down");
        for (var line : sides(slice)) {
            if (!(line instanceof Length.Points points && points.value() >= 0)
                    && !(line instanceof Length.Percent percent && percent.value() >= 0)) {
                throw new IllegalArgumentException("a slice line is a number of pixels or a percentage, not " + line);
            }
        }
        for (var width : sides(widths)) {
            if (!(width instanceof Length.Points points && points.value() >= 0)
                    && !(width instanceof Length.Percent percent && percent.value() >= 0)
                    && width != Length.AUTO) {
                throw new IllegalArgumentException("a side is drawn a length, a percentage or auto wide, not " + width);
            }
        }
        if (density < 1) {
            throw new IllegalArgumentException("a picture has at least one pixel per logical pixel, not " + density);
        }
    }

    /// A picture cut `top`, `right`, `bottom` and `left` pixels in from its
    /// edges, each side drawn at the slice's own size, the middle drawn, and
    /// everything stretched.
    ///
    /// What a plate or a panel sprite wants: a 995 × 113 name plate cut
    /// `22 64 22 64` and drawn 300 × 40 keeps its ends whole and stretches the
    /// band between them.
    public static NinePatch of(int top, int right, int bottom, int left) {
        return new NinePatch(
                new Insets(pixels(top), pixels(right), pixels(bottom), pixels(left)),
                Insets.all(Length.AUTO),
                true,
                Repeat.STRETCH,
                Repeat.STRETCH,
                1);
    }

    /// This cut with each side drawn `top`, `right`, `bottom` and `left`
    /// logical pixels wide.
    public NinePatch withWidths(double top, double right, double bottom, double left) {
        return withWidths(new Insets(
                Length.points((float) top),
                Length.points((float) right),
                Length.points((float) bottom),
                Length.points((float) left)));
    }

    /// This cut with each side drawn as `value` says.
    public NinePatch withWidths(Insets value) {
        return new NinePatch(slice, value, fill, across, down, density);
    }

    /// This cut with its middle drawn or left out.
    public NinePatch withFill(boolean value) {
        return new NinePatch(slice, widths, value, across, down, density);
    }

    /// This cut with its edges and middle filled as `horizontal` and `vertical`
    /// say: stretched, tiled, or tiled a whole number of times.
    public NinePatch withRepeat(Repeat horizontal, Repeat vertical) {
        return new NinePatch(slice, widths, fill, horizontal, vertical, density);
    }

    /// This cut for a picture with `value` pixels per logical pixel. The slice
    /// lines are then read at that many pixels each, and a side drawn `auto` is
    /// as wide as its slice at 100%.
    public NinePatch atDensity(int value) {
        return new NinePatch(slice, widths, fill, across, down, value);
    }

    /// The pieces to draw for a picture `imageWidth` × `imageHeight` pixels
    /// over a rectangle `width` × `height`, in the rectangle's own logical
    /// coordinates. Corners first, then the edges, then the middle when it is
    /// drawn; a piece with nothing in it is left out.
    public List<NineSlice.Piece> pieces(int imageWidth, int imageHeight, double width, double height) {
        return NineSlice.pieces(borderImage(), imageWidth, imageHeight, density, Border.NONE, width, height);
    }

    /// The same cut as the stylesheet writes it: a border image with no source
    /// and no outsets, on a box with no border.
    private BorderImage borderImage() {
        return BorderImage.NONE
                .slice(new BorderImage.Slice(slice.top(), slice.right(), slice.bottom(), slice.left(), fill))
                .widths(sides(widths).stream().map(NinePatch::extent).toList())
                .repeat(across, down);
    }

    private static Extent extent(Length width) {
        return switch (width) {
            case Length.Points points -> new Extent(Extent.Kind.LENGTH, points.value());
            case Length.Percent percent -> new Extent(Extent.Kind.PERCENT, percent.value());
            case Length.Keyword _ -> Extent.AUTO;
        };
    }

    private static List<Length> sides(Insets insets) {
        return List.of(insets.top(), insets.right(), insets.bottom(), insets.left());
    }

    private static Length pixels(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("a slice line is not negative, and " + value + " is");
        }
        return Length.points(value);
    }
}
