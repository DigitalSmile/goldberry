package io.github.digitalsmile.goldberry.layout;

/// What a leaf reports it needs, in logical pixels.
///
/// @param width  how far across
/// @param height how far down
public record MeasuredSize(float width, float height) {

    public MeasuredSize {
        if (!Float.isFinite(width) || !Float.isFinite(height)) {
            // A NaN here reaches the layout engine as "undefined" and the node
            // silently collapses, which reads as text that failed to appear
            // rather than as arithmetic that went wrong.
            throw new IllegalArgumentException(
                    "a measured size must be finite, and " + width + "x" + height + " is not");
        }
    }
}
