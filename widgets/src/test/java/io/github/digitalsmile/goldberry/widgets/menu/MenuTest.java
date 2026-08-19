package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// `menu`, `item` and `separator` as widgets — the half of §8 that is a tree.
///
/// [io.github.digitalsmile.goldberry.widgets.menu.MenusTest] drives the other
/// half, which is a window.
class MenuTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static WidgetRenderer renderer() {
        return new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, "")),
                TestFont.get());
    }

    private static Box render(Widget widget) {
        return renderer().render(new ElementTree(widget));
    }

    /// The text of every box under `box`, depth first — which is how a row's
    /// label and its accelerator are told apart from each other's absence.
    private static List<String> textIn(Box box) {
        var found = new ArrayList<String>();
        collect(box, found);
        return found;
    }

    private static void collect(Box box, List<String> into) {
        if (box.text() != null) {
            into.add(box.text().paragraph().text());
        }
        box.children().forEach(child -> collect(child, into));
    }

    @Test
    @DisplayName("an item draws its label and its accelerator, in that order")
    void labelAndAccelerator() {
        var box = render(new Menu(new Item("Save", () -> { }).accelerator("Ctrl+S")));

        assertEquals(List.of("Save", "Ctrl+S"), textIn(box),
                "the accelerator is a second run, pushed to the far edge by a growing gap —"
                        + " §8's CSS subset has no text-align");
    }

    /// The tick column is a **menu's** decision, not a row's: every row in a menu
    /// with anything checkable in it reserves one so the labels line up, and a
    /// menu with nothing checkable has no column at all — which is most menus, and
    /// fourteen pixels of unexplained indent when it was per row (ADR-0113).
    @Test
    @DisplayName("a row reserves a tick column only when its menu has one")
    void tickColumnIsTheMenusDecision() {
        var plain = new Item("Word wrap", () -> { });
        var checkable = plain.checkable();

        assertFalse(plain.isCheckable(), "no `checked` at all is not a checkbox");
        assertTrue(checkable.isCheckable(), "and `checked=false` is one that is off");
        assertFalse(checkable.isChecked());
        assertTrue(plain.checked(true).isChecked());

        // A row on its own reserves nothing: only the menu knows whether anything
        // in it is checkable.
        assertEquals(0, plain.children().size());
        assertEquals(0, checkable.children().size());
    }

    /// And the menu that has one gives it to every row, including the rows that
    /// are not checkable — which is what keeps the labels in a line.
    @Test
    @DisplayName("one checkable row gives every row in that menu a column")
    void oneCheckableRowReservesForAll() {
        var withCheckable = headersOf(new Menu(
                new Item("Plain", () -> { }),
                new Item("Word wrap", () -> { }).checked(true)));
        var withoutAny = headersOf(new Menu(
                new Item("Plain", () -> { }),
                new Item("Also plain", () -> { })));

        assertEquals(1, withCheckable.get(0).children().size(), "a plain row beside a checkable"
                + " one reserves the column too, or the labels step in and out");
        assertEquals(1, withCheckable.get(1).children().size());
        assertEquals(0, withoutAny.get(0).children().size());
        assertEquals(0, withoutAny.get(1).children().size());
    }

    /// What a menu built for opening looks like — the rows [Menus] rewrites, which
    /// is where the column is decided.
    private static List<Item> headersOf(Menu menu) {
        var reserve = menu.children().stream()
                .anyMatch(child -> child instanceof Item item && (item.isCheckable() || item.icon() != null));
        return menu.children().stream()
                .filter(Item.class::isInstance)
                .map(Item.class::cast)
                .map(item -> item.reservingLead(reserve))
                .toList();
    }

    @Test
    @DisplayName("a menu is a vertical focus scope, so Left and Right stay with the items")
    void verticalScope() {
        assertEquals(FocusScope.VERTICAL, new Menu().focusScope());
    }

    /// `Right` opens a submenu rather than moving focus, which is why the scope is
    /// vertical and not both.
    @Test
    @DisplayName("an item with children knows it leads somewhere")
    void submenus() {
        var plain = new Item("Save", () -> { });
        var parent = new Item("Recent").submenu(new Item("notes.txt", () -> { }));

        assertFalse(plain.hasSubmenu());
        assertTrue(parent.hasSubmenu());
        assertEquals(1, parent.submenu().size());
    }

    @Test
    @DisplayName("a disabled item is not focusable, so the arrows skip it")
    void disabledIsNotFocusable() {
        assertTrue(new Item("Save", () -> { }).isFocusable());
        assertFalse(new Item("Save", () -> { }).disabled(true).isFocusable());
    }

    /// A separator is a line: nothing to focus, so focus traversal skips it for
    /// free rather than by a rule about separators.
    @Test
    @DisplayName("a separator is not focusable and has no content")
    void separator() {
        var box = render(new Menu(new Item("A", () -> { }), new Separator(),
                new Item("B", () -> { })));

        assertEquals(List.of("A", "B"), textIn(box));
        // Not `Handles` at all, which is what makes it unfocusable: focus
        // traversal collects nodes that handle input, and this handles none. The
        // compiler enforces it — `Separator` does not implement the interface, so
        // this is a statement about the type rather than about a flag.
        assertFalse(io.github.digitalsmile.goldberry.input.Handles.class
                .isAssignableFrom(Separator.class));
    }

    @Test
    @DisplayName("a document writes a menu, its rows and its rules")
    void fromKdl() {
        var widget = Widgets.inflater().inflate(KdlParser.parse("""
                menu id="file" {
                    item accelerator="Ctrl+O" "Open…"
                    separator
                    item checked=#true "Word wrap"
                    item "Recent" {
                        item "notes.txt"
                    }
                }
                """).getFirst());

        var menu = assertInstanceOf(Menu.class, widget);
        assertEquals("file", menu.id());
        assertEquals(4, menu.children().size());

        var open = assertInstanceOf(Item.class, menu.children().get(0));
        assertEquals("Open…", open.label());
        assertEquals("Ctrl+O", open.accelerator());
        assertInstanceOf(Separator.class, menu.children().get(1));
        assertTrue(((Item) menu.children().get(2)).checked());

        var recent = (Item) menu.children().get(3);
        assertTrue(recent.hasSubmenu(), "a nested item *is* the submenu syntax");
        assertEquals("notes.txt", ((Item) recent.submenu().getFirst()).label());
    }
}
