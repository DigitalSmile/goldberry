package dev.goldberry.css.background;

import java.util.Objects;

import dev.goldberry.layout.Length;

/// How large a picture in a box's background is drawn — CSS's
/// `background-size`.
///
/// ```css
/// .hero { background-size: cover }
/// .crest { background-size: 64px }
/// .weave { background-size: 50% auto }
/// ```
///
/// `cover` scales the picture until it fills the box, cropping what is left
/// over; `contain` until it fits inside it. Lengths size it directly, a
/// percentage being of the box, and an `auto` axis follows the picture's own
/// shape. `auto` on both, the initial value, is the picture's natural size. A
/// gradient is the size of its box whatever this says.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
///
/// @param kind   `cover`, `contain`, or sized by the two lengths
/// @param width  the width, a length, a percentage of the box's, or
///               [Length#AUTO]; [Length#AUTO] for `cover` and `contain`
/// @param height the height, the same way
public record BackgroundSize(Kind kind, Length width, Length height) {

    /// The picture's natural size: CSS's initial value.
    public static final BackgroundSize AUTO = new BackgroundSize(Kind.LENGTHS, Length.AUTO, Length.AUTO);

    /// Large enough to cover the box.
    public static final BackgroundSize COVER = new BackgroundSize(Kind.COVER, Length.AUTO, Length.AUTO);

    /// Large enough to fit inside the box.
    public static final BackgroundSize CONTAIN = new BackgroundSize(Kind.CONTAIN, Length.AUTO, Length.AUTO);

    /// The three ways a size is written.
    public enum Kind {
        /// `cover`.
        COVER,
        /// `contain`.
        CONTAIN,
        /// One or two lengths, percentages or `auto`.
        LENGTHS
    }

    public BackgroundSize {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        for (var length : new Length[] {width, height}) {
            if (!(length instanceof Length.Points) && !(length instanceof Length.Percent) && length != Length.AUTO) {
                throw new IllegalArgumentException(
                        "a background size is a length, a percentage or auto, not " + length);
            }
        }
    }

    /// The size, in logical pixels, of a picture `naturalWidth` × `naturalHeight`
    /// in a box `boxWidth` × `boxHeight`.
    ///
    /// @return the width and the height, each at least zero
    public double[] resolve(double boxWidth, double boxHeight, double naturalWidth, double naturalHeight) {
        if (!(naturalWidth > 0) || !(naturalHeight > 0)) {
            return new double[] {0, 0};
        }
        var ratio = naturalWidth / naturalHeight;
        return switch (kind) {
            case COVER, CONTAIN -> {
                var across = boxWidth / naturalWidth;
                var down = boxHeight / naturalHeight;
                var factor = kind == Kind.COVER ? Math.max(across, down) : Math.min(across, down);
                yield new double[] {naturalWidth * factor, naturalHeight * factor};
            }
            case LENGTHS -> {
                var autoWidth = width == Length.AUTO;
                var autoHeight = height == Length.AUTO;
                var w = Length.resolve(width, (float) boxWidth);
                var h = Length.resolve(height, (float) boxHeight);
                if (autoWidth && autoHeight) {
                    yield new double[] {naturalWidth, naturalHeight};
                }
                if (autoWidth) {
                    yield new double[] {Math.max(0, h) * ratio, Math.max(0, h)};
                }
                if (autoHeight) {
                    yield new double[] {Math.max(0, w), Math.max(0, w) / ratio};
                }
                yield new double[] {Math.max(0, w), Math.max(0, h)};
            }
        };
    }

    /// The size as CSS writes it.
    @Override
    public String toString() {
        return switch (kind) {
            case COVER -> "cover";
            case CONTAIN -> "contain";
            case LENGTHS -> width + " " + height;
        };
    }
}
