package dev.goldberry.natives.sdl.gpu;

import java.util.Objects;
import java.util.Optional;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuAddressMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuCompareOp;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;

/// Everything a sampler is made from.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param filter      how it reads between texels, minifying and magnifying
///                    alike
/// @param addressMode what it reads outside 0 to 1, on every axis
/// @param mipFilter   how it reads between mip levels, or empty to read level
///                    0 alone, whatever the texture has
/// @param compare     the comparison a lookup makes against its reference
///                    depth, giving 0 or 1, or empty for a plain lookup
public record SdlGpuSamplerDescription(
        SdlGpuFilter filter,
        SdlGpuAddressMode addressMode,
        Optional<SdlGpuFilter> mipFilter,
        Optional<SdlGpuCompareOp> compare) {

    /// Checks nothing is missing.
    public SdlGpuSamplerDescription {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(addressMode, "addressMode");
        Objects.requireNonNull(mipFilter, "mipFilter");
        Objects.requireNonNull(compare, "compare");
    }

    /// `filter` and `addressMode`, reading level 0 alone with no comparison:
    /// the toolkit's own samplers.
    public static SdlGpuSamplerDescription of(SdlGpuFilter filter, SdlGpuAddressMode addressMode) {
        return new SdlGpuSamplerDescription(filter, addressMode, Optional.empty(), Optional.empty());
    }
}
