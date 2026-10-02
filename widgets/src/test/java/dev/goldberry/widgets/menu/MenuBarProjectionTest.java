package dev.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.render.desktop.menubar.AppMenuItem;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.TestHost;

/// A `menubar` on a platform with a menu bar of its own hands its headings to
/// it and draws nothing; everywhere else it stays in the window.
@DisplayName("a menubar where the platform has a menu bar")
class MenuBarProjectionTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static MenuBar bar(Runnable open) {
        return new MenuBar(
                new Item("File").submenu(new Item("Open…", open).accelerator("Ctrl+O"), new Separator()),
                new Item("Help")
                        .submenu(new Item("About").checkable().checked(true).disabled(true)));
    }

    private static List<Widget> described(Element element) {
        var found = new ArrayList<Widget>();
        collect(element, found);
        return found;
    }

    private static void collect(Element element, List<Widget> into) {
        into.add(element.widget());
        for (var child : element.children()) {
            collect(child, into);
        }
    }

    @Test
    @DisplayName("hands its headings to the platform, draws nothing and binds nothing")
    void projected() {
        var host = new TestHost().platformMenuBar(true);
        var opened = new AtomicInteger();

        var tree = new ElementTree(bar(opened::incrementAndGet), host);

        assertTrue(described(tree.root()).stream().noneMatch(MenuBarRow.class::isInstance), "the bar was drawn too");
        assertFalse(host.press("Ctrl+O"), "the window bound a key the platform's menu fires itself");
        var headings = host.applicationMenus().getLast();
        assertEquals(
                List.of("File", "Help"),
                headings.stream().map(AppMenuItem::label).toList());
        var open = headings.getFirst().children().getFirst();
        assertEquals(Shortcut.of("Ctrl+O"), open.shortcut());
        open.onChosen().run();
        assertEquals(1, opened.get());
    }

    @Test
    @DisplayName("puts the platform's bar back when it goes away")
    void restoresOnUnmount() {
        var host = new TestHost().platformMenuBar(true);
        var tree = new ElementTree(bar(() -> {}), host);

        tree.unmount();

        assertTrue(host.applicationMenus().getLast().isEmpty(), "the platform still shows a bar that has gone");
    }

    @Test
    @DisplayName("stays in the window, with its keys, where the platform has no bar")
    void inTheWindowElsewhere() {
        var host = new TestHost();
        var opened = new AtomicInteger();

        var tree = new ElementTree(bar(opened::incrementAndGet), host);

        assertTrue(described(tree.root()).stream().anyMatch(MenuBarRow.class::isInstance));
        assertTrue(host.press("Ctrl+O"));
        assertEquals(1, opened.get());
    }

    @Test
    @DisplayName("translates rows: submenus, tagged commands, ticks, greyed rows and lines")
    void translation() {
        var rows = AppMenus.rowsOf(List.of(
                new Item("File").submenu(new Item("Open…", () -> {}).accelerator("Ctrl+O"), new Separator()),
                new Item("Wrap", () -> {}).checkable().checked(true),
                new Item("Print", () -> {}).disabled(true),
                new Item("Typo", () -> {}).accelerator("Ctrl+Nope")));

        assertEquals(AppMenuItem.Kind.SUBMENU, rows.get(0).kind());
        assertEquals(AppMenuItem.Kind.SEPARATOR, rows.get(0).children().get(1).kind());
        assertSame(Boolean.TRUE, rows.get(1).checked());
        assertFalse(rows.get(2).enabled());
        assertNull(rows.get(3).shortcut(), "an accelerator that does not parse is left off, not fatal");
    }
}
