package io.github.digitalsmile.goldberry.input;

import java.util.Locale;
import java.util.Objects;

/// A key with modifiers, as an accelerator table names one (§7.2).
///
/// Written the way a menu prints it — `Ctrl+S`, `Ctrl+Shift+Z`, `F5`, `Alt+F4` —
/// because that string is going to end up beside the menu item anyway, and two
/// spellings of the same shortcut is one more thing to keep in step.
///
/// A value: two shortcuts parsed from the same text are equal and hash the same,
/// which is what lets them be map keys.
///
/// @param key       the key itself, never a modifier
/// @param modifiers exactly which modifiers must be held — `Ctrl+S` does **not**
///                  fire on `Ctrl+Shift+S`, because that is a different shortcut
///                  and applications bind both
public record Shortcut(Key key, Modifiers modifiers) {

    public Shortcut {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(modifiers, "modifiers");
        if (key == Key.UNKNOWN) {
            throw new IllegalArgumentException("a shortcut on an unnamed key can never fire");
        }
    }

    /// A bare key with no modifiers — `F5`, `Escape`, `Delete`.
    ///
    /// The other end of [Mod#and(Key)]: with modifiers you start from the
    /// modifier, and without them there is nothing to start from.
    public static Shortcut of(Key key) {
        return new Shortcut(key, Modifiers.NONE);
    }

    /// A key with exactly these modifiers.
    ///
    /// `Shortcut.of(Key.S, Mod.CTRL, Mod.SHIFT)` for code that has the modifiers
    /// in an array already. [Mod#and(Key)] reads better when they are literals,
    /// because it puts them in the order a menu prints them
    /// ([ADR-0095](../../../../../../book/src/adr/0095-a-shortcut-is-built-from-enums.md)).
    public static Shortcut of(Key key, Mod... mods) {
        return new Shortcut(key, Modifiers.of(mods));
    }

    /// Parses `Ctrl+Shift+S` and friends.
    ///
    /// Case-insensitive, and tolerant about which name a modifier goes by:
    /// `Ctrl`, `Control`, `Cmd`, `Command`, `Super`, `Win`, `Meta`, `Opt` and
    /// `Option` all land where you would expect. The key comes last.
    ///
    /// **`Cmd` is not translated to `Ctrl` on macOS.** A toolkit that silently
    /// remapped them would make `Ctrl+C` mean two different things depending on
    /// where it ran, and an application that wants the platform's own convention
    /// is better served by asking for it than by having it guessed.
    ///
    /// @throws IllegalArgumentException if the text names no key, names a key
    ///         this toolkit does not have, or ends with a modifier
    public static Shortcut of(String text) {
        Objects.requireNonNull(text, "text");
        var parts = text.split("\\+", -1);
        var mods = Modifiers.NONE;
        Key key = null;

        for (var raw : parts) {
            var part = raw.trim();
            if (part.isEmpty()) {
                throw new IllegalArgumentException(
                        "\"" + text + "\" has an empty part; shortcuts read \"Ctrl+Shift+S\"");
            }
            switch (part.toLowerCase(Locale.ROOT)) {
                case "shift" -> mods = mods.and(Mod.SHIFT);
                case "ctrl", "control" -> mods = mods.and(Mod.CTRL);
                case "alt", "opt", "option" -> mods = mods.and(Mod.ALT);
                case "meta", "cmd", "command", "super", "win" -> mods = mods.and(Mod.META);
                default -> {
                    if (key != null) {
                        throw new IllegalArgumentException(
                                "\"" + text + "\" names two keys: " + key + " and " + part);
                    }
                    key = parseKey(part, text);
                }
            }
        }
        if (key == null) {
            throw new IllegalArgumentException("\"" + text + "\" is all modifiers and no key");
        }
        return new Shortcut(key, mods);
    }

    /// Whether a key press matches.
    ///
    /// Repeats match: holding `Ctrl+Z` down repeats the undo, which is what the
    /// platform's own key repeat is for. A shortcut that must not repeat checks
    /// [KeyEvent#repeat()] itself.
    public boolean matches(Key pressed, Modifiers held) {
        return key == pressed && modifiers.equals(held);
    }

    private static Key parseKey(String part, String text) {
        var name = part.toUpperCase(Locale.ROOT);
        // A bare digit is DIGIT_n: "1" is not a legal Java identifier, and
        // spelling shortcuts "Ctrl+DIGIT_1" would be absurd.
        if (name.length() == 1 && name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            name = "DIGIT_" + name;
        }
        name = switch (name) {
            case "ESC" -> "ESCAPE";
            case "DEL" -> "DELETE";
            case "INS" -> "INSERT";
            case "PGUP" -> "PAGE_UP";
            case "PGDN", "PGDOWN" -> "PAGE_DOWN";
            case "," -> "COMMA";
            case "." -> "PERIOD";
            case "/" -> "SLASH";
            case "-" -> "MINUS";
            case "=" -> "EQUALS";
            default -> name.replace('-', '_');
        };
        try {
            return Key.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "\"" + text + "\" names no key this toolkit has: " + part
                            + ". Keys are named as in " + Key.class.getName() + ".", e);
        }
    }

    /// The shortcut as a menu would print it.
    @Override
    public String toString() {
        var text = new StringBuilder();
        if (modifiers.control()) {
            text.append("Ctrl+");
        }
        if (modifiers.alt()) {
            text.append("Alt+");
        }
        if (modifiers.shift()) {
            text.append("Shift+");
        }
        if (modifiers.meta()) {
            text.append("Meta+");
        }
        return text.append(printable(key)).toString();
    }

    private static String printable(Key key) {
        var name = key.name();
        if (name.startsWith("DIGIT_")) {
            return name.substring("DIGIT_".length());
        }
        return name.length() == 1 ? name : capitalize(name);
    }

    private static String capitalize(String name) {
        var words = name.split("_");
        var text = new StringBuilder();
        for (var word : words) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return text.toString();
    }
}
