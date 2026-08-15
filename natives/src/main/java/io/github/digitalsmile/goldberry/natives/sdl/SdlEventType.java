package io.github.digitalsmile.goldberry.natives.sdl;

/// The `SDL_EventType` values Goldberry dispatches on.
///
/// Only the ones with a consumer. SDL defines around a hundred; transcribing the
/// rest would be a hundred more chances to mistype a hex literal for no benefit,
/// and an event with no handler is skipped by number anyway.
///
/// Every value here is checked against the compiled SDL by the layout probe
/// (ADR-0010): the shim reports what the C preprocessor computed, and a
/// disagreement fails the build. That matters more than it looks. A wrong struct
/// offset usually crashes; a wrong event number does nothing at all — the window
/// simply never closes, and there is no error anywhere to notice.
public enum SdlEventType {

    /// The application was asked to quit — the last window closed, or the session
    /// is ending.
    QUIT(0x100),

    /// The window's contents were lost and must be redrawn.
    WINDOW_EXPOSED(0x204),

    /// The window's logical size changed.
    WINDOW_RESIZED(0x206),

    /// The window's backing store changed size. Distinct from a resize: this also
    /// fires when the scale changes and the logical size does not.
    WINDOW_PIXEL_SIZE_CHANGED(0x207),

    /// The window moved to a display with a different scale.
    WINDOW_DISPLAY_SCALE_CHANGED(0x214),

    /// The window manager asked for the window to close.
    WINDOW_CLOSE_REQUESTED(0x210),

    /// The base of the user-defined event range. Goldberry's cross-thread wakeup
    /// is pushed as one of these.
    USER(0x8000);

    private final int value;

    SdlEventType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_EVENT_" + name();
    }
}
