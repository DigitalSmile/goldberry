package io.github.digitalsmile.goldberry.render.backend.sdl3;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// When a window presents through the GPU rather than its window surface
/// (`docs/gpu-plan.md`, D3; ADR-0479, ADR-0480), from two system properties:
///
/// - `goldberry.gpu`: `off` never touches the GPU, and GPU layers show what
///   their painters draw without one; `auto` (the default) uses it;
/// - `goldberry.gpu.composite`: `always` (the default) composites every window
///   from its first frame, `auto` composites a window while it shows GPU
///   layers (ADR-0481), `never` composites none, and GPU layers are read back.
///
/// **The GPU by default, the CPU as the fallback** (ADR-0480). A window that
/// cannot be composited -- no `:gpu` on the module path, no device, a window
/// the driver will not claim, a GPU that fails mid-run -- presents on the CPU
/// exactly as it did before there was a GPU path, and the log says which and
/// why. Popups are transparent windows, which SDL will not claim, and stay on
/// the CPU whatever this says.
enum Composition {
    /// No GPU at all: no window is composited, and no GPU layer is rendered.
    OFF,
    /// No window is composited; GPU layers are rendered and read back.
    NEVER,
    /// A window is composited while it shows GPU layers, and for a while after
    /// the last one goes (ADR-0481); until then its layers are read back.
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
            return OFF;
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
    /// Whether GPU layers are shown at all, composited or read back.
    boolean usesGpu() {
        return this != OFF;
    }

    /// Whether a window may be claimed for the GPU and later given back to its
    /// surface: `always` gives one back when a present fails, `auto` when its
    /// last layer goes. Under X11 that give-back is what used to cost the window
    /// its id (ADR-0491).
    boolean claimsWindows() {
        return this == AUTO || this == ALWAYS;
    }

    /// Why a window this policy did not composite is on the CPU, when nothing
    /// else kept it there: the property that says so, for the log and
    /// `Window.presentation()` (ADR-0492).
    String whyOnTheCpu() {
        return switch (this) {
            case OFF -> GPU_PROPERTY + "=off";
            case NEVER -> COMPOSITE_PROPERTY + "=never";
            case AUTO -> "no GPU layer on screen (" + COMPOSITE_PROPERTY + "=auto)";
            case ALWAYS -> "not composited yet";
        };
    }

    String describe() {
        return switch (this) {
            case ALWAYS ->
                "windows present through the GPU where it can be used, and on the CPU where it cannot" + " (-D"
                        + GPU_PROPERTY + "=off for the CPU only)";
            case AUTO ->
                "windows present on the CPU, and through the GPU while they show GPU layers (" + COMPOSITE_PROPERTY
                        + "=auto)";
            case NEVER ->
                "windows present on the CPU, and GPU layers are read back into them (" + COMPOSITE_PROPERTY + "=never)";
            case OFF -> "windows present on the CPU, and nothing uses the GPU (" + GPU_PROPERTY + "=off)";
        };
    }
}
