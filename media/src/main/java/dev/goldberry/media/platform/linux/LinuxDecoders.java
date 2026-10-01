package dev.goldberry.media.platform.linux;

import java.util.Optional;

/// What `PlatformDecoders`, in the exported package, may ask of this one.
///
/// Public so that the exported package can reach it; the package is not
/// exported, so nothing outside the module can.
public final class LinuxDecoders {

    private LinuxDecoders() {}

    /// Why GStreamer is not bound, or empty when it is. Binds it on the first
    /// call.
    public static Optional<String> unavailableReason() {
        return GStreamer.unavailableReason();
    }
}
