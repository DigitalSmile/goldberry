package io.github.digitalsmile.goldberry.render.model;

/// A rectangle in **physical** pixels — a region of a raster rather than a
/// position in the coordinate space.
///
/// Integers, for [PhysicalSize]'s reason: there is no such thing as two thirds of
/// a pixel in a buffer. [LogicalRect] is the other one and is not
/// interchangeable — a logical rectangle is where something goes, and this is
/// which pixels it is made of.
///
/// What asks for one today is the crop of a drawn image (ADR-0283): the part of
/// an image to draw, addressed in the image's own pixels, which is a different
/// space from the logical rectangle it is drawn into.
///
/// Half-open, like every pixel rectangle here: [#right()] and [#bottom()] are the
/// first column and row *outside*, so two rectangles that touch do not both claim
/// the seam.
///
/// @param x left edge
/// @param y top edge
/// @param width extent to the right, which may be zero
/// @param height extent downwards, which may be zero
public record PhysicalRect(int x, int y, int width, int height) {

    public PhysicalRect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "a physical rectangle must not have a negative extent: " + width + "x" + height);
        }
    }

    public static PhysicalRect of(int x, int y, int width, int height) {
        return new PhysicalRect(x, y, width, height);
    }

    /// The whole of a raster of this size, at the origin.
    public static PhysicalRect of(PhysicalSize size) {
        return new PhysicalRect(0, 0, size.width(), size.height());
    }

    /// The first column outside the rectangle.
    public int right() {
        return x + width;
    }

    /// The first row outside the rectangle.
    public int bottom() {
        return y + height;
    }

    public PhysicalSize size() {
        return new PhysicalSize(width, height);
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /// Whether this rectangle lies entirely within a raster of `size`.
    ///
    /// The question a crop is checked against: a source rectangle that runs off
    /// the edge of its image is a caller error worth reporting, rather than a blit
    /// that quietly draws less than it was asked for.
    public boolean fitsWithin(PhysicalSize size) {
        return x >= 0 && y >= 0 && right() <= size.width() && bottom() <= size.height();
    }

    @Override
    public String toString() {
        return width + "x" + height + " px at (" + x + ", " + y + ")";
    }
}
