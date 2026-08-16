package io.github.digitalsmile.goldberry.natives.sdl;

/// The `SDL_SystemCursor` shapes Goldberry asks the platform for.
///
/// Ordinals in a C enum, and upstream is free to insert into the middle of it:
/// `SDL_SYSTEM_CURSOR_POINTER` is 11 in SDL3 and did not exist in SDL2, while
/// `SDL_SYSTEM_CURSOR_HAND` — SDL2's name for the same shape — was 11 for
/// different reasons. Every value here is checked against the compiled SDL by the
/// layout probe (ADR-0010), because a wrong one shows the user the wrong cursor
/// and reports no error at all.
///
/// Not the whole enum. SDL3 also has eight per-edge window-resize shapes
/// (`SDL_SYSTEM_CURSOR_NW_RESIZE` and friends) which it documents as possibly
/// falling back to the two-headed arrows below; they are absent because nothing
/// asks for them until client-side decorations do (M3).
public enum SdlSystemCursor {

    DEFAULT(0),
    TEXT(1),
    WAIT(2),
    CROSSHAIR(3),
    PROGRESS(4),
    NWSE_RESIZE(5),
    NESW_RESIZE(6),
    EW_RESIZE(7),
    NS_RESIZE(8),
    MOVE(9),
    NOT_ALLOWED(10),
    POINTER(11);

    private final int value;

    SdlSystemCursor(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_SYSTEM_CURSOR_" + name();
    }
}
