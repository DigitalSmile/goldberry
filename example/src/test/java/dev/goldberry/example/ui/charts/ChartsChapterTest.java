package dev.goldberry.example.ui.charts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.docs.CardShape;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.image.Image;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.data.Series;
import dev.goldberry.widgets.data.linechart.LineChart;
import dev.goldberry.widgets.panel.card.Card;

/// The Charts screen: a card for every section of the chapter, each linking the
/// section it shows, and a crosshair two of its charts share.
class ChartsChapterTest {

    /// Every card on the screen, and the section of the guide it opens.
    private static final Map<String, DocLink> CARDS = new LinkedHashMap<>();

    static {
        CARDS.put("marched-card", DocLink.to("components/charts", "what-the-five-share"));
        CARDS.put("line-chart-card", DocLink.to("components/charts", "line-chart"));
        CARDS.put("watch-card", DocLink.to("components/charts", "line-chart"));
        CARDS.put("dropouts-card", DocLink.to("components/charts", "line-chart"));
        CARDS.put("sightings-card", DocLink.to("components/charts", "bar-chart"));
        CARDS.put("provisions-card", DocLink.to("components/charts", "area-chart"));
        CARDS.put("packs-card", DocLink.to("components/charts", "donut-chart"));
        CARDS.put("safe-card", DocLink.to("components/charts", "sparkline"));
        CARDS.put("charts-not-here", DocLink.to("components/charts", "what-is-not-a-chart-here"));
    }

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the screen holds a card for each section, linking that section")
    void everyCardLinksItsSection() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 900)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(scene.root("charts"))) {
            assertTrue(session.byId("screen-charts").isPresent(), "the screen's root is #screen-charts");
            CARDS.forEach((id, link) -> {
                var card = session.byId(id).orElseThrow(() -> new AssertionError("no card #" + id));
                assertEquals(Optional.of(link), CardShape.link((Card) card.widget()), id);
                assertEquals(List.of(), CardShape.problems((Card) card.widget()), id);
            });
        }
    }

    @Test
    @DisplayName("the line chart in markup has its two series")
    void theMarkupChartHasItsSeries() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 900)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(scene.root("charts"))) {
            var road = widgetsUnder(session.byId("line-chart-card").orElseThrow())
                    .filter(LineChart.class::isInstance)
                    .map(LineChart.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the card holds no line chart"));
            assertEquals("road", road.attributes().id());
            var series = road.series();
            assertEquals(
                    List.of("On the road", "Off it"),
                    series.stream().map(Series::name).toList());
            assertEquals(7, series.getFirst().values().size(), "a point node per day");
        }
    }

    @Test
    @DisplayName("pointing at a day on the line puts the crosshair on the bars too")
    void theCrosshairIsShared() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.addAll(ShowcaseStyles.sheets());
        try (var session = Offscreen.of(480, 480).stylesheets(sheets).session(new SharedCrosshair())) {
            var before = session.frame();
            var bars = rect(session, "rests");

            session.hover("marched");
            var after = session.frame();

            assertFalse(
                    samePixels(before, after, bars),
                    "the pointer is over the line, and the bars below it did not change");
        }
    }

    private static Stream<Object> widgetsUnder(Element element) {
        return Stream.concat(
                Stream.of(element.widget()), element.children().stream().flatMap(ChartsChapterTest::widgetsUnder));
    }

    /// The outermost drawn box of the node with `id`, or of what is under it.
    private static LogicalRect rect(Session session, String id) {
        var target = session.byId(id).orElseThrow();
        for (var region : session.regions()) {
            if (region.owner() instanceof Element owner && within(owner, target)) {
                return region.painted();
            }
        }
        throw new AssertionError("#" + id + " was not drawn");
    }

    private static boolean within(Element node, Element ancestor) {
        for (var at = node; at != null; at = at.parent() instanceof Element parent ? parent : null) {
            if (at == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePixels(Image a, Image b, LogicalRect rect) {
        for (var y = (int) rect.top(); y < (int) (rect.top() + rect.height()); y++) {
            for (var x = (int) rect.left(); x < (int) (rect.left() + rect.width()); x++) {
                if (a.argb(x, y) != b.argb(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
