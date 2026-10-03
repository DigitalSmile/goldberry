package dev.goldberry.example.ui.panels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.docs.CardShape;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Panels screen, mounted the way the window mounts it and then pressed.
@DisplayName("the Panels screen")
class PanelsChapterTest {

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

    /// Tall enough that every card is drawn, so each one can be pressed.
    private void withScreen(Consumer<Session> test) {
        try (var session = Offscreen.of(1280, 2400)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(scene.root("panels"))) {
            test.accept(session);
        }
    }

    @Test
    @DisplayName("has a card for every section of the chapter")
    void hasEveryCard() {
        withScreen(session -> {
            var screen = session.byId("screen-panels").orElseThrow();
            var cards = walk(screen)
                    .filter(element -> element.widget() instanceof Card card && CardShape.isGalleryCard(card))
                    .toList();
            assertEquals(
                    Stream.of(
                                    "panels-panel",
                                    "surface-card",
                                    "group-box-card",
                                    "collapse-card",
                                    "accordion-card",
                                    "carousel-card",
                                    "loading-card",
                                    "numbers-card",
                                    "panels-trend",
                                    "chapters-card",
                                    "timeline-card")
                            .sorted()
                            .toList(),
                    cards.stream().map(Element::id).sorted().toList());
            cards.forEach(element -> assertEquals(List.of(), CardShape.problems((Card) element.widget())));
        });
    }

    @Test
    @DisplayName("the + opens a tab, and the card rebuilds its strip for it")
    void plusOpensATab() {
        withScreen(session -> {
            assertEquals(List.of("Rivendell", "Moria"), labels(session));

            press(session, only(session, "tab-new"));

            assertEquals(List.of("Rivendell", "Moria", "Bree"), labels(session));
            assertEquals("Bree", Models.observable(scene.model(), "app.tab").get());
            assertTrue(
                    walk(session.byId("demo-tabs").orElseThrow())
                            .filter(element -> "tab-body".equals(element.id()))
                            .anyMatch(element -> TabsCard.body("Bree").equals(((Text) element.widget()).resolved())),
                    "the new tab's page is shown");
        });
    }

    @Test
    @DisplayName("a tab's × closes it")
    void crossClosesATab() {
        withScreen(session -> {
            var crosses = parts(session.byId("demo-tabs").orElseThrow(), "tab-close");
            assertEquals(2, crosses.size());

            press(session, crosses.getLast());

            assertEquals(List.of("Rivendell"), labels(session));
        });
    }

    @Test
    @DisplayName("an accordion keeps one section open")
    void anAccordionOpensOneSection() {
        withScreen(session -> {
            var accordion = session.byId("demo-accordion").orElseThrow();
            assertEquals(0, parts(accordion, "collapse-body").size());
            var headers = parts(accordion, "collapse-header");
            assertEquals(3, headers.size());

            session.click(headers.getFirst());
            assertEquals(
                    1,
                    parts(session.byId("demo-accordion").orElseThrow(), "collapse-body")
                            .size());

            session.click(parts(session.byId("demo-accordion").orElseThrow(), "collapse-header")
                    .getLast());
            var open = walk(session.byId("demo-accordion").orElseThrow())
                    .filter(element -> "collapse".equals(element.type()))
                    .map(element -> element.classes().contains("open"))
                    .toList();
            assertEquals(List.of(false, false, true), open);
        });
    }

    /// Presses `element`, then does what the window does after any event: sweeps
    /// the model for what moved, and lets a tab finish arriving or leaving.
    private void press(Session session, Element element) {
        session.click(element);
        Models.refresh(scene.model());
        for (var frame = 0; frame < 30; frame++) {
            session.advance(Duration.ofMillis(50));
        }
    }

    /// The labels of the demo strip's tabs, in the model's order, checked
    /// against the headers the strip is drawing.
    private List<String> labels(Session session) {
        var names = Models.<List<String>>observable(scene.model(), "app.tabs").get();
        assertEquals(names == null ? 0 : names.size(), headers(session).size());
        return names;
    }

    /// The demo strip's tab headers.
    private static List<Element> headers(Session session) {
        return parts(session.byId("demo-tabs").orElseThrow(), "tab");
    }

    private static Element only(Session session, String type) {
        var found = parts(session.byId("demo-tabs").orElseThrow(), type);
        assertEquals(1, found.size(), () -> "one " + type + " in the strip");
        return found.getFirst();
    }

    private static List<Element> parts(Element from, String type) {
        return walk(from).filter(element -> type.equals(element.type())).toList();
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(PanelsChapterTest::walk));
    }
}
