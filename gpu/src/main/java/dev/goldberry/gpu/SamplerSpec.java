package dev.goldberry.gpu;

import java.util.Objects;
import java.util.Optional;

/// What a [GpuSampler] is made as: one filter for minifying and magnifying,
/// one address mode for every axis, how it reads between mip levels, and
/// whether a lookup compares against a reference depth.
///
/// Without a mip filter a sampler reads level 0 alone, whatever the texture
/// has: what the toolkit's own textures, with one level, want. With one it
/// reads the level the footprint calls for, and [Filter#LINEAR] blends the two
/// nearest. A comparison sampler is what a shadow map is read with: HLSL's
/// `SampleCmp` gives 0 or 1 per texel, filtered.
///
/// @param filter      how it reads between texels
/// @param addressMode what it reads outside 0 to 1
/// @param mipFilter   how it reads between mip levels, or empty for level 0
///                    alone
/// @param compare     the comparison a lookup makes, or empty for a plain
///                    lookup
public record SamplerSpec(
        Filter filter, AddressMode addressMode, Optional<Filter> mipFilter, Optional<CompareOp> compare) {

    /// Checks nothing is missing.
    public SamplerSpec {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(addressMode, "addressMode");
        Objects.requireNonNull(mipFilter, "mipFilter");
        Objects.requireNonNull(compare, "compare");
    }

    /// `filter` and `addressMode`, reading level 0 alone, with no comparison.
    public SamplerSpec(Filter filter, AddressMode addressMode) {
        this(filter, addressMode, Optional.empty(), Optional.empty());
    }

    /// Nearest texel, clamped to the edge: a texture drawn 1:1.
    public static SamplerSpec nearest() {
        return new SamplerSpec(Filter.NEAREST, AddressMode.CLAMP_TO_EDGE);
    }

    /// Interpolated, clamped to the edge: a texture drawn scaled.
    public static SamplerSpec linear() {
        return new SamplerSpec(Filter.LINEAR, AddressMode.CLAMP_TO_EDGE);
    }

    /// Interpolated between texels and between mip levels, clamped to the
    /// edge: a mipmapped texture seen at any distance.
    public static SamplerSpec trilinear() {
        return linear().withMipFilter(Filter.LINEAR);
    }

    /// This spec with `mode` outside 0 to 1.
    public SamplerSpec withAddressMode(AddressMode mode) {
        return new SamplerSpec(filter, mode, mipFilter, compare);
    }

    /// This spec reading between mip levels with `filter`.
    public SamplerSpec withMipFilter(Filter filter) {
        return new SamplerSpec(this.filter, addressMode, Optional.of(filter), compare);
    }

    /// This spec comparing each lookup with `op`: a shadow map's sampler.
    public SamplerSpec withCompare(CompareOp op) {
        return new SamplerSpec(filter, addressMode, mipFilter, Optional.of(op));
    }
}
