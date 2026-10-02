package dev.goldberry.render.desktop.menubar;

import java.util.Objects;

import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.natives.desktop.macos.MacMenuBar;

/// A [Shortcut] in AppKit's terms: the `keyEquivalent` string an `NSMenuItem`
/// matches, and the modifier mask beside it.
///
/// ## `Ctrl` is `Cmd` in the menu bar
///
/// A shortcut means exactly the modifiers it names everywhere else in the
/// toolkit, and `Ctrl` is never quietly turned into `Cmd`. The menu bar is the
/// one exception: a menu's accelerators are written once, `Ctrl+O`, for every
/// platform, and a Mac application whose File menu says ⌃O is one nobody can
/// use. So in the platform's menu bar a `Ctrl` with no `Cmd` beside it is read
/// as `Cmd`; a shortcut that names both keeps both. `Primary+O` needs no
/// reading at all.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
///
/// @param key       the character, lower case; empty for a key AppKit has no
///                  equivalent for
/// @param modifiers `NSEventModifierFlags`
public record KeyEquivalent(String key, long modifiers) {

    /// No key equivalent: a row chosen only with the pointer.
    public static final KeyEquivalent NONE = new KeyEquivalent("", 0);

    public KeyEquivalent {
        Objects.requireNonNull(key, "key");
    }

    /// `shortcut` as AppKit spells it.
    public static KeyEquivalent of(Shortcut shortcut) {
        Objects.requireNonNull(shortcut, "shortcut");
        var key = character(shortcut.key());
        if (key.isEmpty()) {
            return NONE;
        }
        var held = shortcut.modifiers();
        long mask = 0;
        if (held.shift()) {
            mask |= MacMenuBar.SHIFT;
        }
        if (held.alt()) {
            mask |= MacMenuBar.OPTION;
        }
        if (held.meta()) {
            mask |= MacMenuBar.COMMAND;
        }
        if (held.control()) {
            mask |= held.meta() ? MacMenuBar.CONTROL : MacMenuBar.COMMAND;
        }
        return new KeyEquivalent(key, mask);
    }

    /// The character AppKit matches for `key`: the key's own for a letter,
    /// digit or mark, and the private-use code point `NSEvent.h` gives the
    /// others.
    static String character(Key key) {
        return switch (key) {
            case UNKNOWN -> "";
            case ENTER -> "\r";
            case ESCAPE -> "\u001b";
            case BACKSPACE -> "\b";
            case TAB -> "\t";
            case SPACE -> " ";
            case DELETE -> function(0xF728);
            case UP -> function(0xF700);
            case DOWN -> function(0xF701);
            case LEFT -> function(0xF702);
            case RIGHT -> function(0xF703);
            case INSERT -> function(0xF727);
            case HOME -> function(0xF729);
            case END -> function(0xF72B);
            case PAGE_UP -> function(0xF72C);
            case PAGE_DOWN -> function(0xF72D);
            case MENU -> function(0xF735);
            case F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12 ->
                function(0xF704 + key.sdlKeycode() - Key.F1.sdlKeycode());
            default -> String.valueOf((char) key.sdlKeycode());
        };
    }

    /// One of `NSEvent.h`'s function-key characters, `NSUpArrowFunctionKey`
    /// and the rest, which live in the private-use area.
    private static String function(int codePoint) {
        return String.valueOf((char) codePoint);
    }
}
