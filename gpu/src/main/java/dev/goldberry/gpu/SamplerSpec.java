package dev.goldberry.gpu;

import java.util.Objects;

/// What a [GpuSampler] is made as: one filter for minifying and magnifying, and
/// one address mode for both axes. The toolkit's textures have one mip level.
///
/// @param filter      how it reads between texels
/// @param addressMode what it reads outside 0 to 1
public record SamplerSpec(Filter filter, AddressMode addressMode) {

    /// Checks nothing is missing.
    public SamplerSpec {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(addressMode, "addressMode");
    }

    /// Nearest texel, clamped to the edge: a texture drawn 1:1.
    public static SamplerSpec nearest() {
        return new SamplerSpec(Filter.NEAREST, AddressMode.CLAMP_TO_EDGE);
    }

    /// Interpolated, clamped to the edge: a texture drawn scaled.
    public static SamplerSpec linear() {
        return new SamplerSpec(Filter.LINEAR, AddressMode.CLAMP_TO_EDGE);
    }

    /// This spec with `mode` outside 0 to 1.
    public SamplerSpec withAddressMode(AddressMode mode) {
        return new SamplerSpec(filter, mode);
    }
}
