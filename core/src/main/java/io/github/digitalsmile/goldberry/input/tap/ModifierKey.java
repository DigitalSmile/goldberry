package io.github.digitalsmile.goldberry.input.tap;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;

/// A modifier considered as a **key that can be tapped**, rather than as a bit
/// held while another key goes down.
///
/// [Key] deliberately names no modifier: a [io.github.digitalsmile.goldberry.input.key.Shortcut]
/// is a key plus modifiers, and a shortcut on a modifier alone can never fire —
/// there is nothing left to press. That refusal is right, and it left §8's
/// "`Alt`-style keyboard activation" unexpressible, because what a menu bar wants
/// is not an accelerator at all: it is a **tap**, which is a press and a release
/// with nothing in between ([ModifierTaps]).
///
/// So this is a second, much smaller vocabulary beside [Key], and it is small on
/// purpose. There are four modifiers and no way to write a fifth.
///
/// ## Left and right are one modifier
///
/// The same fold [io.github.digitalsmile.goldberry.input.key.Modifiers#fromSdl]
/// does, for the same reason: a platform reports left `Alt` and right `Alt`
/// separately and nothing above the backend has ever cared. Tapping either is
/// tapping this.
///
/// The keycodes are SDL's `SDLK_L*`/`SDLK_R*` values, which are the only form the
/// window ever sees — [Key#fromSdl] answers [Key#UNKNOWN] for every one of them,
/// which is exactly why a tap cannot be detected from a translated [Key] and has
/// to be read from the raw code (ADR-0223).
public enum ModifierKey {
    /// `Alt` / `Option`. The one §8 names, and the reason this type exists.
    ALT(Mod.ALT, 0x400000e2, 0x400000e6),
    CONTROL(Mod.CTRL, 0x400000e0, 0x400000e4),
    SHIFT(Mod.SHIFT, 0x400000e1, 0x400000e5),
    /// Super, Command, Windows — whatever the platform calls the fourth one.
    META(Mod.META, 0x400000e3, 0x400000e7);

    private static final Map<Integer, ModifierKey> BY_KEYCODE = Stream.of(values())
            .flatMap(modifier ->
                    Stream.of(Map.entry(modifier.leftKeycode, modifier), Map.entry(modifier.rightKeycode, modifier)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private final Mod modifier;
    private final int leftKeycode;
    private final int rightKeycode;

    ModifierKey(Mod modifier, int leftKeycode, int rightKeycode) {
        this.modifier = modifier;
        this.leftKeycode = leftKeycode;
        this.rightKeycode = rightKeycode;
    }

    /// The bit this modifier sets while it is held.
    ///
    /// The bridge back to the accelerator vocabulary: a tap of [#ALT] and the
    /// `Alt` in `Alt+F4` are the same physical key, and something that has to
    /// reason about both — a detector deciding whether the modifier state agrees
    /// with what it thinks is held — needs to say so.
    public Mod modifier() {
        return modifier;
    }

    /// SDL's keycode for the left-hand key.
    public int leftKeycode() {
        return leftKeycode;
    }

    /// SDL's keycode for the right-hand key.
    public int rightKeycode() {
        return rightKeycode;
    }

    /// Whether `sdlKeycode` is either of this modifier's two keys.
    public boolean matches(int sdlKeycode) {
        return sdlKeycode == leftKeycode || sdlKeycode == rightKeycode;
    }

    /// The modifier an SDL keycode names, or empty when the code is an ordinary
    /// key.
    ///
    /// Empty rather than null, and empty rather than a fifth constant: "not a
    /// modifier" is the answer for almost every key on the keyboard, and it is
    /// the answer that **disarms** a tap rather than one that has to be named.
    public static Optional<ModifierKey> ofSdl(int sdlKeycode) {
        return Optional.ofNullable(BY_KEYCODE.get(sdlKeycode));
    }
}
