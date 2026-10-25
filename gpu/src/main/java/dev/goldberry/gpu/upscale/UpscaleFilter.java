package dev.goldberry.gpu.upscale;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How a picture drawn at one size is brought to another: what `canvas3d
/// upscale=` names.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
public enum UpscaleFilter {

    /// Bilinear: each pixel interpolated from the four texels nearest it. One
    /// pass, soft edges. What a picture drawn larger than its box is brought
    /// down with, whatever the filter asked for.
    LINEAR,

    /// AMD FidelityFX Super Resolution 1: EASU, which upscales along the edges
    /// it finds and keeps them sharp, then RCAS, which sharpens by
    /// [RenderScale#sharpness]. Two passes where a picture is drawn smaller
    /// than its box; RCAS alone where it is drawn at its size.
    FSR1;

    /// The markup's `upscale=` names: `linear` or `fsr1`; absent is
    /// [#LINEAR].
    ///
    /// @throws IllegalArgumentException for any other name
    public static UpscaleFilter named(@Nullable String name) {
        if (name == null) {
            return LINEAR;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "linear" -> LINEAR;
            case "fsr1" -> FSR1;
            default -> throw new IllegalArgumentException("upscale=\"" + name + "\": it is linear or fsr1");
        };
    }

    /// The name the markup gives it.
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
