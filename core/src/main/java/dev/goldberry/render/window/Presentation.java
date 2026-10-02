package dev.goldberry.render.window;

import java.util.Objects;

/// How a window gets its frames onto the screen: through the GPU, or on the CPU
/// and why.
///
/// Every window presents one way or the other, and which one is a fact about the
/// machine as much as the application: no device, a driver that refused the
/// window, a policy property, a page embedded where the GPU cannot be trusted
/// to show it. This is that fact as a value, so a log line, a status bar and a
/// test all say the same thing.
///
/// It can change while the window is open: `goldberry.gpu.composite=auto` moves a
/// window to the GPU while it shows GPU layers, and a GPU that fails sends it to
/// the CPU for good. `Window.onPresentationChange` hears each change.
///
/// Read more:
/// [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html#which-way-a-window-presents).
public sealed interface Presentation {

    /// Frames are composited through the GPU, by `driver`.
    ///
    /// @param driver SDL's name for the GPU driver: `vulkan`, `metal`, `direct3d12`
    record Gpu(String driver) implements Presentation {
        public Gpu {
            Objects.requireNonNull(driver, "driver");
        }
    }

    /// Frames are rasterized on the CPU and handed to the window surface.
    ///
    /// @param reason why not the GPU, phrased for a log line: "no GPU device",
    ///               "goldberry.gpu=off"
    record Cpu(String reason) implements Presentation {

        /// Before a window has presented anything: it decides at its first
        /// frame. Here rather than on [Presentation], whose initialisation would
        /// then wait on its own subclass's.
        public static final Cpu UNDECIDED = new Cpu("nothing presented yet");

        public Cpu {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /// `GPU` or `CPU`: the one word to grep a log for.
    default String path() {
        return switch (this) {
            case Gpu _ -> "GPU";
            case Cpu _ -> "CPU";
        };
    }

    /// The long form, for a log line: `through the GPU (vulkan)`,
    /// `on the CPU: no GPU device`.
    default String describe() {
        return switch (this) {
            case Gpu(var driver) -> "through the GPU (" + driver + ")";
            case Cpu(var reason) -> "on the CPU: " + reason;
        };
    }

    /// The short form, for a status bar: `GPU · vulkan`, `CPU · no GPU device`.
    default String label() {
        return switch (this) {
            case Gpu(var driver) -> "GPU · " + driver;
            case Cpu(var reason) -> "CPU · " + reason;
        };
    }
}
