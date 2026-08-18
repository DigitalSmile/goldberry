package io.github.digitalsmile.goldberry.widgets.menu;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a menu looks like — the panel, its rows, and the two things about a row
/// that are easy to get wrong and impossible to assert.
///
/// A menu is drawn in a popup window of its own, so none of it appears in any
/// other image in this corpus. The one these exist for: **a menu with nothing
/// checkable in it has no tick column**, and every label in it starts where the
/// icon would ([ADR-0113]). Before that was a per-row decision, every menu in the
/// toolkit indented every label by a column nobody in it could ever use.
class MenuGoldenTest {

    private Icon icon;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        icon = Icon.bundled("folder", 16);
    }

    @AfterEach
    void tearDown() {
        if (icon != null) {
            icon.close();
        }
    }

    /// The rows as [Menus] would build them — which is where the tick column is
    /// decided, so an image of a menu built any other way would be an image of
    /// something nobody sees.
    private static Menu asOpened(Menu menu) {
        var reserve = menu.children().stream()
                .anyMatch(child -> child instanceof Item item && (item.isCheckable() || item.icon() != null));
        return menu.children(menu.children().stream()
                .map(child -> child instanceof Item item
                        ? (Widget) item.reservingLead(reserve)
                        : child)
                .toList());
    }

    private void paint(String name, Theme theme, int width, int height, Menu menu,
            PseudoState... states) {
        var scene = new Row(List.of(asOpened(menu)),
                new Attributes("scene", Set.of(), "scene"));
        var tree = new ElementTree(scene);
        for (var state : states) {
            state.applyTo(tree.root().children().getFirst());
        }
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #scene { padding: 12px; align-items: flex-start;
                                         background: var(--gb-bg) }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    private record PseudoState(int row, Selector.PseudoClass pseudoClass) {

        void applyTo(Element menu) {
            menu.children().get(row).setPseudoClass(pseudoClass, true);
        }
    }

    /// The ordinary case, and most menus are it: nothing checkable, so no column
    /// and no indent.
    @Test
    @DisplayName("a menu with nothing checkable has no tick column")
    void plain() {
        paint("menu-plain", Theme.NORD_DARK, 240, 130, new Menu(
                new Item("Open…", () -> { }).accelerator("Ctrl+O"),
                new Item("Save", () -> { }).accelerator("Ctrl+S"),
                new Separator(),
                new Item("Close", () -> { }).accelerator("Ctrl+W")));
    }

    /// One checkable row gives every row in that menu a column, so the labels
    /// stay in a line.
    @Test
    @DisplayName("one checkable row reserves the column for all of them")
    void checkable() {
        paint("menu-checkable", Theme.NORD_DARK, 240, 130, new Menu(
                new Item("Word wrap", () -> { }).checked(true),
                new Item("Line numbers", () -> { }).checkable(),
                new Separator(),
                new Item("Preferences…", () -> { })));
    }

    /// An icon, an accelerator and a row that leads somewhere, which is every
    /// part of §8's row in one picture.
    @Test
    @DisplayName("icons, accelerators and a submenu row")
    void everything() {
        paint("menu-rows", Theme.NORD_DARK, 260, 130, new Menu(
                new Item("Open folder…", () -> { }).icon(icon).accelerator("Ctrl+O"),
                new Item("Recent").submenu(new Item("notes.txt", () -> { })),
                new Separator(),
                new Item("Nothing here", () -> { }).disabled(true)));
    }

    /// The keyboard's highlight, which is `:focus-visible` and not `:focus` —
    /// a menu opened with a pointer has none of it (ADR-0112).
    @Test
    @DisplayName("the keyboard highlight is on the row the arrows reached")
    void keyboardHighlight() {
        paint("menu-focus", Theme.NORD_DARK, 240, 130, new Menu(
                new Item("Open…", () -> { }).accelerator("Ctrl+O"),
                new Item("Save", () -> { }).accelerator("Ctrl+S"),
                new Separator(),
                new Item("Close", () -> { })),
                new PseudoState(1, Selector.PseudoClass.FOCUS_VISIBLE));
    }

    /// The showcase's own menu, which is the one that gets looked at: an icon
    /// row, two plain rows, a checkable row and a row with a submenu — all five
    /// kinds in the order they appear on screen.
    @Test
    @DisplayName("the showcase's menu, with an icon row and a checkable row")
    void mixed() {
        paint("menu-mixed", Theme.NORD_DARK, 260, 170, new Menu(
                new Item("Switch theme", () -> { }).icon(icon).accelerator("Ctrl+T"),
                new Item("Switch density", () -> { }).accelerator("Ctrl+D"),
                new Separator(),
                new Item("Frame rate", () -> { }).accelerator("Ctrl+F").checked(false),
                new Item("More").submenu(new Item("Reset", () -> { }))));
    }

    @Test
    @DisplayName("a menu on the light theme")
    void light() {
        paint("menu-light", Theme.NORD_LIGHT, 240, 130, new Menu(
                new Item("Open…", () -> { }).accelerator("Ctrl+O"),
                new Item("Save", () -> { }).accelerator("Ctrl+S"),
                new Separator(),
                new Item("Close", () -> { })));
    }
}
