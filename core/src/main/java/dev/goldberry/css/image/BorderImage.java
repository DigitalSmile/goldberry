package dev.goldberry.css.image;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Border;
import dev.goldberry.layout.Length;

/// A box's edge drawn from a picture cut in nine — CSS's `border-image`.
///
/// ```css
/// #menu-panel { border-image: url("classpath:/ui/panel.png") 48 fill / 48px stretch }
/// button:hover { border-image-source: url("classpath:/ui/button-hover.png") }
/// ```
///
/// The picture is cut by four [#slice] lines into four corners, four edges and
/// a middle. The corners are drawn at the corners of the box, the edges are
/// stretched or tiled between them, and the middle fills the inside only when
/// the slice says `fill`. Each piece is as wide as [#widths] says, which is
/// the border's own width unless it says otherwise. When a picture is drawn it
/// takes the place of the border's colour; while it is loading, or when it
/// cannot be, the border is drawn as its colours say.
///
/// It is part of a box's decoration, so a state's rule picks the sprite through
/// the cascade like any other property.
///
/// The [#outsets] push the picture out past the box, and nothing about it
/// moves the layout: the border's widths do that, as they always did. A
/// border image is not clipped by `border-radius`, which is CSS's rule.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
///
/// @param source  the picture, or null for `none`, which draws nothing
/// @param slice   where the picture is cut, and whether its middle is drawn
/// @param widths  how wide each side's pieces are drawn: top, right, bottom,
///                left
/// @param outsets how far the picture reaches past the box on each side: top,
///                right, bottom, left
/// @param across  how the top and bottom edges and the middle fill their length
/// @param down    how the left and right edges and the middle fill theirs
public record BorderImage(
        CssImage.@Nullable Url source,
        Slice slice,
        List<Extent> widths,
        List<Extent> outsets,
        Repeat across,
        Repeat down) {

    /// No border image: CSS's initial values, with no source.
    public static final BorderImage NONE = new BorderImage(
            null,
            Slice.INITIAL,
            List.of(Extent.ONE, Extent.ONE, Extent.ONE, Extent.ONE),
            List.of(Extent.ZERO, Extent.ZERO, Extent.ZERO, Extent.ZERO),
            Repeat.STRETCH,
            Repeat.STRETCH);

    public BorderImage {
        Objects.requireNonNull(slice, "slice");
        widths = List.copyOf(Objects.requireNonNull(widths, "widths"));
        outsets = List.copyOf(Objects.requireNonNull(outsets, "outsets"));
        Objects.requireNonNull(across, "across");
        Objects.requireNonNull(down, "down");
        if (widths.size() != 4 || outsets.size() != 4) {
            throw new IllegalArgumentException("a border image has four widths and four outsets, top first");
        }
        for (var outset : outsets) {
            if (outset.kind() == Extent.Kind.PERCENT || outset.kind() == Extent.Kind.AUTO) {
                throw new IllegalArgumentException("an outset is a length or a number, not " + outset);
            }
        }
    }

    /// Whether there is a picture to draw.
    public boolean isDrawn() {
        return source != null;
    }

    /// This border image drawn from `value` — `border-image-source`.
    public BorderImage source(CssImage.@Nullable Url value) {
        return new BorderImage(value, slice, widths, outsets, across, down);
    }

    /// `border-image-slice`.
    public BorderImage slice(Slice value) {
        return new BorderImage(source, value, widths, outsets, across, down);
    }

    /// `border-image-width`, top, right, bottom and left.
    public BorderImage widths(List<Extent> value) {
        return new BorderImage(source, slice, value, outsets, across, down);
    }

    /// `border-image-outset`, top, right, bottom and left.
    public BorderImage outsets(List<Extent> value) {
        return new BorderImage(source, slice, widths, value, across, down);
    }

    /// `border-image-repeat`.
    public BorderImage repeat(Repeat across, Repeat down) {
        return new BorderImage(source, slice, widths, outsets, across, down);
    }

    /// How far the picture reaches past the box, in logical pixels: top, right,
    /// bottom and left, a number being that many of the side's border width.
    /// Nothing when there is no picture.
    public double[] outsetsFor(Border border) {
        if (!isDrawn()) {
            return new double[4];
        }
        var sides = new double[] {
            border.top().width(),
            border.right().width(),
            border.bottom().width(),
            border.left().width()
        };
        var result = new double[4];
        for (var i = 0; i < 4; i++) {
            var outset = outsets.get(i);
            result[i] = Math.max(
                    0,
                    switch (outset.kind()) {
                        case NUMBER -> outset.value() * sides[i];
                        case LENGTH -> outset.value();
                        case PERCENT, AUTO -> 0;
                    });
        }
        return result;
    }

    /// This border image with its picture's alpha scaled by `alpha`, which is
    /// how `opacity` reaches it.
    public BorderImage fade(double alpha) {
        return alpha >= 1 || source == null ? this : source(source.fade(alpha));
    }

    /// `border-image-slice`: four lines into the picture, in from its top,
    /// right, bottom and left edges.
    ///
    /// A number is in the picture's own pixels — the 1x picture's, so a `@2x`
    /// variant is cut at twice it — and a percentage is of the picture's width
    /// or height.
    ///
    /// @param top    the top line, a [Length.Points] or a [Length.Percent]
    /// @param right  the right line
    /// @param bottom the bottom line
    /// @param left   the left line
    /// @param fill   whether the middle is drawn
    public record Slice(Length top, Length right, Length bottom, Length left, boolean fill) {

        /// `100%` on every side, and no middle: CSS's initial value, which makes
        /// the whole picture four corners.
        public static final Slice INITIAL =
                new Slice(Length.percent(100), Length.percent(100), Length.percent(100), Length.percent(100), false);

        public Slice {
            for (var line : new Length[] {top, right, bottom, left}) {
                Objects.requireNonNull(line, "slice");
                if (!(line instanceof Length.Points points && points.value() >= 0)
                        && !(line instanceof Length.Percent percent && percent.value() >= 0)) {
                    throw new IllegalArgumentException("a slice is a number or a percentage, not " + line);
                }
            }
        }
    }

    /// One side's `border-image-width` or `border-image-outset`.
    ///
    /// @param kind  how `value` is read
    /// @param value the number, the length in logical pixels, or the percentage;
    ///              zero for `auto`
    public record Extent(Kind kind, double value) {

        /// The border's width: the initial `border-image-width`.
        public static final Extent ONE = new Extent(Kind.NUMBER, 1);

        /// Nothing: the initial `border-image-outset`.
        public static final Extent ZERO = new Extent(Kind.NUMBER, 0);

        /// The slice's own size: `auto`.
        public static final Extent AUTO = new Extent(Kind.AUTO, 0);

        /// How an extent is written.
        public enum Kind {
            /// A multiple of the side's border width.
            NUMBER,
            /// A length, in logical pixels.
            LENGTH,
            /// A percentage of the border image area's width or height.
            PERCENT,
            /// The slice's size at the picture's density.
            AUTO
        }

        public Extent {
            Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("a border image extent is not negative, and " + value + " is");
            }
        }

        @Override
        public String toString() {
            return switch (kind) {
                case NUMBER -> String.valueOf(value);
                case LENGTH -> value + "px";
                case PERCENT -> value + "%";
                case AUTO -> "auto";
            };
        }
    }

    /// How an edge's piece fills its length — `border-image-repeat`.
    public enum Repeat {

        /// Stretched to the length: CSS's initial value.
        STRETCH,

        /// Tiled at its own scale, centred, the tiles at the ends cut.
        REPEAT,

        /// Tiled a whole number of times, each tile stretched a little to fit.
        ROUND
    }
}
