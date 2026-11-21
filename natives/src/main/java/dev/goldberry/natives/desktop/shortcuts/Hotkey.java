package dev.goldberry.natives.desktop.shortcuts;

import java.util.Objects;

/// One shortcut to ask the desktop for.
///
/// @param id          the application's name for it, unique within one bind
/// @param description what the desktop shows the user when it asks; the portal
///                    puts it in its approval dialog and its settings
/// @param key         the key, never a modifier
/// @param modifiers   which modifiers must be held, as a mask of [#SHIFT],
///                    [#CTRL], [#ALT] and [#META]
public record Hotkey(String id, String description, HotkeyKey key, int modifiers) {

    /// Shift. The four bits are the ones `:core`'s modifier mask uses, so the
    /// mask crosses as it is.
    public static final int SHIFT = 1;

    /// Control.
    public static final int CTRL = 1 << 1;

    /// Alt, Option.
    public static final int ALT = 1 << 2;

    /// Super, Command, the Windows key.
    public static final int META = 1 << 3;

    private static final int ALL = SHIFT | CTRL | ALT | META;

    public Hotkey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(key, "key");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a hotkey needs an id");
        }
        if ((modifiers & ~ALL) != 0) {
            throw new IllegalArgumentException("unknown modifier bits in " + Integer.toBinaryString(modifiers));
        }
    }

    /// Whether `modifier` must be held.
    public boolean has(int modifier) {
        return (modifiers & modifier) != 0;
    }

    /// The trigger in the XDG shortcuts specification's words, which is what
    /// the portal's `preferred_trigger` reads: `CTRL+ALT+m`, `LOGO+F5`.
    public String xdgTrigger() {
        var text = new StringBuilder();
        if (has(CTRL)) {
            text.append("CTRL+");
        }
        if (has(ALT)) {
            text.append("ALT+");
        }
        if (has(SHIFT)) {
            text.append("SHIFT+");
        }
        if (has(META)) {
            text.append("LOGO+");
        }
        return text.append(key.keysymName()).toString();
    }
}
