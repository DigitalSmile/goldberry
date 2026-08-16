package io.github.digitalsmile.goldberry.input;

import java.util.HashMap;
import java.util.Map;

/// The keys a toolkit has to name.
///
/// Deliberately **not** every key on a keyboard. §7.1 splits keys from text:
/// anything that produces a character arrives as
/// [io.github.digitalsmile.goldberry.backend.BackendEvent.TextInput], already
/// translated through the platform's own layout, compose and IME handling. What
/// is left — and what is here — is the keys that *do* something rather than type
/// something: navigation, editing, modifiers, function keys.
///
/// That split is why there is no `A`…`Z` in this enum. A handler that wants the
/// letter wants the text event; a handler that wants the physical position is
/// asking a question this toolkit does not answer yet.
public enum Key {

    UNKNOWN(0),

    // --- editing and confirmation ---
    ENTER(0x0000000d),
    ESCAPE(0x0000001b),
    BACKSPACE(0x00000008),
    TAB(0x00000009),
    SPACE(0x00000020),
    DELETE(0x0000007f),

    // --- navigation ---
    LEFT(0x40000050),
    RIGHT(0x4000004f),
    UP(0x40000052),
    DOWN(0x40000051),
    HOME(0x4000004a),
    END(0x4000004d),
    PAGE_UP(0x4000004b),
    PAGE_DOWN(0x4000004e),
    INSERT(0x40000049),

    // --- function keys ---
    F1(0x4000003a),
    F2(0x4000003b),
    F3(0x4000003c),
    F4(0x4000003d),
    F5(0x4000003e),
    F6(0x4000003f),
    F7(0x40000040),
    F8(0x40000041),
    F9(0x40000042),
    F10(0x40000043),
    F11(0x40000044),
    F12(0x40000045);

    private static final Map<Integer, Key> BY_KEYCODE = new HashMap<>();

    static {
        for (var key : values()) {
            if (key != UNKNOWN) {
                BY_KEYCODE.put(key.sdlKeycode, key);
            }
        }
    }

    private final int sdlKeycode;

    Key(int sdlKeycode) {
        this.sdlKeycode = sdlKeycode;
    }

    /// SDL's virtual keycode for this key.
    ///
    /// The values are SDL's, and they are what the translation compares against.
    /// They are ASCII for the keys that have an ASCII meaning and
    /// `SDL_SCANCODE_MASK`-tagged for the ones that do not, which is SDL's own
    /// scheme rather than something invented here.
    public int sdlKeycode() {
        return sdlKeycode;
    }

    /// The key for an SDL keycode, or [#UNKNOWN].
    ///
    /// Unknown rather than null: a key this toolkit does not name is not an
    /// error, it is a key whose text event is the interesting part.
    public static Key fromSdl(int keycode) {
        return BY_KEYCODE.getOrDefault(keycode, UNKNOWN);
    }
}
