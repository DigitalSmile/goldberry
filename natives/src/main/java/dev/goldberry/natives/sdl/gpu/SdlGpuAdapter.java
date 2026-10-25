package dev.goldberry.natives.sdl.gpu;

import java.util.Objects;

/// What the system reports a GPU device to be, read from SDL's device
/// properties. Free text: it differs between drivers, vendors and releases, so
/// it is shown to a person and never parsed.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param name          the adapter, as `NVIDIA GeForce GTX 1660 Ti` or
///                      `llvmpipe (LLVM 21.1.8, 256 bits)`; empty where the
///                      driver says nothing
/// @param driverName    the driver's own name, as `NVIDIA` or `llvmpipe`
/// @param driverVersion the driver's version, in its own format
/// @param driverInfo    anything else the driver says, sometimes several lines
public record SdlGpuAdapter(String name, String driverName, String driverVersion, String driverInfo) {

    /// Checks nothing is null.
    public SdlGpuAdapter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(driverName, "driverName");
        Objects.requireNonNull(driverVersion, "driverVersion");
        Objects.requireNonNull(driverInfo, "driverInfo");
    }
}
