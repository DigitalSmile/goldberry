package dev.goldberry.example.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.input.key.Key;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.panel.card.Card;

/// The Layout screen, mounted the way the window mounts it.
class LayoutChapterTest {

    /// Every card on the screen, in the document's order.
    static final List<String> CARDS = List.of(
            "layout-row",
            "layout-column",
            "layout-spacer",
            "layout-stack",
            "split-card",
            "layout-masonry",
            "layout-subset",
            "layout-units",
            "layout-direction-gap",
            "layout-padding-margin",
            "layout-alignment",
            "layout-wrapping",
            "layout-grow-shrink",
            "layout-limits",
            "layout-position",
            "layout-density");

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
                .session(scene.root("layout"));
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(LayoutChapterTest::walk));
    }

    @Test
    @DisplayName("has a card for each layout widget and each section of sizing")
    void cards() {
        try (var session = session()) {
            var screen = session.byId("screen-layout").orElseThrow();
            var ids = walk(screen)
                    .filter(element -> element.widget() instanceof Card)
                    .map(Element::id)
                    .filter(CARDS::contains)
                    .sorted()
                    .toList();
            assertEquals(CARDS.stream().sorted().toList(), ids);
        }
    }

    @Test
    @DisplayName("switches the window's density from the density card")
    void switchesTheDensity() {
        try (var session = session()) {
            assertEquals(Density.REGULAR, scene.model().density());

            assertTrue(session.focus("layout-switch-density"), "the density button takes no focus");
            session.key(Key.SPACE);

            assertEquals(Density.COMPACT, scene.model().density());
        }
    }
}
