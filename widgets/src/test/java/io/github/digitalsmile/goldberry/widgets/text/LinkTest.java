package io.github.digitalsmile.goldberry.widgets.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.registry.ActionRegistry;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// §2's `link` — text that does something ([ADR-0346]).
class LinkTest {

    private final List<String> log = new ArrayList<>();

    private TestHost host;

    @BeforeEach
    void setUp() {
        host = new TestHost();
    }

    private Element painted(Link link) {
        var tree = new ElementTree(link, host);
        tree.flush();
        return tree.root().children().getFirst();
    }

    private LinkText text(Link link) {
        return (LinkText) painted(link).widget();
    }

    private static void click(LinkText link) {
        link.onPointer(new PointerEvent(
                PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, new ElementTree(link).root()));
    }

    private static KeyEvent press(Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
    }

    @Nested
    @DisplayName("the word")
    class TheWord {

        @Test
        @DisplayName("the text is the styled node, and the composition styles nothing")
        void theTextCarriesTheCssType() {
            var node = painted(new Link("Docs", () -> {}).id("docs"));

            assertInstanceOf(LinkText.class, node.widget());
            assertEquals("link", node.type());
            assertEquals("docs", node.id());
        }

        @Test
        @DisplayName("visited is the application's word, and a class")
        void visitedIsAClass() {
            assertTrue(text(new Link("Docs", () -> {}).visited(true)).classes().contains("visited"));
            assertFalse(text(new Link("Docs", () -> {})).classes().contains("visited"));
        }

        @Test
        @DisplayName("a link needs a word")
        void needsALabel() {
            assertThrows(IllegalArgumentException.class, () -> new Link("", () -> {}));
        }

        @Test
        @DisplayName("a word with nothing behind it takes no focus")
        void inertWithoutATarget() {
            var bare = text(new Link("Docs", null, null, false, null));

            assertFalse(bare.isFocusable());
            assertNull(bare.onPress());
        }
    }

    @Nested
    @DisplayName("following it")
    class Following {

        @Test
        @DisplayName("an action runs on a click and on Enter, and not on Space")
        void actionRuns() {
            var link = text(new Link("Docs", () -> log.add("docs")));

            assertTrue(link.isFocusable());
            click(link);
            link.onKey(press(Key.ENTER));
            link.onKey(press(Key.SPACE));

            assertEquals(List.of("docs", "docs"), log);
        }

        @Test
        @DisplayName("an href is handed to the platform through the host")
        void hrefOpensThroughThePlatform() {
            var link = text(Link.external("GitHub", "https://github.com/DigitalSmile/goldberry"));

            click(link);

            assertEquals(List.of("https://github.com/DigitalSmile/goldberry"), host.openedUrls());
        }

        @Test
        @DisplayName("a link with both runs the action and opens the target")
        void both() {
            var link = text(new Link("Docs", () -> log.add("docs"), "https://example.org", false, null));

            click(link);

            assertEquals(List.of("docs"), log);
            assertEquals(List.of("https://example.org"), host.openedUrls());
        }

        @Test
        @DisplayName("a platform that will not open it is not an error")
        void refusedIsNotAnError() {
            host.refuseExternal();
            var link = text(Link.external("GitHub", "https://example.org"));

            click(link);

            assertEquals(List.of("https://example.org"), host.openedUrls(), "it was asked");
        }
    }

    @Nested
    @DisplayName("an external link says so")
    class External {

        @Test
        @DisplayName("it carries the icon and the class, and its name says where it goes")
        void iconClassAndName() {
            var external = text(Link.external("GitHub", "https://example.org"));
            var internal = text(new Link("Docs", () -> {}));

            assertTrue(external.external());
            assertNotNull(external.icon(), "the 12px external-link icon");
            assertTrue(external.classes().contains("external"));
            assertEquals("GitHub, opens outside this window", external.accessibleName());

            assertNull(internal.icon());
            assertEquals("Docs", internal.accessibleName());
            assertEquals(Role.BUTTON, internal.role());
        }

        @Test
        @DisplayName("the icon is the state's: one per link, kept across rebuilds, closed with it")
        void iconIsTheStates() {
            var tree = new ElementTree(Link.external("GitHub", "https://example.org"), host);
            tree.flush();
            var icon = ((LinkText) tree.root().children().getFirst().widget()).icon();
            assertNotNull(icon);

            tree.update(Link.external("GitHub", "https://example.org").visited(true));
            tree.flush();
            var again = ((LinkText) tree.root().children().getFirst().widget()).icon();

            assertTrue(icon == again, "a rebuild must not make a second icon");
            tree.unmount();
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("action, href and visited inflate")
        void inflates() {
            var actions = ActionRegistry.strict().bind("app.docs", () -> log.add("docs"));
            var links = Widgets.inflater(actions).inflateAll(KdlParser.parse("""
                            link action="app.docs" "Read the docs"
                            link href="https://example.org" visited=#true id="gh" "GitHub"
                            """)).stream()
                    .map(Link.class::cast)
                    .toList();

            assertEquals("Read the docs", links.get(0).label());
            assertNotNull(links.get(0).onPress());
            assertNull(links.get(0).href());

            assertEquals("https://example.org", links.get(1).href());
            assertTrue(links.get(1).visited());
            assertNull(links.get(1).onPress());
            assertEquals("gh", links.get(1).attributes().id());

            click(text(links.get(0)));
            assertEquals(List.of("docs"), log);
        }
    }
}
