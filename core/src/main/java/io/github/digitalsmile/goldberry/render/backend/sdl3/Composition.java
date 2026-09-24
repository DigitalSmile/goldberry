package io.github.digitalsmile.goldberry.render.backend.sdl3;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// When a window presents through the GPU rather than its window surface
/// (`docs/gpu-plan.md`, D3; ADR-0479, ADR-0480), from two system properties:
///
/// - `goldberry.gpu`: `off` never touches the GPU; `auto` (the default) uses
///   it;
/// - `goldberry.gpu.composite`: `always` (the default) composites every window
///   from its first frame, `auto` composites a window only when a GPU layer
///   needs it (phase 4), `never` composites none.
///
/// **The GPU by default, the CPU as the fallback** (ADR-0480). A window that
/// cannot be composited -- no `:gpu` on the module path, no device, a window
/// the driver will not claim, a GPU that fails mid-run -- presents on the CPU
/// exactly as it did before there was a GPU path, and the log says which and
/// why. Popups are transparent windows, which SDL will not claim, and stay on
/// the CPU whatever this says.
enum Composition {
    /// No window is composited.
    NEVER,
    /// A window is composited when a GPU layer needs it: none, until phase 4.
    AUTO,
    /// Every window is composited from its first frame. The default.
    ALWAYS;

    /// `off`, `auto`.
    static final String GPU_PROPERTY = "goldberry.gpu";

    /// `never`, `auto`, `always`.
    static final String COMPOSITE_PROPERTY = "goldberry.gpu.composite";

    /// The policy the two properties give. A composite value that is not
    /// understood is taken as the default, as every tuning flag here is: a typo
    /// should not stop a window opening.
    static Composition fromProperties() {
        return of(System.getProperty(GPU_PROPERTY), System.getProperty(COMPOSITE_PROPERTY));
    }

    /// The policy `gpu` and `composite` give; either may be null for unset.
    static Composition of(@Nullable String gpu, @Nullable String composite) {
        if (gpu != null && gpu.trim().toLowerCase(Locale.ROOT).equals("off")) {
            return NEVER;
        }
        if (composite == null) {
            return ALWAYS;
        }
        return switch (composite.trim().toLowerCase(Locale.ROOT)) {
            case "never" -> NEVER;
            case "auto" -> AUTO;
            default -> ALWAYS;
        };
    }

    /// What the log says about this policy when the backend starts: where
    /// windows will present, and the property that changes it.
    String describe() {
        return switch (this) {
            case ALWAYS ->
                "windows present through the GPU where it can be used, and on the CPU where it cannot" + " (-D"
                        + GPU_PROPERTY + "=off for the CPU only)";
            case AUTO -> "windows present on the CPU until a GPU layer needs one (" + COMPOSITE_PROPERTY + "=auto)";
            case NEVER -> "windows present on the CPU (" + GPU_PROPERTY + "=off or " + COMPOSITE_PROPERTY + "=never)";
        };
    }
}
