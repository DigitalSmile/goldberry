package dev.goldberry.gpu.upscale;

import java.util.Objects;

import dev.goldberry.render.model.PhysicalSize;

/// The size a 3D view is drawn at, against the size it is shown at, and how it
/// is brought from one to the other.
///
/// ```java
/// new Canvas3d(renderer).renderScale(RenderScale.of(0.67).withFilter(UpscaleFilter.FSR1))
/// ```
///
/// ```kdl
/// canvas3d renderer="scene" render-scale=0.67 upscale="fsr1" sharpness=0.8
/// ```
///
/// A factor below 1 draws fewer pixels and upscales them: the frame rate a
/// game's "render scale" slider buys. Above 1 it draws more and brings them
/// down linearly, which is supersampling. At 1 the renderer draws straight into
/// the box, and nothing else runs.
///
/// FSR 1's quality modes are factors of this: ultra quality 0.77, quality
/// 0.67, balanced 0.59, performance 0.5.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
///
/// @param factor    the drawn size over the shown size, per axis:
///                  [#MIN_FACTOR] to [#MAX_FACTOR]
/// @param filter    how the drawn picture is brought to the shown size
/// @param sharpness how much [UpscaleFilter#FSR1] sharpens, 0 (not at all)
///                  to 1 (FSR's most); ignored by [UpscaleFilter#LINEAR]
public record RenderScale(double factor, UpscaleFilter filter, double sharpness) {

    /// The smallest factor: half the pixels each way, FSR 1's performance
    /// mode. Below it EASU has too little to find edges in.
    public static final double MIN_FACTOR = 0.5;

    /// The largest factor: twice the pixels each way.
    public static final double MAX_FACTOR = 2;

    /// The sharpness AMD recommends: FSR's 0.2 stops, `2^-0.2`.
    public static final double DEFAULT_SHARPNESS = 0.87;

    /// Drawn at the size shown.
    public static final RenderScale NATIVE = new RenderScale(1, UpscaleFilter.LINEAR, DEFAULT_SHARPNESS);

    /// Checks the numbers are in range.
    ///
    /// @throws IllegalArgumentException when the factor is outside
    ///                                  [#MIN_FACTOR] to [#MAX_FACTOR], or the
    ///                                  sharpness outside 0 to 1
    public RenderScale {
        Objects.requireNonNull(filter, "filter");
        if (!(factor >= MIN_FACTOR && factor <= MAX_FACTOR)) {
            throw new IllegalArgumentException(
                    "render-scale=" + factor + ": it is " + MIN_FACTOR + " to " + MAX_FACTOR);
        }
        if (!(sharpness >= 0 && sharpness <= 1)) {
            throw new IllegalArgumentException("sharpness=" + sharpness + ": it is 0 to 1");
        }
    }

    /// Drawn at `factor` of the size shown, brought there linearly.
    ///
    /// @throws IllegalArgumentException as the constructor
    public static RenderScale of(double factor) {
        return new RenderScale(factor, UpscaleFilter.LINEAR, DEFAULT_SHARPNESS);
    }

    /// The same scale at `value`.
    public RenderScale withFactor(double value) {
        return new RenderScale(value, filter, sharpness);
    }

    /// The same scale, brought to size by `value`.
    public RenderScale withFilter(UpscaleFilter value) {
        return new RenderScale(factor, value, sharpness);
    }

    /// The same scale, sharpened by `value`.
    public RenderScale withSharpness(double value) {
        return new RenderScale(factor, filter, value);
    }

    /// Whether the picture is drawn at the size shown.
    public boolean isNative() {
        return factor == 1;
    }

    /// The size a box of `shown` physical pixels is drawn at: each axis times
    /// the factor, rounded, and never below one pixel.
    public PhysicalSize drawSize(PhysicalSize shown) {
        Objects.requireNonNull(shown, "shown");
        if (isNative()) {
            return shown;
        }
        return new PhysicalSize(scaled(shown.width()), scaled(shown.height()));
    }

    private int scaled(int pixels) {
        return (int) Math.max(1, Math.round(pixels * factor));
    }
}
