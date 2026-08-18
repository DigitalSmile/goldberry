package io.github.digitalsmile.goldberry.input;

/// One modifier key, as a bit.
///
/// The type-safe half of an accelerator:
///
/// ```java
/// Mod.CTRL.and(Key.S)                    // Ctrl+S
/// Mod.CTRL.and(Mod.SHIFT).and(Key.S)     // Ctrl+Shift+S
/// Shortcut.of(Key.F5)                    // F5, no modifiers
/// ```
///
/// ## Why `and` and not `|`
///
/// The obvious spelling is `Mod.CTRL | Key.S`, and Java cannot have it: `|` is
/// defined for the integral types and `boolean` and is not overloadable. The
/// alternative that *would* compile — `Mod.CTRL.bit() | Mod.SHIFT.bit()` handed
/// to a method taking an `int` — is a mask with nothing checking it, and
/// `Key.A.ordinal() | Mod.CTRL.bit()` would compile and mean nothing.
///
/// So the mask is real and it is **private to this package's arithmetic**:
/// [#bit()] exists for [Modifiers#fromSdl] and for tests, and the way an
/// application composes modifiers is [#and(Mod)], which can only ever produce
/// [Modifiers], and [#and(Key)], which can only ever produce a [Shortcut]. The
/// chain reads left to right in the order a menu prints it
/// ([ADR-0095](../../../../../../book/src/adr/0095-a-shortcut-is-built-from-enums.md)).
///
/// ## Left and right are one modifier
///
/// A platform reports left Shift and right Shift separately and no accelerator
/// table has ever cared. They are folded on the way in, which is what
/// [Modifiers#fromSdl] does.
public enum Mod {

    SHIFT(1),
    CTRL(1 << 1),
    ALT(1 << 2),
    /// Super, Command, Windows — whatever the platform calls the fourth one.
    ///
    /// **Not translated to [#CTRL] on macOS.** A toolkit that silently remapped
    /// them would make `Ctrl+C` mean two different things depending on where it
    /// ran; an application that wants the platform's convention asks for it.
    META(1 << 3);

    private final int bit;

    Mod(int bit) {
        this.bit = bit;
    }

    /// This modifier's bit in a [Modifiers] mask.
    ///
    /// For the boundary code that has to turn a platform bitmask into one of
    /// these, and for tests. An application composes with [#and(Mod)] instead —
    /// see the class comment for why the mask is not the API.
    public int bit() {
        return bit;
    }

    /// Both modifiers, held together.
    public Modifiers and(Mod other) {
        return Modifiers.of(this, other);
    }

    /// This modifier plus a key: the accelerator.
    public Shortcut and(Key key) {
        return new Shortcut(key, Modifiers.of(this));
    }
}
