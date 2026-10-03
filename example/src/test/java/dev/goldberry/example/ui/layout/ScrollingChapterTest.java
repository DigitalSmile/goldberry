package dev.goldberry.example.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.Scrollbars;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Scrolling screen, mounted the way the window mounts it, and its plain
/// viewport driven from its buttons.
class ScrollingChapterTest {

    private ShowcaseScene scene;
    private Fonts fonts;

    @BeforeEach
    void open() {
        RendererRequirement.enforce();
        scene = new ShowcaseScene();
        fonts = Fonts.bundled();
    }

    @AfterEach
    void close() {
        if (fonts != null) {
            fonts.close();
        }
        if (scene != null) {
            scene.close();
        }
    }

    private Session session() {
        return Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root("scrolling"));
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(ScrollingChapterTest::walk));
    }

    /// What the line under the grid says, after the glide has finished.
    private static String offset(Session session) {
        session.advance(Duration.ofSeconds(1));
        return ((Text) session.byId("viewport-offset").orElseThrow().widget()).content();
    }

    @Test
    @DisplayName("fills its tab with a card per viewport")
    void cards() {
        try (var session = session()) {
            var screen = session.byId("screen-scrolling").orElseThrow();
            assertTrue(screen.classes().contains("fills"), "the screen does not fill its tab");
            var ids = walk(screen)
                    .filter(element -> element.widget() instanceof Card)
                    .map(Element::id)
                    .toList();
            assertEquals(List.of("scroll-card", "scrolling-viewport", "console-card"), ids);
        }
    }

    @Test
    @DisplayName("moves the grid down and right from its buttons, and back to the start")
    void buttonsMoveTheGrid() {
        try (var session = session()) {
            var start = offset(session);
            assertTrue(start.startsWith("Scrolled 0 across and 0 down"), start);

            session.click("viewport-down");
            assertTrue(offset(session).contains("and " + ViewportCard.STEP + " down"), offset(session));

            session.click("viewport-right");
            assertTrue(offset(session).startsWith("Scrolled " + ViewportCard.STEP + " across"), offset(session));

            session.click("viewport-home");
            assertEquals(start, offset(session));
        }
    }

    @Test
    @DisplayName("switches the window's scroll bars")
    void switchesTheBars() {
        try (var session = session()) {
            var was = scene.model().scrollbars();

            session.click("viewport-bars");

            assertNotEquals(was, scene.model().scrollbars());
            assertEquals(
                    was == Scrollbars.OVERLAY ? Scrollbars.ALWAYS : Scrollbars.OVERLAY,
                    scene.model().scrollbars());
        }
    }
}
