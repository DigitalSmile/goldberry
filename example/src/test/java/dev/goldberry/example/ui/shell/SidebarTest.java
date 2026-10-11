package dev.goldberry.example.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.Section;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.text.Text;

@DisplayName("the sidebar")
class SidebarTest {

    private final List<String> picked = new ArrayList<>();

    private ElementTree tree(String selected, boolean wide) {
        var tree = new ElementTree(new Sidebar(selected, wide, Map.of("buttons", 7), picked::add));
        tree.flush();
        return tree;
    }

    private static Element byId(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = byId(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Pressable row(Element root, String name) {
        var element = byId(root, Sidebar.rowId(name));
        assertNotNull(element, () -> "no row for " + name);
        return (Pressable) element.widget();
    }

    private static List<String> texts(Element from) {
        var found = new ArrayList<String>();
        if (from.widget() instanceof Text text) {
            found.add(text.content());
        }
        from.children().forEach(child -> found.addAll(texts(child)));
        return found;
    }

    @Test
    @DisplayName("lists Home and every screen, and marks the one showing")
    void rows() {
        var root = tree("buttons", true).root();

        assertNotNull(byId(root, Sidebar.rowId(HomeScreen.NAME)));
        for (var tab : Gallery.TABS) {
            var row = row(root, tab.name());
            assertEquals(
                    tab.name().equals("buttons"),
                    row.attributes().classes().contains("selected"),
                    () -> tab.name() + " is marked wrongly");
        }
    }

    @Test
    @DisplayName("a row asks for its screen")
    void picks() {
        var root = tree("home", true).root();

        row(root, "forms").onPress().run();

        assertEquals(List.of("forms"), picked);
    }

    @Test
    @DisplayName("shows each section's title and a screen's card count when wide")
    void wide() {
        var texts = texts(tree("home", true).root());

        for (var section : Section.values()) {
            assertTrue(texts.contains(section.title()), section.title());
        }
        assertTrue(texts.contains("7"), "Buttons holds seven cards");
    }

    @Test
    @DisplayName("as a rail, keeps the icons and puts each label in a tooltip")
    void rail() {
        var root = tree("home", false).root();

        var buttons = row(root, "buttons");
        assertEquals("Buttons", buttons.attributes().tooltip());
        assertFalse(texts(root).contains("Buttons"));
        assertNull(byId(root, "nav-section-controls"), "a rail has rules rather than headings");
    }

    @Test
    @DisplayName("a section's heading folds its screens away and back")
    void folds() {
        var tree = tree("home", true);
        var heading = (Pressable) byId(tree.root(), "nav-section-controls").widget();

        heading.onPress().run();
        tree.flush();
        assertNull(byId(tree.root(), Sidebar.rowId("buttons")), "a folded section lists nothing");
        assertNotNull(byId(tree.root(), Sidebar.rowId("layout")), "and the others are untouched");

        ((Pressable) byId(tree.root(), "nav-section-controls").widget())
                .onPress()
                .run();
        tree.flush();
        assertNotNull(byId(tree.root(), Sidebar.rowId("buttons")));
    }
}
