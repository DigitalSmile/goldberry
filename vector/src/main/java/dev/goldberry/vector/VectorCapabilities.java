package dev.goldberry.vector;

import java.util.Optional;

import dev.goldberry.vector.thorvg.VectorNatives;

/// What `goldberry-vector` can do on this machine: whether its library loaded,
/// and which ThorVG it holds.
///
/// Asking loads the library on the first call. Nothing here throws: a missing
/// natives jar or a library of another version is a reason, not an exception.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
public final class VectorCapabilities {

    private VectorCapabilities() {}

    /// Whether `libgoldberry-vector` loaded and passed its checks.
    public static boolean isAvailable() {
        return VectorNatives.state() instanceof VectorNatives.State.Loaded;
    }

    /// Why it did not, or empty when it did.
    public static Optional<String> unavailableReason() {
        return VectorNatives.unavailableReason();
    }

    /// The build the library answers, `ThorVG 1.1.4; cpu; svg png jpg; simd off`,
    /// or empty when it did not load.
    public static Optional<String> versions() {
        return switch (VectorNatives.state()) {
            case VectorNatives.State.Loaded(var library) -> Optional.of(library.versions());
            case VectorNatives.State.Unavailable _ -> Optional.empty();
        };
    }
}
