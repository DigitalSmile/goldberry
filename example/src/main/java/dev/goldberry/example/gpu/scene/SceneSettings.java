package dev.goldberry.example.gpu.scene;

import java.util.List;

/// What the room's options panel sets, read by the renderer on every frame: a
/// game's graphics settings, less the render scale and the present mode, which
/// are the canvas's and the window's.
///
/// @param rayTracing  whether the room is lit by the hardware ray tracer; off,
///                    it is lit by the light's angle alone
/// @param shadows     whether the traced shadows darken what the sun cannot
///                    reach
/// @param occlusion   whether the traced occlusion darkens corners and creases
/// @param reflections whether the shiny surfaces show what their reflection
///                    rays see
/// @param samples     the anti-aliasing samples a pixel asked for: one of
///                    [#SAMPLE_COUNTS]; the renderer takes the most the device
///                    has up to it
/// @param anisotropy  how many samples the floor's texture takes along a
///                    slanted footprint: one of [#ANISOTROPY]
public record SceneSettings(
        boolean rayTracing, boolean shadows, boolean occlusion, boolean reflections, int samples, int anisotropy) {

    /// The anti-aliasing a panel offers.
    public static final List<Integer> SAMPLE_COUNTS = List.of(1, 2, 4, 8);

    /// The anisotropy a panel offers.
    public static final List<Integer> ANISOTROPY = List.of(1, 4, 16);

    /// Lit by the light's angle, every traced effect on for when tracing is
    /// turned on, one sample a pixel, and the floor's texture read
    /// trilinearly: the same picture on every machine.
    public static final SceneSettings DEFAULT = new SceneSettings(false, true, true, true, 1, 1);

    /// @throws IllegalArgumentException for samples or anisotropy not offered
    public SceneSettings {
        if (!SAMPLE_COUNTS.contains(samples)) {
            throw new IllegalArgumentException(samples + " samples; it is one of " + SAMPLE_COUNTS);
        }
        if (!ANISOTROPY.contains(anisotropy)) {
            throw new IllegalArgumentException(anisotropy + "x anisotropy; it is one of " + ANISOTROPY);
        }
    }

    /// The same, traced or not.
    public SceneSettings withRayTracing(boolean value) {
        return new SceneSettings(value, shadows, occlusion, reflections, samples, anisotropy);
    }

    /// The same, with traced shadows or without.
    public SceneSettings withShadows(boolean value) {
        return new SceneSettings(rayTracing, value, occlusion, reflections, samples, anisotropy);
    }

    /// The same, with traced occlusion or without.
    public SceneSettings withOcclusion(boolean value) {
        return new SceneSettings(rayTracing, shadows, value, reflections, samples, anisotropy);
    }

    /// The same, with traced reflections or without.
    public SceneSettings withReflections(boolean value) {
        return new SceneSettings(rayTracing, shadows, occlusion, value, samples, anisotropy);
    }

    /// The same, at `value` samples a pixel.
    public SceneSettings withSamples(int value) {
        return new SceneSettings(rayTracing, shadows, occlusion, reflections, value, anisotropy);
    }

    /// The same, at `value` anisotropy.
    public SceneSettings withAnisotropy(int value) {
        return new SceneSettings(rayTracing, shadows, occlusion, reflections, samples, value);
    }
}
