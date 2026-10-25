package dev.goldberry.gpu;

import java.util.Objects;

/// What the system reports a [GpuDevice] to be, from [GpuDevice#adapter].
///
/// Free text, which differs between drivers, vendors and releases: shown to a
/// person in a settings screen or a log, and never parsed to decide what the
/// device can do. Each field is empty where the driver says nothing.
///
/// @param name          the adapter, as `NVIDIA GeForce GTX 1660 Ti` or
///                      `llvmpipe (LLVM 21.1.8, 256 bits)`
/// @param driverName    the driver's own name
/// @param driverVersion the driver's version, in its own format
/// @param driverInfo    anything else the driver says, sometimes several lines
public record GpuAdapter(String name, String driverName, String driverVersion, String driverInfo) {

    /// Checks nothing is null.
    public GpuAdapter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(driverName, "driverName");
        Objects.requireNonNull(driverVersion, "driverVersion");
        Objects.requireNonNull(driverInfo, "driverInfo");
    }
}
