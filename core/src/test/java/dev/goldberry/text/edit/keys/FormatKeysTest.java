package dev.goldberry.text.edit.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.text.mark.MarkKind.Style;

/// The formatting keys a rich editor takes, and that a plain one never sees.
class FormatKeysTest {

    private static KeyEvent press(Key key, Mod... mods) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.of(mods), false, null);
    }

    private static KeyEvent primary(Key key, Mod... more) {
        var mods = new ArrayList<>(List.of(more));
        mods.add(PrimaryModifier.current());
        return press(key, mods.toArray(Mod[]::new));
    }

    @Test
    @DisplayName("a messenger's set, on the desktop's own modifier")
    void keys() {
        assertEquals(new FormatCommand.Toggle(Style.BOLD), EditKeys.format(primary(Key.B)));
        assertEquals(new FormatCommand.Toggle(Style.ITALIC), EditKeys.format(primary(Key.I)));
        assertEquals(new FormatCommand.Toggle(Style.UNDERLINE), EditKeys.format(primary(Key.U)));
        assertEquals(new FormatCommand.Toggle(Style.STRIKE), EditKeys.format(primary(Key.X, Mod.SHIFT)));
        assertEquals(new FormatCommand.Toggle(Style.CODE), EditKeys.format(primary(Key.M, Mod.SHIFT)));
        assertEquals(new FormatCommand.Toggle(Style.SPOILER), EditKeys.format(primary(Key.P, Mod.SHIFT)));
        assertEquals(FormatCommand.Simple.LINK, EditKeys.format(primary(Key.K)));
        assertEquals(FormatCommand.Simple.CLEAR, EditKeys.format(primary(Key.N, Mod.SHIFT)));
    }

    @Test
    @DisplayName("not a formatting key: a plain letter, AltGr, a release, cut")
    void others() {
        assertNull(EditKeys.format(press(Key.B)));
        assertNull(EditKeys.format(primary(Key.B, Mod.ALT)));
        assertNull(EditKeys.format(
                new KeyEvent(KeyEvent.Kind.RELEASED, Key.B, Modifiers.of(PrimaryModifier.current()), false, null)));
        assertNull(EditKeys.format(primary(Key.X)), "Ctrl+X is cut");
    }

    @Test
    @DisplayName("the plain key map has no meaning for Ctrl+B")
    void plainMapLeavesThem() {
        assertNull(EditKeys.of(primary(Key.B), EditSurface.DOCUMENT));
    }
}
