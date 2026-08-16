package io.github.digitalsmile.goldberry.backend;

/// A rectangle of a frame that changed, in **physical** pixels relative to the
/// window's top-left.
///
/// Damage is what makes a repaint cheap: the frame loop re-rasterizes dirty
/// layers and blits the rest, and the backend uploads only these rectangles
/// (`docs/ARCHITECTURE.md` §5).
///
/// Physical rather than logical, because this describes a region of a pixel
/// buffer. Converting at the boundary is [DisplayScale]'s job, and doing it
/// anywhere else is how a 150% display ends up with a one-pixel undrawn seam
/// along every damage edge.
public record DamageRect(int x, int y, int width, int height) {

    public DamageRect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "damage size must not be negative: " + width + "x" + height);
        }
    }

    /// The whole of a frame.
    public static DamageRect all(PhysicalSize size) {
        return new DamageRect(0, 0, size.width(), size.height());
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /// Whether this rectangle lies entirely inside a frame of the given size.
    ///
    /// A backend uploading a region outside its buffer is a crash on some
    /// platforms and silent corruption on others, so this is checked rather than
    /// assumed.
    public boolean fitsWithin(PhysicalSize size) {
        return x >= 0
                && y >= 0
                && (long) x + width <= size.width()
                && (long) y + height <= size.height();
    }

    @Override
    public String toString() {
        return width + "x" + height + "+" + x + "+" + y;
    }
}
