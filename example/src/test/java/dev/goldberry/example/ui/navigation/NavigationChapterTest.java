package dev.goldberry.example.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
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
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.nav.steps.Steps;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Navigation screen: its cards, a trail that follows the model's path, and
/// a wizard and a list of steps that move when asked.
@DisplayName("the Navigation screen")
class NavigationChapterTest {

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
        fonts.close();
        scene.close();
    }

    private Session session(Widget root) {
        return Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(root);
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(NavigationChapterTest::walk));
    }

    @Test
    @DisplayName("has a card for the breadcrumbs, the steps and the wizard")
    void cards() {
        try (var session = session(scene.root("navigation"))) {
            var ids = walk(session.byId("screen-navigation").orElseThrow())
                    .map(Element::widget)
                    .filter(widget -> widget instanceof Card card
                            && card.attributes().classes().contains("wall-card"))
                    .map(widget -> ((Card) widget).attributes().id())
                    .toList();

            assertEquals(Set.of("trail-card", "navigation-steps", "wizard-card"), Set.copyOf(ids));
            assertEquals(3, ids.size());
        }
    }

    @Test
    @DisplayName("Go deeper lengthens the model's path, and the trail draws it")
    void deeper() {
        try (var session = session(scene.root("navigation"))) {
            var before = scene.model().path().size();
            session.click("go-deeper");

            assertEquals(before + 1, scene.model().path().size());
            assertTrue(session.byId("crumb-" + before).isPresent(), "the trail did not draw the step the model added");
        }
    }

    @Test
    @DisplayName("Next asks, the card moves the index, and the steps above read it")
    void wizard() {
        try (var session = session(new WizardCard())) {
            session.click("journey-next");

            var steps = walk(session.byId("wizard-card").orElseThrow())
                    .map(Element::widget)
                    .filter(widget -> widget instanceof Steps list
                            && "journey-steps".equals(list.attributes().id()))
                    .map(Steps.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertEquals(1, steps.resolvedCurrent());
        }
    }

    @Test
    @DisplayName("a reachable step in a clickable list takes a click")
    void steps() {
        try (var session = session(new StepsCard())) {
            var steps = walk(session.byId("steps-vertical").orElseThrow())
                    .filter(element -> element.widget() instanceof Styled styled
                            && styled.cssType().equals("step"))
                    .toList();
            assertEquals(3, steps.size());

            session.click(steps.get(2));

            assertEquals(
                    "At step 3 of 3",
                    ((Text) session.byId("steps-at").orElseThrow().widget()).content());
        }
    }
}
