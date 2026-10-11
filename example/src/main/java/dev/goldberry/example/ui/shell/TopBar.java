package dev.goldberry.example.ui.shell;

import java.util.List;

import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.menu.MenuBar;
import dev.goldberry.widgets.text.Text;

/// The one bar across the top of the window.
///
/// ```text
/// ◧  Goldberry showcase   [ ⌕ Search screens, cards and commands…  Ctrl K ]   ☀  Menu
/// ```
///
/// The sidebar's switch, the name, the search field, the light, and the
/// overflow menu that holds File, Edit, View and Help. The field is a
/// `pressable` that looks like one: pressing it or `Ctrl+K` opens the search
/// palette, where the typing happens, so the bar never holds a half-typed query.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#menubar).
///
/// @param wide          whether the sidebar shows its labels, for the switch's
///                      icon
/// @param light         whether the light theme is on, for the light's icon
/// @param menu          the overflow menu
/// @param toggleSidebar what the sidebar's switch does
/// @param openSearch    what the field does
/// @param toggleLight   what the light does
public record TopBar(
        boolean wide, boolean light, MenuBar menu, Runnable toggleSidebar, Runnable openSearch, Runnable toggleLight)
        implements Widget.Stateless {

    /// The bar's id.
    public static final String ID = "top-bar";

    /// The search field's id.
    public static final String SEARCH = "search-field";

    @Override
    public Widget build(BuildContext context) {
        return new Row(
                List.of(
                        iconButton(
                                "sidebar-toggle",
                                wide ? "panel-left-close" : "panel-left-open",
                                wide ? "Fold the sidebar to icons (Ctrl+B)" : "Show the sidebar (Ctrl+B)",
                                toggleSidebar),
                        new Row(
                                List.of(
                                        new Text("Goldberry", Attributes.NONE.id("title")),
                                        new Text("showcase", Attributes.NONE.classes("brand-sub"))),
                                Attributes.NONE.classes("brand")),
                        new Pressable(
                                "Search",
                                openSearch,
                                false,
                                List.of(
                                        new IconView("search").withAttributes(Attributes.NONE.classes("search-glyph")),
                                        new Text(
                                                "Search screens, cards and commands…",
                                                Attributes.NONE.classes("search-placeholder")),
                                        new Spacer(),
                                        new Text("Ctrl K", Attributes.NONE.classes("kbd"))),
                                Attributes.NONE.id(SEARCH).tooltip("Search (Ctrl+K)")),
                        new Spacer(),
                        iconButton(
                                "theme",
                                light ? "moon" : "sun",
                                light ? "Switch to the dark theme (Ctrl+T)" : "Switch to the light theme (Ctrl+T)",
                                toggleLight),
                        menu),
                Attributes.NONE.id(ID));
    }

    /// A button that is only an icon, named and with a tooltip, because an icon
    /// alone tells a reader nothing.
    static Pressable iconButton(String id, String icon, String name, Runnable onPress) {
        return new Pressable(
                name,
                onPress,
                false,
                List.of(new IconView(icon)),
                Attributes.NONE.id(id).classes("icon-button").tooltip(name));
    }
}
