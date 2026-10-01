package dev.goldberry.render.composite;

import java.util.Objects;

/// What a [Compositor] answered when asked to take a window over: the window,
/// composited, or why not.
///
/// A reason rather than an empty answer, because the window is what logs the
/// outcome (ADR-0480), and "presents on the CPU" says nothing a reader can act
/// on without the why beside it.
public sealed interface Claim {

    /// The window presents through the GPU from now on.
    ///
    /// @param window the compositor's hold on it
    record Claimed(CompositedWindow window) implements Claim {

        /// Checks there is a window.
        public Claimed {
            Objects.requireNonNull(window, "window");
        }
    }

    /// The window stays on the CPU, and nothing of it is left claimed.
    ///
    /// @param reason why, for the log: no device, or what the driver said
    record Refused(String reason) implements Claim {

        /// Checks there is a reason.
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
