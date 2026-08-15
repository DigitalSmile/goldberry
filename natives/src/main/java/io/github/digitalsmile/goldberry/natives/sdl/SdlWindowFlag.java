package io.github.digitalsmile.goldberry.natives.sdl;

import java.util.Collection;

/// The `SDL_WINDOW_*` creation flags Goldberry uses.
///
/// SDL declares these as `Uint64`, so the mask is a `long` even though every flag
/// Goldberry sets today fits in the low 32 bits. Passing a 32-bit value where SDL
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
    HIGH_PIXEL_DENSITY(0x2000L);

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
