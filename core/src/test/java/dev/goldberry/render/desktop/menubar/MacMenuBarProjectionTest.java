package dev.goldberry.render.desktop.menubar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.natives.desktop.macos.MacMenuBar;
import dev.goldberry.natives.desktop.macos.MacMenuBar.Item.Kind;

/// A menu bar's rows become AppKit's: the translation, run with no Mac.
@DisplayName("the macOS menu bar")
class MacMenuBarProjectionTest {

    @Nested
    @DisplayName("a key equivalent")
    class Keys {

        @Test
        @DisplayName("reads Ctrl as Cmd, because a Mac menu is written for Cmd")
        void controlIsCommand() {
            assertEquals(new KeyEquivalent("o", MacMenuBar.COMMAND), KeyEquivalent.of(Shortcut.of("Ctrl+O")));
        }

        @Test
        @DisplayName("keeps Ctrl when Cmd is named beside it")
        void bothAreKept() {
            assertEquals(
                    new KeyEquivalent("k", MacMenuBar.COMMAND | MacMenuBar.CONTROL),
                    KeyEquivalent.of(Shortcut.of(Key.K, Mod.CTRL, Mod.META)));
        }

        @Test
        @DisplayName("carries Shift and Option, and keeps the letter lower case")
        void shiftAndOption() {
            assertEquals(
                    new KeyEquivalent("z", MacMenuBar.COMMAND | MacMenuBar.SHIFT),
                    KeyEquivalent.of(Shortcut.of("Ctrl+Shift+Z")));
            assertEquals(
                    new KeyEquivalent("s", MacMenuBar.COMMAND | MacMenuBar.OPTION),
                    KeyEquivalent.of(Shortcut.of("Cmd+Alt+S")));
        }

        @Test
        @DisplayName("spells function and arrow keys as NSEvent's private-use characters")
        void functionKeys() {
            assertEquals("\uF704", KeyEquivalent.of(Shortcut.of(Key.F1)).key());
            assertEquals("\uF70F", KeyEquivalent.of(Shortcut.of(Key.F12)).key());
            assertEquals("\uF700", KeyEquivalent.of(Shortcut.of(Key.UP)).key());
            assertEquals("\uF728", KeyEquivalent.of(Shortcut.of(Key.DELETE)).key());
            assertEquals(",", KeyEquivalent.of(Shortcut.of("Cmd+,")).key());
            assertEquals("1", KeyEquivalent.of(Shortcut.of("Ctrl+1")).key());
            assertEquals(0, KeyEquivalent.of(Shortcut.of(Key.F5)).modifiers());
        }
    }

    @Nested
    @DisplayName("a plan")
    class Plans {

        private final List<String> ran = new ArrayList<>();

        @Test
        @DisplayName("turns headings into submenus, rows into tagged commands, and keeps the order")
        void translates() {
            var file = AppMenuItem.submenu(
                    "File",
                    List.of(
                            AppMenuItem.command("Open…", Shortcut.of("Ctrl+O"), () -> ran.add("open")),
                            AppMenuItem.separator(),
                            AppMenuItem.command("Wrap", null, () -> ran.add("wrap"))
                                    .checked(true),
                            AppMenuItem.command("Print", null, () -> ran.add("print"))
                                    .disabled()));
            var view = AppMenuItem.submenu(
                    "View",
                    List.of(AppMenuItem.submenu(
                            "Zoom", List.of(AppMenuItem.command("In", null, () -> ran.add("in"))))));

            var plan = MacMenuBarProjection.plan(List.of(file, view));

            assertEquals(2, plan.items().size());
            var fileMenu = plan.items().getFirst();
            assertEquals(Kind.SUBMENU, fileMenu.kind());
            assertEquals("File", fileMenu.title());
            var rows = fileMenu.children();
            assertEquals(
                    List.of(Kind.COMMAND, Kind.SEPARATOR, Kind.COMMAND, Kind.COMMAND),
                    rows.stream().map(MacMenuBar.Item::kind).toList());
            assertEquals("o", rows.get(0).keyEquivalent());
            assertEquals(MacMenuBar.COMMAND, rows.get(0).modifiers());
            assertTrue(rows.get(2).checked());
            assertFalse(rows.get(3).enabled());
            var zoomIn = plan.items().get(1).children().getFirst().children().getFirst();
            assertEquals("In", zoomIn.title());

            // Every tag runs the row it was given to.
            for (var row : List.of(rows.get(0), rows.get(2), rows.get(3), zoomIn)) {
                plan.actions().get(row.tag()).run();
            }
            assertEquals(List.of("open", "wrap", "print", "in"), ran);
        }

        @Test
        @DisplayName("puts a lone command at the top level in a menu of its own")
        void topLevelCommand() {
            var plan = MacMenuBarProjection.plan(
                    List.of(AppMenuItem.command("Help", null, () -> ran.add("help")), AppMenuItem.separator()));

            assertEquals(1, plan.items().size(), "a separator has no place on a menu bar");
            var help = plan.items().getFirst();
            assertEquals(Kind.SUBMENU, help.kind());
            assertEquals("Help", help.children().getFirst().title());
        }

        @Test
        @DisplayName("greys out a command with nothing to run")
        void nothingToRun() {
            var plan = MacMenuBarProjection.plan(
                    List.of(AppMenuItem.submenu("File", List.of(AppMenuItem.command("Soon", null, null)))));

            var soon = plan.items().getFirst().children().getFirst();
            assertFalse(soon.enabled());
            assertEquals(-1, soon.tag());
            assertTrue(plan.actions().isEmpty());
        }

        @Test
        @DisplayName("is equal for the same rows, so a rebuilt bar does not reinstall AppKit's")
        void stableForTheSameRows() {
            var first = MacMenuBarProjection.plan(
                    List.of(AppMenuItem.submenu("File", List.of(AppMenuItem.command("Open", null, () -> {})))));
            var second = MacMenuBarProjection.plan(
                    List.of(AppMenuItem.submenu("File", List.of(AppMenuItem.command("Open", null, () -> {})))));

            assertEquals(first.items(), second.items());
        }
    }

    @Test
    @DisplayName("runs a row's action and then the host's repaint")
    void andThenRunsBoth() {
        var ran = new ArrayList<String>();
        var row = AppMenuItem.submenu("File", List.of(AppMenuItem.command("Open", null, () -> ran.add("open"))))
                .andThen(() -> ran.add("repaint"));

        row.children().getFirst().onChosen().run();

        assertEquals(List.of("open", "repaint"), ran);
    }

    @Test
    @DisplayName("is not offered off macOS, and the bar stays in the window there")
    void onlyOnMacOs() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            assertTrue(MacMenuBarProjection.current().isEmpty());
            assertFalse(BackendMenuBar.NONE.show("App", List.of()));
        }
    }
}
