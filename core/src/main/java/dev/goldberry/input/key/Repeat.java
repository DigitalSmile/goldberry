package dev.goldberry.input.key;

/// What a window accelerator does while its key is **held down** and the
/// platform repeats the press.
///
/// Two kinds of accelerator want opposite answers. An editing one —
/// `Ctrl+Z` stepping back through an undo history, `Ctrl+Y` forward again —
/// should repeat the way a held letter does, because holding it is how a
/// person asks for many of it. A toggle — `Escape` opening and closing a menu,
/// `G` showing and hiding a panel — must not: held for a second, it flips the
/// panel at the platform's repeat rate and leaves it wherever the key happened
/// to come up.
///
/// [#FIRE] is the default, so a binding made before this choice existed keeps
/// what it did. A toggle says [#IGNORE] where it is bound:
///
/// ```java
/// host.shortcut(Shortcut.of(Key.ESCAPE), menu::toggle, Repeat.IGNORE);
/// ```
///
/// A repeat an [#IGNORE] binding declines is still **consumed** by it. The key
/// is that accelerator's; letting the held key fall through to focus
/// navigation or to a widget would make the second half of a long press do
/// something the first half did not.
///
/// Read more: [Accelerators](https://goldberry.dev/docs/guide/input.html#accelerators).
public enum Repeat {

    /// Runs on the first press and on every repeat of it.
    FIRE,

    /// Runs on the first press only; repeats are consumed and do nothing.
    IGNORE;

    /// Whether a press, a repeat or not, runs the binding.
    public boolean runs(boolean repeat) {
        return this == FIRE || !repeat;
    }
}
