package dev.goldberry.widgets.form.edit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.Separator;

/// The stock text menu's rows, from a field that is only its four answers.
@DisplayName("the stock text menu")
class TextMenuTest {

    /// A field whose answers are set by the test and whose commands are logged.
    private static final class Field implements TextCommands {
        boolean cut;
        boolean copy;
        boolean paste;
        boolean selectAll;
        final List<String> ran = new ArrayList<>();

        @Override
        public boolean canCut() {
            return cut;
        }

        @Override
        public boolean canCopy() {
            return copy;
        }

        @Override
        public boolean canPaste() {
            return paste;
        }

        @Override
        public boolean canSelectAll() {
            return selectAll;
        }

        @Override
        public boolean cut() {
            return ran.add("cut");
        }

        @Override
        public boolean copy() {
            return ran.add("copy");
        }

        @Override
        public boolean paste() {
            return ran.add("paste");
        }

        @Override
        public boolean selectAll() {
            return ran.add("select all");
        }
    }

    private static List<Item> items(Field field) {
        return TextMenu.menu(field).children().stream()
                .filter(Item.class::isInstance)
                .map(Item.class::cast)
                .toList();
    }

    @Test
    @DisplayName("is Cut, Copy and Paste, a separator, and Select all, with the platform's accelerators")
    void rows() {
        var children = TextMenu.menu(new Field()).children();
        var items = items(new Field());

        assertAll(
                () -> assertEquals(5, children.size()),
                () -> assertInstanceOf(Separator.class, children.get(3)),
                () -> assertEquals(
                        List.of("Cut", "Copy", "Paste", "Select all"),
                        items.stream().map(Item::label).toList()),
                () -> assertEquals(
                        List.of(
                                Shortcut.primary(Key.X).toString(),
                                Shortcut.primary(Key.C).toString(),
                                Shortcut.primary(Key.V).toString(),
                                Shortcut.primary(Key.A).toString()),
                        items.stream().map(Item::accelerator).toList()));
    }

    @Test
    @DisplayName("disables each row whose command cannot run now")
    void disabledFollowsTheAnswers() {
        var field = new Field();
        field.copy = true;
        field.selectAll = true;

        assertEquals(
                List.of(true, false, true, false),
                items(field).stream().map(Item::disabled).toList(),
                "a read-only field with a selection: Copy and Select all only");
    }

    @Test
    @DisplayName("runs the field's own command for each row")
    void runsTheCommands() {
        var field = new Field();

        for (var item : items(field)) {
            var press = item.onPress();
            if (press != null) {
                press.run();
            }
        }

        assertEquals(List.of("cut", "copy", "paste", "select all"), field.ran);
    }

    @Test
    @DisplayName("asks the field again every time the rows are built")
    void asksAgain() {
        var field = new Field();
        assertEquals(true, items(field).get(2).disabled());

        field.paste = true;

        assertEquals(false, items(field).get(2).disabled(), "a copy elsewhere enables Paste");
    }
}
