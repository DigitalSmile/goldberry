package io.github.digitalsmile.goldberry.render.backend.sdl3;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// When a window presents through the GPU rather than its window surface
/// (`docs/gpu-plan.md`, D3; ADR-0479), from two system properties:
///
/// - `goldberry.gpu`: `off` never touches the GPU, `auto` (the default) uses
///   it when something needs it;
/// - `goldberry.gpu.composite`: `auto` (the default) composites a window when a
///   GPU layer needs it, `always` composites every window from its first frame,
///   `never` composites none.
///
/// Nothing needs it before GPU layers exist (phase 4), so the default composites
/// nothing and every window presents exactly as it did. `always` is how the
/// composited path is run and measured until then, and how the showcase is held
/// to looking the same both ways.
enum Composition {
    /// No window is composited.
    NEVER,
    /// A window is composited when a GPU layer needs it.
    AUTO,
    /// Every window is composited from its first frame.
    ALWAYS;

    /// `off`, `auto`.
    static final String GPU_PROPERTY = "goldberry.gpu";

    /// `never`, `auto`, `always`.
    static final String COMPOSITE_PROPERTY = "goldberry.gpu.composite";

    /// The policy the two properties give. A value that is not understood is
    /// taken as the default, as every tuning flag here is: a typo should not stop
    /// a window opening.
    static Composition fromProperties() {
        return of(System.getProperty(GPU_PROPERTY), System.getProperty(COMPOSITE_PROPERTY));
    }

    /// The policy `gpu` and `composite` give; either may be null for unset.
    static Composition of(@Nullable String gpu, @Nullable String composite) {
        if (gpu != null && gpu.trim().toLowerCase(Locale.ROOT).equals("off")) {
            return NEVER;
        }
        if (composite == null) {
            return AUTO;
        }
        return switch (composite.trim().toLowerCase(Locale.ROOT)) {
            case "never" -> NEVER;
            case "always" -> ALWAYS;
            default -> AUTO;
        };
    }
}
