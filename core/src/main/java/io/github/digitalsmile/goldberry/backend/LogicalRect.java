package io.github.digitalsmile.goldberry.backend;

import java.util.Objects;

/// A rectangle in logical pixels.
///
/// [LogicalPoint] and [LogicalSize] together, because the two are only ever
/// meaningful together where this is used: **where a popup is allowed to be.**
/// A work area is an origin *and* an extent — a taskbar at the top of the screen
/// moves the origin and shrinks the extent, and a policy given only the size
/// would place a menu underneath it.
///
/// Whose coordinates is the caller's to know, exactly as for [LogicalPoint].
///
/// @param origin the top-left corner
/// @param size   the extent from there
public record LogicalRect(LogicalPoint origin, LogicalSize size) {

    public LogicalRect {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(size, "size");
    }

    public static LogicalRect of(float x, float y, float width, float height) {
        return new LogicalRect(new LogicalPoint(x, y), new LogicalSize(width, height));
    }

    public float left() {
        return origin.x();
    }

    public float top() {
        return origin.y();
    }

    /// The first x **outside** the rectangle, as a CSS or Yoga edge would mean it.
    public float right() {
        return origin.x() + size.width();
    }

    /// The first y outside the rectangle.
    public float bottom() {
        return origin.y() + size.height();
    }

    public float width() {
        return size.width();
    }

    public float height() {
        return size.height();
    }

    /// Whether `point` is inside, edges included on the top-left and excluded on
    /// the bottom-right — the half-open convention every pixel rectangle in the
    /// toolkit uses, so two rectangles that touch do not both claim the seam.
    public boolean contains(LogicalPoint point) {
        return point.x() >= left() && point.x() < right()
                && point.y() >= top() && point.y() < bottom();
    }

    /// Whether a rectangle of `size` placed at `at` fits entirely inside.
    public boolean encloses(LogicalPoint at, LogicalSize extent) {
        return at.x() >= left() && at.y() >= top()
                && at.x() + extent.width() <= right()
                && at.y() + extent.height() <= bottom();
    }

    /// This rectangle moved by `dx`, `dy` — for translating between one window's
    /// coordinates and the desktop's.
    public LogicalRect offsetBy(float dx, float dy) {
        return new LogicalRect(origin.offsetBy(dx, dy), size);
    }

    @Override
    public String toString() {
        return size + " at " + origin;
    }
}
