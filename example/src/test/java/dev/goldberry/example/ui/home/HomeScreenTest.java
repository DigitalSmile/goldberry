package dev.goldberry.example.ui.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.example.docs.BookSections;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.Section;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.text.Link;

@DisplayName("the Home screen")
class HomeScreenTest {

    private final List<String> picked = new ArrayList<>();
    private int searches;
    private int tours;

    private Element root(List<String> recent) {
        var tree = new ElementTree(
                new HomeScreen(recent, Map.of("buttons", 7), picked::add, () -> searches++, () -> tours++));
        tree.flush();
        return tree.root();
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

    /// The `Link` carrying `id`: the widget a document or a screen wrote, not
    /// the text node under it that the id is styled on.
    private static Link linkWithId(Element from, String id) {
        if (from.widget() instanceof Link link && id.equals(link.attributes().id())) {
            return link;
        }
        for (var child : from.children()) {
            var found = linkWithId(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void press(Element element) {
        ((Pressable) element.widget()).onPress().run();
    }

    @Test
    @DisplayName("has a tile per section and a link per screen")
    void tiles() {
        var root = root(List.of());

        for (var section : Section.values()) {
            assertNotNull(byId(root, "home-" + section.name().toLowerCase(Locale.ROOT)), section.title());
        }
        for (var tab : Gallery.TABS) {
            assertNotNull(byId(root, "home-link-" + tab.name()), tab.title());
        }
    }

    @Test
    @DisplayName("a link opens its screen, and the search field and the tour do what they say")
    void links() {
        var root = root(List.of());

        press(byId(root, "home-link-forms"));
        press(byId(root, "home-search"));

        assertEquals(List.of("forms"), picked);
        assertEquals(1, searches);
        assertEquals(0, tours);
    }

    @Test
    @DisplayName("offers the screens visited lately, and nothing when there are none")
    void recent() {
        assertNull(byId(root(List.of()), "home-recent"));
        var recent = byId(root(List.of("buttons", "forms")), "home-recent");
        assertNotNull(recent);
        assertEquals(3, recent.children().size(), "a label and two chips");
    }

    @Test
    @DisplayName("lists the guide's reading chapters as links, part by part, each to a chapter the book has")
    void guide() {
        var root = root(List.of());

        assertEquals(
                List.of("Overview", "Getting started", "Performance", "Developer guide", "Contributing"),
                GuideParts.PARTS.stream().map(GuideParts.Part::title).toList());
        for (var part : GuideParts.PARTS) {
            assertNotNull(
                    byId(root, "guide-" + part.title().toLowerCase(Locale.ROOT).replace(' ', '-')));
            for (var chapter : part.chapters()) {
                var link = linkWithId(root, HomeScreen.guideLinkId(chapter.id()));
                assertNotNull(link, chapter.title());
                var target = DocLink.page(chapter.page());
                assertEquals(target.url(), link.href());
                assertTrue(BookSections.resolves(target), () -> chapter.page() + " is not a chapter of the book");
            }
        }
    }
}
