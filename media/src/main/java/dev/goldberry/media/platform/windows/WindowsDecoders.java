package dev.goldberry.media.platform.windows;

import java.util.Optional;

/// What `PlatformDecoders`, in the exported package, may ask of this one.
///
/// Public so that the exported package can reach it; the package is not
/// exported, so nothing outside the module can.
public final class WindowsDecoders {

    private WindowsDecoders() {}

    /// Why Media Foundation is not bound, or empty when it is. Binds it on the
    /// first call.
    public static Optional<String> unavailableReason() {
        return MediaFoundation.unavailableReason();
    }
}
