package io.github.digitalsmile.goldberry.backend;

/// A size in **logical** pixels — the space layout, styling and application code
/// work in.
///
/// Distinct from [PhysicalSize] on purpose. The two are the same number at 100%
/// scale and different everywhere else, and mixing them up is the classic HiDPI
/// bug: it looks correct on the developer's machine and is wrong by 50% on a
/// user's. Making them different types means the compiler catches it, and
/// [DisplayScale] is the only bridge.
public record LogicalSize(float width, float height) {

    public LogicalSize {
        requireUsable(width, "width");
        requireUsable(height, "height");
    }

    public static LogicalSize of(float width, float height) {
        return new LogicalSize(width, height);
    }

    public boolean isEmpty() {
        return width == 0f || height == 0f;
    }

    private static void requireUsable(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, not " + value);
        }
        if (value < 0f) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }

    @Override
    public String toString() {
        return width + "x" + height + " logical";
    }
}
