package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.badge.Badge;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// What a timeline looks like (§14, [ADR-0050]): a vertical one with a body
/// and a pending marker, an alternating one, and a horizontal one under both —
/// and a release log whose markers are badges ([ADR-0356]).
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class TimelineGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private void paint(String name, Theme theme, Widget content) {
        paint(name, theme, 560, 360, content);
    }

    private void paint(String name, Theme theme, int width, int height, Widget content) {
        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #page { gap: 24px; padding: 16px; background: var(--gb-bg) }
                                #pair { gap: 32px; align-items: flex-start }
                                #pair timeline { width: 240px }
                                """)),
                TestFont.get());

        GoldenImage.assertMatches(
                name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(new ElementTree(content))));
    }

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    private static Widget page() {
        var vertical = new Timeline(
                        new Entry("Pushed", new Text("Three commits to main.")).at("09:12"),
                        new Entry("Built").at("09:15").colour(0xFFA3BE8C),
                        new Entry("Deployed", new Text("To staging.")).at("09:20"))
                .pending(true);
        var alternate = new Timeline(
                        new Entry("Draft").at("Mon"), new Entry("Review").at("Tue"), new Entry("Ship").at("Fri"))
                .align(Timeline.Align.ALTERNATE);
        var horizontal = new Timeline(
                        new Entry("Order").at("1 Sep"),
                        new Entry("Paid").at("2 Sep").colour(0xFFA3BE8C),
                        new Entry("Shipped").at("4 Sep"),
                        new Entry("Delivered"))
                .direction(Timeline.Direction.HORIZONTAL)
                .pending(true);
        return new Column(List.of(new Row(List.of(vertical, alternate), id("pair")), horizontal), id("page"));
    }

    /// A one-digit badge is exactly the rail's width; `v2` overhangs it into the
    /// body's padding, and the axis stays where the dots put it.
    private static Widget releases() {
        return new Column(
                List.of(new Timeline(
                        new Entry("Released", new Text("Tagged and published."))
                                .at("Fri")
                                .withMarker(new Badge("v2", null, new Attributes(null, Set.of("success"), null))),
                        new Entry("Reviewed").at("Thu").withMarker(new Badge("3")),
                        new Entry("Drafted").at("Mon"))),
                id("page"));
    }

    @Test
    @DisplayName("badges on the axis, with a dot after them")
    void badgeMarkers() {
        paint("timeline-badges-dark", Theme.NORD_DARK, 280, 200, releases());
    }

    @Test
    @DisplayName("three timelines on dark")
    void dark() {
        paint("timeline-dark", Theme.NORD_DARK, page());
    }

    @Test
    @DisplayName("and the same on light")
    void light() {
        paint("timeline-light", Theme.NORD_LIGHT, page());
    }
}
