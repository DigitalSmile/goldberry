package dev.goldberry.gpu;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/// What a [GpuDevice] can do, asked of it once: the answers a game's options
/// screen offers its choices from, so that a setting the device lacks is never
/// offered rather than refused when it is applied.
///
/// From [GpuDevice#features]. Ray tracing is not here: it runs on a device of
/// its own, which `RayTracer.availability()` looks for, once a process, and
/// only when asked.
///
/// ```java
/// var features = device.features();
/// var msaa = features.colorSampleCounts();          // [1, 2, 4, 8]: the anti-aliasing choices
/// var portals = features.stencilFormat().isPresent();
/// ```
///
/// @param driver            the driver, as [GpuDevice#driver] names it
/// @param adapter           what the system says the device is
/// @param shaderFormats     the shader formats it takes
/// @param colorSampleCounts the sample counts a [TextureFormat#B8G8R8A8_UNORM]
///                          target can have, ascending, from 1. Not always
///                          every one between: lavapipe has 4 and 8 and no 2
/// @param depthSampleCounts the sample counts a [TextureFormat#D32_FLOAT] depth
///                          target can have, ascending, from 1
/// @param stencilFormats    the depth formats with a stencil it can make a
///                          target of, which a [StencilTest] needs
/// @param compressedFormats the block-compressed formats it samples: BC on
///                          desktop GPUs, ASTC on Apple's and mobile ones
/// @param floatTargets      whether a [TextureFormat#R16G16B16A16_FLOAT] target
///                          can be rendered into and sampled: a scene kept in
///                          linear light above 1, for bloom or HDR output
public record GpuFeatures(
        String driver,
        GpuAdapter adapter,
        Set<ShaderFormat> shaderFormats,
        List<Integer> colorSampleCounts,
        List<Integer> depthSampleCounts,
        Set<TextureFormat> stencilFormats,
        Set<TextureFormat> compressedFormats,
        boolean floatTargets) {

    /// The sample counts there are: 1, 2, 4 and 8.
    static final List<Integer> SAMPLE_COUNTS = List.of(1, 2, 4, 8);

    /// Checks the counts and formats, and copies the collections.
    ///
    /// @throws IllegalArgumentException when a list of sample counts does not
    ///                                  start at 1 and rise through some of 2,
    ///                                  4 and 8, or a format is in the wrong set
    public GpuFeatures {
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(adapter, "adapter");
        shaderFormats = Collections.unmodifiableSet(
                shaderFormats.isEmpty() ? EnumSet.noneOf(ShaderFormat.class) : EnumSet.copyOf(shaderFormats));
        colorSampleCounts = requireCounts(colorSampleCounts, "colour");
        depthSampleCounts = requireCounts(depthSampleCounts, "depth");
        stencilFormats = formats(stencilFormats, TextureFormat::hasStencil, "a depth format with a stencil");
        compressedFormats = formats(compressedFormats, TextureFormat::isCompressed, "block-compressed");
    }

    /// The most samples a colour target can have: the highest anti-aliasing
    /// setting to offer.
    public int maxColorSamples() {
        return colorSampleCounts.getLast();
    }

    /// The depth format with a stencil to make a stencilled pass's target of:
    /// [TextureFormat#D24_UNORM_S8_UINT] where the device has it, being the
    /// smaller, and [TextureFormat#D32_FLOAT_S8_UINT] otherwise. Empty on a
    /// device with neither.
    public Optional<TextureFormat> stencilFormat() {
        return List.of(TextureFormat.D24_UNORM_S8_UINT, TextureFormat.D32_FLOAT_S8_UINT).stream()
                .filter(stencilFormats::contains)
                .findFirst();
    }

    /// What `device` can do, asked of it now.
    static GpuFeatures of(GpuDevice device) {
        var stencil = EnumSet.noneOf(TextureFormat.class);
        var compressed = EnumSet.noneOf(TextureFormat.class);
        for (var format : TextureFormat.values()) {
            if (format.hasStencil() && device.supports(format, EnumSet.of(TextureUsage.DEPTH_TARGET))) {
                stencil.add(format);
            }
            if (format.isCompressed() && device.supports(format, EnumSet.of(TextureUsage.SAMPLER))) {
                compressed.add(format);
            }
        }
        return new GpuFeatures(
                device.driver(),
                device.adapter(),
                device.shaderFormats(),
                sampleCounts(device, TextureFormat.B8G8R8A8_UNORM),
                sampleCounts(device, TextureFormat.D32_FLOAT),
                stencil,
                compressed,
                device.supports(
                        TextureFormat.R16G16B16A16_FLOAT, EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER)));
    }

    private static List<Integer> sampleCounts(GpuDevice device, TextureFormat format) {
        return SAMPLE_COUNTS.stream()
                .filter(count -> device.supportsSamples(format, count))
                .toList();
    }

    private static List<Integer> requireCounts(List<Integer> counts, String what) {
        var copy = List.copyOf(counts);
        var ascending = SAMPLE_COUNTS.stream().filter(copy::contains).toList();
        if (copy.isEmpty() || copy.getFirst() != 1 || !ascending.equals(copy)) {
            throw new IllegalArgumentException(
                    what + " sample counts " + copy + " do not start at 1 and rise through some of 2, 4 and 8");
        }
        return copy;
    }

    private static Set<TextureFormat> formats(Set<TextureFormat> formats, Predicate<TextureFormat> kind, String what) {
        for (var format : formats) {
            if (!kind.test(format)) {
                throw new IllegalArgumentException(format + " is not " + what);
            }
        }
        return Collections.unmodifiableSet(
                formats.isEmpty() ? EnumSet.noneOf(TextureFormat.class) : EnumSet.copyOf(formats));
    }
}
