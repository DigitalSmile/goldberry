package dev.goldberry.natives.sdl.gpu.enums;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/// The shader bytecode formats a device can take, as SDL's
/// `SDL_GPU_SHADERFORMAT_*` bits.
///
/// Asking for a format when creating a device is also how a driver is chosen:
/// SDL creates a Vulkan device only for SPIR-V, a Metal one only for MSL or a
/// metallib, and a Direct3D 12 one only for DXIL or DXBC.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuShaderFormat {
    /// SPIR-V, for Vulkan.
    SPIRV(1 << 1, "spirv"),
    /// DXBC shader model 5.1, for Direct3D 12.
    DXBC(1 << 2, "dxbc"),
    /// DXIL shader model 6.0, for Direct3D 12.
    DXIL(1 << 3, "dxil"),
    /// Metal Shading Language source, compiled by the driver.
    MSL(1 << 4, "msl"),
    /// A precompiled Metal library.
    METALLIB(1 << 5, "metallib");

    private final int bit;
    private final String property;

    SdlGpuShaderFormat(int bit, String property) {
        this.bit = bit;
        this.property = property;
    }

    /// SDL's bit.
    public int bit() {
        return bit;
    }

    /// The device-creation property that asks for this format:
    /// `SDL_PROP_GPU_DEVICE_CREATE_SHADERS_*_BOOLEAN`.
    ///
    /// Public because `SdlGpuDevice`, which asks for it, is in the package the
    /// enumerations were split from. The module exports this package
    /// to `:core` and `:gpu` alone, so no application sees it.
    public String createProperty() {
        return "SDL.gpu.device.create.shaders." + property;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_SHADERFORMAT_" + name();
    }

    /// The formats whose bits are set in `mask`. Bits this enum does not model
    /// (`PRIVATE`, for consoles) are ignored.
    public static Set<SdlGpuShaderFormat> decode(int mask) {
        var formats = EnumSet.noneOf(SdlGpuShaderFormat.class);
        for (var format : values()) {
            if ((mask & format.bit) != 0) {
                formats.add(format);
            }
        }
        return formats;
    }

    /// The bits of `formats`, or'd together.
    public static int mask(Collection<SdlGpuShaderFormat> formats) {
        var mask = 0;
        for (var format : formats) {
            mask |= format.bit;
        }
        return mask;
    }
}
