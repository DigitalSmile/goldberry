package dev.goldberry.example.ui.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ui.gallery.ScreenHeader;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.web.WebView;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.text.Text;

/// The Markdown, HTML and Web view screens, opened the way the window opens them:
/// each fills its tab under one header, and holds the pieces its chapter shows.
@DisplayName("the content screens")
class ContentScreensTest {

    private ShowcaseScene scene;
    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    private Session open(String tab) {
        return Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root(tab));
    }

    private static void assertScreen(Session session, String tab, List<String> ids) {
        var root = session.byId("screen-" + tab).orElseThrow(() -> new AssertionError("no #screen-" + tab));
        assertTrue(root.widget() instanceof Column, "the screen is a column");
        assertEquals(
                1,
                root.children().stream()
                        .filter(child -> child.widget() instanceof ScreenHeader)
                        .count(),
                "one header, first under the screen");
        for (var id : ids) {
            assertTrue(session.byId(id).isPresent(), "no #" + id + " on the " + tab + " screen");
        }
    }

    @Test
    @DisplayName("Markdown is a header over an editor and a preview")
    void markdown() {
        try (var session = open("markdown")) {
            assertScreen(
                    session,
                    "markdown",
                    List.of("markdown-split", "markdown-source", "markdown-followed", "markdown-rendered"));
        }
    }

    @Test
    @DisplayName("HTML is a header over an editor and a preview")
    void html() {
        try (var session = open("html")) {
            assertScreen(session, "html", List.of("html-split", "html-source", "html-followed", "html-rendered"));
        }
    }

    @Test
    @DisplayName("Web view is a header over an address bar, the actions and the page")
    void web() {
        try (var session = open("web")) {
            assertScreen(
                    session,
                    "web",
                    List.of("web-address", "web-url", "web-go", "web-actions", "web-dialog", "web-demo", "web-page"));
            assertEquals(WebPane.GOLDBERRY_URL, page(session).page().url());
        }
    }

    @Test
    @DisplayName("typing an address changes nothing until Go, which shows it with a scheme")
    void goNavigates() {
        try (var session = open("web")) {
            var field = widget(session, "web-url", TextInput.class);
            Objects.requireNonNull(field.onChange(), "the field writes the screen's state")
                    .accept("example.org");
            assertEquals(WebPane.GOLDBERRY_URL, page(session).page().url(), "typing is not navigating");

            session.click("web-go");

            assertEquals("https://example.org", page(session).page().url());
        }
    }

    @Test
    @DisplayName("the callback demo is a document the screen wrote, with Java's handlers on it")
    void demoDocument() {
        try (var session = open("web")) {
            session.click("web-demo");

            var page = page(session).page();
            assertNull(page.url(), "the demo is a document, not an address");
            assertNotNull(page.html());
            assertTrue(page.callbacks().keySet().containsAll(List.of("goldberrySays", "goldberryFails")));
            assertEquals(
                    "(the built-in demo document)",
                    widget(session, "web-url", TextInput.class).value());
            assertEquals(
                    "the page has not called back yet",
                    widget(session, "web-callback", Text.class).content());
        }
    }

    /// The widget of `type` whose own id is `id`. A widget's id is carried down to
    /// what it builds, so the id is read off the widget rather than the element.
    private static <T> T widget(Session session, String id, Class<T> type) {
        return walk(session.byId("root").orElseThrow())
                .map(Element::widget)
                .filter(type::isInstance)
                .filter(widget -> widget instanceof Attributed<?> attributed
                        && id.equals(attributed.attributes().id()))
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type.getSimpleName() + " #" + id));
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(ContentScreensTest::walk));
    }

    private static WebView page(Session session) {
        return widget(session, "web-page", WebView.class);
    }
}
