package io.github.digitalsmile.goldberry.backend;

/// A size in **physical** pixels — what is actually rasterized and presented.
///
/// Integers, because there is no such thing as two thirds of a pixel in a frame
/// buffer. See [LogicalSize] for why the two are separate types, and
/// [DisplayScale] for the only conversion between them.
public record PhysicalSize(int width, int height) {

    public PhysicalSize {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "physical size must not be negative: " + width + "x" + height);
        }
    }

    public static PhysicalSize of(int width, int height) {
        return new PhysicalSize(width, height);
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /// The number of pixels, as a `long` because 16384×16384 overflows an `int`
    /// once multiplied by four bytes.
    public long pixelCount() {
        return (long) width * height;
    }

    @Override
    public String toString() {
        return width + "x" + height + " px";
    }
}
