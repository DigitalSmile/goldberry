package io.github.digitalsmile.goldberry.input;

import java.util.EnumSet;
import java.util.Set;

/// Which modifier keys were held, as a mask over [Mod].
///
/// ```java
/// Modifiers.of(Mod.CTRL, Mod.SHIFT)
/// Mod.CTRL.and(Mod.SHIFT)            // the same thing, read left to right
/// held.has(Mod.SHIFT)
/// ```
///
/// ## A mask, and one that cannot be built wrong
///
/// This was four booleans, and every caller wrote them positionally —
/// `new Modifiers(true, false, false, false)` — which is four chances to get the
/// order wrong and no way for the compiler to notice. It is one `int` now, and
/// the only way to *make* one is from [Mod] values
/// ([ADR-0095](../../../../../../book/src/adr/0095-a-shortcut-is-built-from-enums.md)).
///
/// The mask is not the API even so: [#mask()] exists for the backend boundary and
/// for tests, and everything else asks [#has(Mod)]. A raw `int` parameter would
/// accept `Key.A.ordinal()` and mean nothing.
///
/// A value, so two of them are equal and hash the same — which is what lets a
/// [Shortcut] be a map key.
///
/// @param mask the [Mod#bit()]s that were down
public record Modifiers(int mask) {

    /// Nothing held — the common case, and the one a plain keypress tests for.
    public static final Modifiers NONE = new Modifiers(0);

    // SDL's SDL_KMOD_* bits. Left and right are separate flags there and every
    // caller here means "either", so they are folded on the way in.
    private static final int SDL_SHIFT = 0x0001 | 0x0002;
    private static final int SDL_CTRL = 0x0040 | 0x0080;
    private static final int SDL_ALT = 0x0100 | 0x0200;
    private static final int SDL_GUI = 0x0400 | 0x0800;

    public Modifiers {
        if ((mask & ~0xF) != 0) {
            throw new IllegalArgumentException(
                    "0x" + Integer.toHexString(mask) + " has bits no Mod owns;"
                            + " build one with Modifiers.of(Mod…) rather than by hand");
        }
    }

    /// The modifiers that are held, in any order.
    public static Modifiers of(Mod... mods) {
        var mask = 0;
        for (var mod : mods) {
            mask |= mod.bit();
        }
        return new Modifiers(mask);
    }

    /// The four-boolean form, kept because a test that wants "shift and nothing
    /// else" reads perfectly well as one.
    ///
    /// Positional, which is why it is not the canonical constructor: it is fine
    /// where all four are written out and a trap where they are computed.
    public Modifiers(boolean shift, boolean control, boolean alt, boolean meta) {
        this((shift ? Mod.SHIFT.bit() : 0)
                | (control ? Mod.CTRL.bit() : 0)
                | (alt ? Mod.ALT.bit() : 0)
                | (meta ? Mod.META.bit() : 0));
    }

    /// Reads a platform bitmask — SDL's, at the only boundary that has one.
    public static Modifiers fromSdl(int sdlMask) {
        return new Modifiers(
                ((sdlMask & SDL_SHIFT) != 0 ? Mod.SHIFT.bit() : 0)
                        | ((sdlMask & SDL_CTRL) != 0 ? Mod.CTRL.bit() : 0)
                        | ((sdlMask & SDL_ALT) != 0 ? Mod.ALT.bit() : 0)
                        | ((sdlMask & SDL_GUI) != 0 ? Mod.META.bit() : 0));
    }

    /// Whether `mod` was held.
    public boolean has(Mod mod) {
        return (mask & mod.bit()) != 0;
    }

    /// These modifiers and one more.
    public Modifiers and(Mod mod) {
        return new Modifiers(mask | mod.bit());
    }

    /// These modifiers plus a key: the accelerator.
    public Shortcut and(Key key) {
        return new Shortcut(key, this);
    }

    /// The modifiers held, as a set — for a menu that wants to print them, or a
    /// test that wants to assert on them without knowing the bit layout.
    public Set<Mod> set() {
        var mods = EnumSet.noneOf(Mod.class);
        for (var mod : Mod.values()) {
            if (has(mod)) {
                mods.add(mod);
            }
        }
        return mods;
    }

    public boolean shift() {
        return has(Mod.SHIFT);
    }

    public boolean control() {
        return has(Mod.CTRL);
    }

    public boolean alt() {
        return has(Mod.ALT);
    }

    public boolean meta() {
        return has(Mod.META);
    }

    /// Whether no modifier was held — the common test for a plain keypress.
    public boolean none() {
        return mask == 0;
    }

    /// Whether **only** `mod` is held, which is what an accelerator usually
    /// means: `Ctrl+S` does not fire on `Ctrl+Shift+S`.
    public boolean only(Mod mod) {
        return mask == mod.bit();
    }

    /// Whether only Ctrl is held.
    public boolean onlyControl() {
        return only(Mod.CTRL);
    }

    @Override
    public String toString() {
        if (none()) {
            return "none";
        }
        var text = new StringBuilder();
        // Ctrl, Alt, Shift, Meta -- the order a menu prints them, which is not
        // the order the bits are in.
        if (control()) {
            text.append("Ctrl+");
        }
        if (alt()) {
            text.append("Alt+");
        }
        if (shift()) {
            text.append("Shift+");
        }
        if (meta()) {
            text.append("Meta+");
        }
        return text.substring(0, text.length() - 1);
    }
}
