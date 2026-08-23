package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import java.util.Collection;

/// The `SDL_TRAYENTRY_*` flags an entry is created with.
///
/// Two rules SDL states and does not enforce twice: **exactly one** of
/// [#BUTTON], [#CHECKBOX] and [#SUBMENU] is mandatory, and [#CHECKED] means
/// nothing on anything but a checkbox. [SdlTray] is where both are checked,
/// because an entry that claims to be neither a button nor a checkbox comes back
/// as a null pointer with no explanation attached.
///
/// The mask is a `long` for the reason
/// [io.github.digitalsmile.goldberry.natives.sdl.window.SdlWindowFlag] gives:
/// [#DISABLED] is `0x80000000`, which is a *negative* `int`. SDL's own type here
/// is `Uint32`, so the value is narrowed at the call and the width is only ever
/// wrong on the Java side of it.
public enum SdlTrayEntryFlag {

    /// A plain command. One of the three mandatory kinds.
    BUTTON(0x00000001L),

    /// A checkable entry. SDL toggles it *itself* before calling back, so the
    /// callback's job is to read the new state rather than to compute it.
    CHECKBOX(0x00000002L),

    /// An entry that opens a submenu. One of the three mandatory kinds, and the
    /// submenu is a separate call — the flag only says the entry may have one.
    SUBMENU(0x00000004L),

    /// Drawn greyed out and not selectable. Optional, and combines with any
    /// mandatory kind.
    DISABLED(0x80000000L),

    /// A checkbox that starts checked. Optional, and valid only with
    /// [#CHECKBOX].
    CHECKED(0x40000000L);

    private final long bit;

    SdlTrayEntryFlag(long bit) {
        this.bit = bit;
    }

    public long bit() {
        return bit;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_TRAYENTRY_" + name();
    }

    /// The `SDL_TrayEntryFlags` value for a set of flags.
    public static int mask(Collection<SdlTrayEntryFlag> flags) {
        var mask = 0L;
        for (var flag : flags) {
            mask |= flag.bit;
        }
        // Narrowed here and nowhere else: SDL's parameter is a Uint32, and the
        // only reason the bits are held as a long is that DISABLED does not fit
        // in a positive int.
        return (int) mask;
    }
}
