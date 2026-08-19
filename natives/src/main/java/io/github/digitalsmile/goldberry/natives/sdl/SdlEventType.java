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

    /// The window took the keyboard focus.
    WINDOW_FOCUS_GAINED(0x20E),

    /// The window lost the keyboard focus — to another of this application's
    /// windows, or to another application entirely. Which of the two it was is
    /// not in the event: it is whether anything else of ours gained it.
    WINDOW_FOCUS_LOST(0x20F),

    /// The window manager asked for the window to close.
    WINDOW_CLOSE_REQUESTED(0x210),

    /// The base of the user-defined event range. Goldberry's cross-thread wakeup
    /// is pushed as one of these.
    /// The pointer moved. `SDL_MouseMotionEvent` carries the position.
    MOUSE_MOTION(0x400),

    /// A mouse button went down. `SDL_MouseButtonEvent`.
    MOUSE_BUTTON_DOWN(0x401),

    /// A mouse button came up.
    MOUSE_BUTTON_UP(0x402),

    /// The wheel turned, or a touchpad scrolled. `SDL_MouseWheelEvent`.
    MOUSE_WHEEL(0x403),

    /// A key went down. `SDL_KeyboardEvent`.
    KEY_DOWN(0x300),

    /// A key came up.
    KEY_UP(0x301),

    /// Committed text, already translated by the platform. `SDL_TextInputEvent`.
    ///
    /// Separate from [#KEY_DOWN] on purpose (§7.1): a key is a key and text is
    /// text, and on a compose or IME sequence several keys produce one character.
    TEXT_INPUT(0x303),

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
