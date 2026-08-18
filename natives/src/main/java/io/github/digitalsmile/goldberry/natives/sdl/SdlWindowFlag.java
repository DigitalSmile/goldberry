package io.github.digitalsmile.goldberry.natives.sdl;

import java.util.Collection;

/// The `SDL_WINDOW_*` creation flags Goldberry uses.
///
/// SDL declares these as `Uint64`, so the mask is a `long` even though every flag
/// Goldberry sets today fits in the low 32 bits — `NOT_FOCUSABLE` is `0x80000000`
/// and would be a *negative* `int`. Passing a 32-bit value where SDL
/// expects 64 is the kind of thing that works on one calling convention and
/// truncates on another.
public enum SdlWindowFlag {

    /// The window has no decorations. Client-side decorations are drawn by
    /// Goldberry on top of this.
    BORDERLESS(0x10L),

    /// The user can resize the window.
    RESIZABLE(0x20L),

    /// Created hidden. Goldberry creates windows hidden and shows them once the
    /// first frame is ready, so the user never sees an unpainted window.
    HIDDEN(0x08L),

    /// Give the window a backing store at the display's real pixel density
    /// rather than a scaled-up one.
    ///
    /// **Without this there is no HiDPI**: the window gets a logical-resolution
    /// buffer that the compositor upscales, and every glyph is soft. It is the
    /// single most important flag here.
    HIGH_PIXEL_DENSITY(0x2000L),

    /// A tooltip: a popup that never takes input focus and is not in the
    /// window list.
    ///
    /// Only legal on a window created by `SDL_CreatePopupWindow`, and mutually
    /// exclusive with [#POPUP_MENU] — SDL refuses a popup that claims to be both,
    /// because the two get different treatment from every window manager.
    TOOLTIP(0x40000L),

    /// A menu or a dropdown: a popup that may take focus, is dismissed by the
    /// platform's own conventions, and is not in the window list.
    POPUP_MENU(0x80000L),

    /// The window is never given input focus.
    ///
    /// What makes a tooltip a tooltip rather than a very small window that steals
    /// the caret. A menu wants the opposite and does not set it.
    NOT_FOCUSABLE(0x80000000L),

    /// The window's framebuffer has an alpha channel the compositor honours.
    ///
    /// For a popup with rounded corners: without it the corners are filled with
    /// whatever the platform's default background is, and a menu is a rectangle
    /// with four grey triangles at its corners.
    TRANSPARENT(0x40000000L);

    private final long bit;

    SdlWindowFlag(long bit) {
        this.bit = bit;
    }

    public long bit() {
        return bit;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_WINDOW_" + name();
    }

    public static long mask(Collection<SdlWindowFlag> flags) {
        var mask = 0L;
        for (var flag : flags) {
            mask |= flag.bit;
        }
        return mask;
    }
}
