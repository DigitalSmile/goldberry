package io.github.digitalsmile.goldberry.natives.sdl.event;

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

    /// The window's top-left corner moved on the desktop.
    ///
    /// Sent liberally, and during a drag continuously — the same way
    /// [#WINDOW_RESIZED] is. What consumes it deduplicates it
    /// ([ADR-0270]).
    WINDOW_MOVED(0x205),

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

    /// The window was maximized — by the user or by
    /// [io.github.digitalsmile.goldberry.natives.sdl.calls.SdlWindowCalls.MaximizeWindow].
    ///
    /// **The event is the only truth about the state.** Asking to maximize is a
    /// request a window manager may refuse, delay or grant in part, so a window
    /// is maximized when SDL says it is and not when we asked (ADR-0252).
    WINDOW_MAXIMIZED(0x20A),

    /// The window went back to its ordinary size — SDL's `RESTORED`, which is
    /// what un-maximizing and un-minimizing both report.
    WINDOW_RESTORED(0x20B),

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

    /// The composition string an input method is assembling — `SDL_TextEditingEvent`.
    ///
    /// Not an edit, and that is the whole of why it is a separate event
    /// (ADR-0289): a Japanese, Chinese or Korean user sees an underlined string
    /// being built and chooses among candidates, and only what they accept
    /// arrives as [#TEXT_INPUT]. A toolkit that inserted this into the document
    /// would be inserting text the user has not chosen, and then deleting it.
    ///
    /// Arrives only between `SDL_StartTextInput` and `SDL_StopTextInput`, and
    /// an empty string means the composition has ended.
    TEXT_EDITING(0x302),

    /// The desktop's light-or-dark setting changed — SDL's
    /// `SDL_EVENT_SYSTEM_THEME_CHANGED`.
    ///
    /// **Not a window event**, though SDL delivers it through the same queue: it
    /// concerns the session, so it carries no window id and whoever translates it
    /// tells every window there is. It arrives while the application is running,
    /// which on every desktop with a sunset schedule is once a day
    /// (`docs/gaps.md` G26, [ADR-0322]).
    SYSTEM_THEME_CHANGED(0x108),

    /// One file of a drag-and-drop gesture was dropped on a window —
    /// `SDL_EVENT_DROP_FILE` (`docs/gaps.md` G35b, [ADR-0330]).
    ///
    /// `SDL_DropEvent.data` is the file's name and dies at the next pump, like
    /// every other string SDL hands over in an event.
    DROP_FILE(0x1000),

    /// The pointer moved over the window while a drag was in progress —
    /// `SDL_EVENT_DROP_POSITION`.
    ///
    /// Not surfaced as an event of its own: what it is read for is the
    /// **position**, which the drop that follows it may or may not carry
    /// depending on the platform. The backend keeps the last one so a drop always
    /// knows where it landed.
    DROP_POSITION(0x1004),

    /// The gesture ended — `SDL_EVENT_DROP_COMPLETE`, which arrives once after
    /// however many [#DROP_FILE]s there were, and also after a drag that dropped
    /// nothing.
    DROP_COMPLETE(0x1003),

    /// A new gesture is starting — `SDL_EVENT_DROP_BEGIN`.
    ///
    /// Carries no file and, per SDL's own header, no position. It is read only to
    /// throw away whatever a previous gesture left behind, which on a platform
    /// that sent no `DROP_COMPLETE` would otherwise be dropped twice.
    DROP_BEGIN(0x1002),

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
