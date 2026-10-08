package dev.goldberry.css.image;

import java.util.Objects;

import dev.goldberry.image.Image;

/// A decoded picture a stylesheet named, and the density of the variant it is.
///
/// A `@2x` variant has twice the pixels of the 1x picture and the same natural
/// size, so its natural size is its pixels over its density — the rule an
/// `image`'s variants follow.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
///
/// @param image   the pixels
/// @param density how many of its pixels make one logical pixel: 1 or 2
public record StyleImage(Image image, int density) {

    public StyleImage {
        Objects.requireNonNull(image, "image");
        if (density < 1) {
            throw new IllegalArgumentException("a density is 1 or more, not " + density);
        }
    }

    /// The width it is drawn at when nothing sizes it, in logical pixels.
    public double naturalWidth() {
        return (double) image.width() / density;
    }

    /// The height it is drawn at when nothing sizes it, in logical pixels.
    public double naturalHeight() {
        return (double) image.height() / density;
    }
}
