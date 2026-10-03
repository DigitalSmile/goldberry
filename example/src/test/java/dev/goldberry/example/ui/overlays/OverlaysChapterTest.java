package dev.goldberry.example.ui.overlays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widgets.overlay.tour.Stop;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Text;

/// The Overlays screen: its cards, the tour's stops, and the cards that answer a
/// press without a window.
@DisplayName("the Overlays screen")
class OverlaysChapterTest {

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

    private Session session(Widget root) {
        return Offscreen.of(1280, 900)
                .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                .fonts(fonts)
                .session(root);
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(OverlaysChapterTest::walk));
    }

    @Test
    @DisplayName("has the document's cards, then its own")
    void cards() {
        try (var session = session(scene.root("overlays"))) {
            var ids = walk(session.byId("screen-overlays").orElseThrow())
                    .map(Element::widget)
                    .filter(widget -> widget instanceof Card card
                            && card.attributes().classes().contains("wall-card"))
                    .map(widget -> ((Card) widget).attributes().id())
                    .toList();

            assertEquals(
                    Set.of(
                            "overlays-dialog",
                            "overlays-hud",
                            "overlays-toasts",
                            "overlays-popover",
                            "kinds-card",
                            "summary-card",
                            "stack-card",
                            "tour-card",
                            "overlays-tooltips"),
                    Set.copyOf(ids));
            assertEquals(9, ids.size(), "a card twice: " + ids);
        }
    }

    /// A stop whose target is not built is skipped, so a renamed id would leave a
    /// tour that silently shows less.
    @Test
    @DisplayName("every stop of the tour names something the screen builds")
    void tourStops() {
        assertEquals("overlays", ShowcaseTour.SCREEN);
        try (var session = session(scene.root(ShowcaseTour.SCREEN))) {
            var missing = ShowcaseTour.stops().stream()
                    .map(Stop::targetId)
                    .filter(id -> session.byId(id).isEmpty())
                    .toList();

            assertEquals(List.of(), missing);
        }
    }

    @Test
    @DisplayName("Take the tour runs what the application handed the screen")
    void takeTheTour() {
        var started = new AtomicInteger();
        try (var session = session(new TourCard(started::incrementAndGet))) {
            session.click("tour-button");

            assertEquals(1, started.get());
        }
    }

    @Test
    @DisplayName("a popover with nowhere to open says so")
    void popoverWithoutPopups() {
        try (var session = session(new PopoverCard())) {
            session.click(PopoverCard.BUTTON);

            assertEquals(
                    "Saved. This desktop has no popup windows to say so.",
                    ((Text) session.byId("popover-last").orElseThrow().widget()).content());
        }
    }

    @Test
    @DisplayName("every demonstration on the tooltip card carries a tooltip")
    void tooltips() {
        try (var session = session(new TooltipsCard())) {
            var tipped = walk(session.byId("overlays-tooltips").orElseThrow())
                    .map(Element::widget)
                    .filter(widget -> widget instanceof Attributed<?> attributed
                            && attributed.attributes().tooltip() != null)
                    .count();

            assertTrue(tipped >= 5, "only " + tipped + " widgets carry a tooltip");
        }
    }
}
