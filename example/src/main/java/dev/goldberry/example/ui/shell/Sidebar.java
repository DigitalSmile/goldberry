package dev.goldberry.example.ui.shell;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.GalleryTab;
import dev.goldberry.example.ui.gallery.Section;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.text.Text;

/// The list of screens down the left of the window: Home, then each [Section]
/// with its screens under it.
///
/// ```text
/// ⌂ Home
/// ▾ BASICS
///   ▦ Layout          4
///   ▤ Scrolling       2
/// ▸ CONTROLS
/// ```
///
/// **Wide or a rail.** Wide, every row has its icon, its label and how many
/// cards the screen holds, and a section's heading folds it away. As a rail
/// (`Ctrl+B`) only the icons are left, with the label as a tooltip, and the
/// sections are rules between them — the window gives the room to the screen.
///
/// Which sections are folded is this widget's own state, as a scroll offset
/// is: it is about the list, not about the application, and a new window
/// starts with every section open.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html).
///
/// @param selected the screen showing, which its row marks
/// @param wide     whether the rows have labels, or the sidebar is a rail
/// @param counts   how many cards each screen holds, by name; a screen missing
///                 from it shows no count
/// @param pick     what a row asks for: the screen's name
public record Sidebar(String selected, boolean wide, Map<String, Integer> counts, Consumer<String> pick)
        implements Widget.Stateful {

    /// The sidebar's id, which the tour's last stop names.
    public static final String ID = "sidebar";

    public Sidebar {
        counts = Map.copyOf(counts);
    }

    /// The id of the row for the screen `name`.
    public static String rowId(String name) {
        return "nav-" + name;
    }

    @Override
    public State<?> createState() {
        return new SidebarState();
    }

    /// The folded sections.
    static final class SidebarState extends State<Sidebar> {

        private final Set<Section> folded = EnumSet.noneOf(Section.class);

        @Override
        public Widget build(BuildContext context) {
            var sidebar = widget();
            var rows = new ArrayList<Widget>();
            rows.add(row(HomeScreen.NAME, HomeScreen.TITLE, HomeScreen.ICON, sidebar));
            for (var section : Section.values()) {
                rows.add(heading(section, sidebar.wide()));
                if (!sidebar.wide() || !folded.contains(section)) {
                    for (var tab : Gallery.in(section)) {
                        rows.add(row(tab, sidebar));
                    }
                }
            }
            var list = new Column(rows, Attributes.NONE.classes("nav-list"));
            return new Scroll(
                    List.of(list),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE
                            .id(ID)
                            .classes("sidebar", sidebar.wide() ? "wide" : "rail")
                            .name("Screens"));
        }

        /// A section's heading: a fold when the sidebar is wide, a rule when it
        /// is a rail.
        private Widget heading(Section section, boolean wide) {
            if (!wide) {
                return new Column(List.of(), Attributes.NONE.classes("nav-rule"));
            }
            var open = !folded.contains(section);
            return new Pressable(
                    section.title() + (open ? ", open" : ", folded"),
                    () -> fold(section),
                    false,
                    List.of(
                            new IconView(open ? "chevron-down" : "chevron-right")
                                    .withAttributes(Attributes.NONE.classes("nav-chevron")),
                            new Text(section.title(), Attributes.NONE.classes("nav-section-title"))),
                    Attributes.NONE
                            .id("nav-section-" + section.name().toLowerCase(Locale.ROOT))
                            .classes("nav-section"));
        }

        private void fold(Section section) {
            setState(() -> {
                if (!folded.remove(section)) {
                    folded.add(section);
                }
            });
        }

        private static Widget row(GalleryTab tab, Sidebar sidebar) {
            return row(tab.name(), tab.title(), tab.icon(), sidebar);
        }

        private static Widget row(String name, String title, String icon, Sidebar sidebar) {
            var classes =
                    name.equals(sidebar.selected()) ? new String[] {"nav-item", "selected"} : new String[] {"nav-item"};
            var content = new ArrayList<Widget>();
            content.add(new IconView(icon).withAttributes(Attributes.NONE.classes("nav-icon")));
            var attributes = Attributes.NONE.id(rowId(name)).classes(classes);
            if (sidebar.wide()) {
                content.add(new Text(title, Attributes.NONE.classes("nav-label")));
                var count = sidebar.counts().get(name);
                if (count != null && count > 0) {
                    content.add(new Text(Integer.toString(count), Attributes.NONE.classes("nav-count")));
                }
            } else {
                attributes = attributes.tooltip(title);
            }
            return new Pressable(title, () -> sidebar.pick().accept(name), false, content, attributes);
        }
    }
}
