package dev.goldberry.gpu;

/// What a render pass does with its colour target before it draws.
public sealed interface Load {

    /// Keeps what the target holds, and draws over it.
    record Keep() implements Load {}

    /// Clears the target to a colour, in the format's components: for a
    /// `UNORM` format, 0 to 1. Premultiplied, as everything the toolkit
    /// composites is.
    ///
    /// @param red   red
    /// @param green green
    /// @param blue  blue
    /// @param alpha alpha
    record Clear(float red, float green, float blue, float alpha) implements Load {}

    /// Leaves the target's contents undefined, because every pixel will be
    /// drawn: cheaper than a clear on tiled GPUs.
    record DontCare() implements Load {}

    /// Keeps what the target holds.
    static Load keep() {
        return new Keep();
    }

    /// Clears to a colour.
    static Load clear(float red, float green, float blue, float alpha) {
        return new Clear(red, green, blue, alpha);
    }

    /// Clears to transparent black.
    static Load clearTransparent() {
        return new Clear(0, 0, 0, 0);
    }

    /// Leaves the contents undefined.
    static Load dontCare() {
        return new DontCare();
    }
}
