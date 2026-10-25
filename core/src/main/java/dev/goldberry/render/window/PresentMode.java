package dev.goldberry.render.window;

import java.util.List;

/// How a window hands its frames to the display: what a game's options screen
/// calls vertical sync.
///
/// A window presenting through the GPU switches between them at run time,
/// from its next frame. A window presenting on the CPU keeps the mode it was
/// asked for and presents as its window surface does: paced to the display,
/// which is [#VSYNC], or unpaced, which is [#IMMEDIATE], as
/// `goldberry.backend.vsync` said at launch. The window says which is in
/// effect, and takes the mode asked for when it moves to the GPU.
///
/// The frame loop follows the mode in effect. A window in [#VSYNC] is painted
/// at most once a refresh; in [#MAILBOX] or [#IMMEDIATE] it is painted as fast
/// as it asks, which a frame cap then limits.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html).
public enum PresentMode {
    /// Each frame waits for the display's vertical blank: no tearing, and a
    /// frame of latency when the loop keeps up. Every window has it, and it is
    /// the default.
    VSYNC,
    /// The newest frame is shown at the vertical blank and older ones are
    /// dropped, so the loop never waits for the display: no tearing, and the
    /// least latency short of tearing. Not every driver has it.
    MAILBOX,
    /// Each frame is shown at once, mid-scan if need be: the least latency, and
    /// tearing. Not every driver has it.
    IMMEDIATE;

    /// The modes to try, in order, when this one is asked for: itself first,
    /// then the nearest that a driver without it may have, and [#VSYNC], which
    /// every window has, last.
    public List<PresentMode> fallbacks() {
        return switch (this) {
            case VSYNC -> List.of(VSYNC);
            case MAILBOX -> List.of(MAILBOX, IMMEDIATE, VSYNC);
            case IMMEDIATE -> List.of(IMMEDIATE, MAILBOX, VSYNC);
        };
    }

    /// Whether a window in this mode is painted at most once a refresh.
    public boolean waitsForTheDisplay() {
        return this == VSYNC;
    }
}
