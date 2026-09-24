package io.github.digitalsmile.goldberry.media.platform.macos;

import java.util.Optional;

/// What `PlatformDecoders`, in the exported package, may ask of this one.
///
/// Public so that the exported package can reach it; the package is not
/// exported, so nothing outside the module can.
public final class MacDecoders {

    private MacDecoders() {}

    /// Why the macOS frameworks are not bound, or empty when they are. Binds
    /// them on the first call.
    public static Optional<String> unavailableReason() {
        return Frameworks.unavailableReason();
    }
}
