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
/// That split is why a handler that wants the letter the user typed wants the
/// text event, and a handler that wants the physical position is asking a
/// question this toolkit does not answer yet.
///
/// The letters and digits are here anyway, and **accelerators are why**. `Ctrl+S`
/// produces no text event on any platform — a modified letter is not committed
/// text — so a toolkit that only named the navigation keys could not express the
/// one shortcut every application has. They are the layout's letters, not the
/// keyboard's positions: SDL's default `latin_letters` translation means the key
/// where `A` sits on a Cyrillic or Thai keyboard still arrives as `A`, so
/// `Ctrl+S` is `Ctrl+S` everywhere, while on AZERTY it stays where the user's own
/// layout puts it (ADR-0055).
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
    F12(0x40000045),

    // --- letters and digits, for accelerators ---
    //
    // SDL's keycodes for these are the ASCII code of the *lowercase* character,
    // which is why the values look like a lookup table nobody wrote: 'a' is 0x61.
    A('a'), B('b'), C('c'), D('d'), E('e'), F('f'), G('g'), H('h'), I('i'),
    J('j'), K('k'), L('l'), M('m'), N('n'), O('o'), P('p'), Q('q'), R('r'),
    S('s'), T('t'), U('u'), V('v'), W('w'), X('x'), Y('y'), Z('z'),

    DIGIT_0('0'), DIGIT_1('1'), DIGIT_2('2'), DIGIT_3('3'), DIGIT_4('4'),
    DIGIT_5('5'), DIGIT_6('6'), DIGIT_7('7'), DIGIT_8('8'), DIGIT_9('9'),

    COMMA(','), PERIOD('.'), SLASH('/'), MINUS('-'), EQUALS('=');

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
    ///
    /// An uppercase letter folds to the lowercase one. SDL's default is to report
    /// the unmodified keycode, so `Shift+S` arrives as `'s'` with the shift
    /// modifier set — but it documents platforms that only ever give modified
    /// keycodes, and on those `Shift+S` would arrive as `'S'` and match nothing.
    /// One `Key` per physical letter either way.
    public static Key fromSdl(int keycode) {
        var folded = keycode >= 'A' && keycode <= 'Z' ? keycode - 'A' + 'a' : keycode;
        return BY_KEYCODE.getOrDefault(folded, UNKNOWN);
    }
}
