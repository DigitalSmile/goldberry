package dev.goldberry.gpu;

import java.util.Objects;
import java.util.Optional;

/// What a [GpuSampler] is made as: one filter for minifying and magnifying,
/// one address mode for every axis, how it reads between mip levels, whether
/// a lookup compares against a reference depth, and how far it filters along a
/// slanted footprint.
///
/// Without a mip filter a sampler reads level 0 alone, whatever the texture
/// has: what the toolkit's own textures, with one level, want. With one it
/// reads the level the footprint calls for, and [Filter#LINEAR] blends the two
/// nearest. A comparison sampler is what a shadow map is read with: HLSL's
/// `SampleCmp` gives 0 or 1 per texel, filtered.
///
/// **Anisotropy.** A surface seen at a grazing angle covers a footprint much
/// longer than it is wide. A trilinear sampler picks the mip level of the
/// longer axis and blurs the shorter one; an anisotropic sampler takes up to
/// `maxAnisotropy` samples along the longer axis at the level of the shorter,
/// and keeps the detail ([#withAnisotropy]). 1 is off, and 16 is the most any
/// driver takes. It matters for a mipmapped texture, so it goes with a mip
/// filter.
///
/// @param filter        how it reads between texels
/// @param addressMode   what it reads outside 0 to 1
/// @param mipFilter     how it reads between mip levels, or empty for level 0
///                      alone
/// @param compare       the comparison a lookup makes, or empty for a plain
///                      lookup
/// @param maxAnisotropy how many samples it may take along a slanted
///                      footprint: 1 (off) to [#MAX_ANISOTROPY]
public record SamplerSpec(
        Filter filter,
        AddressMode addressMode,
        Optional<Filter> mipFilter,
        Optional<CompareOp> compare,
        float maxAnisotropy) {

    /// The most anisotropy a sampler is made with. SDL reports no limit of
    /// the device's, and 16 is the limit of every driver it runs on.
    public static final float MAX_ANISOTROPY = 16f;

    /// Checks nothing is missing, and clamps the anisotropy to
    /// [#MAX_ANISOTROPY].
    ///
    /// @throws IllegalArgumentException when `maxAnisotropy` is not a number
    ///                                  or is below 1
    public SamplerSpec {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(addressMode, "addressMode");
        Objects.requireNonNull(mipFilter, "mipFilter");
        Objects.requireNonNull(compare, "compare");
        if (!(maxAnisotropy >= 1f)) {
            throw new IllegalArgumentException("anisotropy " + maxAnisotropy + "; 1 is off, and it is no less");
        }
        maxAnisotropy = Math.min(maxAnisotropy, MAX_ANISOTROPY);
    }

    /// `filter`, `addressMode`, `mipFilter` and `compare`, with no
    /// anisotropy.
    public SamplerSpec(
            Filter filter, AddressMode addressMode, Optional<Filter> mipFilter, Optional<CompareOp> compare) {
        this(filter, addressMode, mipFilter, compare, 1f);
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
        return new SamplerSpec(filter, mode, mipFilter, compare, maxAnisotropy);
    }

    /// This spec reading between mip levels with `filter`.
    public SamplerSpec withMipFilter(Filter filter) {
        return new SamplerSpec(this.filter, addressMode, Optional.of(filter), compare, maxAnisotropy);
    }

    /// This spec comparing each lookup with `op`: a shadow map's sampler.
    public SamplerSpec withCompare(CompareOp op) {
        return new SamplerSpec(filter, addressMode, mipFilter, Optional.of(op), maxAnisotropy);
    }

    /// This spec taking up to `max` samples along a slanted footprint: a
    /// textured ground seen at a grazing angle. 1 turns it off, and more than
    /// [#MAX_ANISOTROPY] is taken as that.
    ///
    /// ```java
    /// var ground = device.createSampler(SamplerSpec.trilinear().withAnisotropy(8));
    /// ```
    ///
    /// @throws IllegalArgumentException when `max` is not a number or is
    ///                                  below 1
    public SamplerSpec withAnisotropy(float max) {
        return new SamplerSpec(filter, addressMode, mipFilter, compare, max);
    }

    /// Whether it filters anisotropically: more than one sample along a
    /// slanted footprint.
    public boolean isAnisotropic() {
        return maxAnisotropy > 1f;
    }
}
