package dev.goldberry.example.book.pictures;

import java.util.Optional;

import dev.goldberry.image.Image;

/// The part of a render that holds the widget: the smallest box around every
/// pixel that is not the page colour, grown by a margin.
///
/// A widget is drawn into a viewport larger than itself so that it lays out as
/// it would in a window, and the picture the guide shows is the widget rather
/// than the viewport. A widget that fills the viewport — a `scroll`, a
/// `split-pane` — crops to the whole of it, which is the honest picture of a
/// widget that grows.
///
/// @param x      the left edge, in pixels of the render
/// @param y      the top edge
/// @param width  how wide, always positive
/// @param height how tall, always positive
public record Crop(int x, int y, int width, int height) {

    public Crop {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a crop needs a positive size, and " + width + "x" + height + " is not");
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("a crop starts inside the image, not at (" + x + ", " + y + ")");
        }
    }

    /// The box around every pixel of `image` that is not `page`, grown by
    /// `margin` on each side and clamped to the image. Empty when every pixel is
    /// the page colour, which is a widget that drew nothing.
    public static Optional<Crop> around(Image image, int page, int margin) {
        var width = image.width();
        var height = image.height();
        var left = width;
        var top = height;
        var right = -1;
        var bottom = -1;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (image.argb(x, y) != page) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) {
            return Optional.empty();
        }
        var x0 = Math.max(0, left - margin);
        var y0 = Math.max(0, top - margin);
        var x1 = Math.min(width - 1, right + margin);
        var y1 = Math.min(height - 1, bottom + margin);
        return Optional.of(new Crop(x0, y0, x1 - x0 + 1, y1 - y0 + 1));
    }

    /// `image` cut down to this box.
    public Image apply(Image image) {
        var argb = new int[width * height];
        for (var row = 0; row < height; row++) {
            for (var column = 0; column < width; column++) {
                argb[row * width + column] = image.argb(x + column, y + row);
            }
        }
        return Image.ofArgb(width, height, argb);
    }
}
