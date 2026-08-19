package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.example.ui.Scrolling;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.overlay.tour.Stop;
import io.github.digitalsmile.goldberry.widgets.overlay.tour.Tour;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A tour over the scrolling screen, as a picture.
///
/// The only way to see what a tour is: the veil is a fact about *pixels* — which
/// region is dimmed and which is not — and no assertion on the widget tree says
/// whether the thing being described is the lit one.
///
/// It earned its place immediately. The first version put the card down the whole
/// left edge of the window and one pixel from the top, because `Insets` is in CSS
/// order — top, right, bottom, left — and the call passed left and top. Anchoring
/// a box by its top *and its bottom* stretches it, and every test that asked the
/// tree what it contained passed while this was true
/// ([ADR-0121](../../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
class TourGoldenTest {

    @Test
    @DisplayName("a stop lights its target and dims the rest")
    void stop() {
        shoot("tour-stop", "jump-bar", "Jump to a section",
                "These ask the list to bring a section into view.");
    }

    /// A target at the far right, which is the case the placement has to clamp:
    /// a card centred on it would hang off the window.
    @Test
    @DisplayName("a card centred on a target near the edge stays on screen")
    void nearTheEdge() {
        shoot("tour-edge", "tour-button", "Start it again",
                "A small target at the right-hand edge.");
    }

    private void shoot(String golden, String targetId, String title, String body) {
        RendererRequirement.enforce();
        var sheets = new ArrayList<Stylesheet>(
                Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));

        try (var fonts = Fonts.bundled()) {
            var renderer = new WidgetRenderer(sheets, fonts);
            var screen = new Scrolling(() -> { });

            List<HitTest.Region> regions;
            var probeTarget = TestFrames.of(900, 560, 1.0f, 0);
            try (var probe = RenderTree.create()) {
                var probeTree = new ElementTree(screen);
                for (var i = 0; i < 4; i++) {
                    probeTree.flush();
                    probe.update(probeTarget.frame(), renderer.render(probeTree));
                }
                regions = HitTest.capture(probe);
            } finally {
                probeTarget.end();
            }

            var host = new TourTestHost(regions);
            var tour = new Tour(List.of(
                    new Stop(targetId, title, body),
                    new Stop("scroll-demo", "A viewport of its own",
                            "Scroll it with the wheel, or use PageDown.")),
                    host, () -> { });
            // Through WindowRoot, because that is where a filling overlay gets
            // its insets -- a tour laid out in flow has no size at all.
            var tree = new ElementTree(new io.github.digitalsmile.goldberry.widget.WindowRoot(
                    screen,
                    io.github.digitalsmile.goldberry.bind.Property.of(
                            List.of(io.github.digitalsmile.goldberry.Overlay.filling(tour)))));

            var router = new io.github.digitalsmile.goldberry.input.PointerRouter();
            router.windowBounds(LogicalRect.of(0, 0, 900, 560));
            var warm = TestFrames.of(900, 560, 1.0f, 0);
            try (var render = RenderTree.create()) {
                for (var i = 0; i < 5; i++) {
                    tree.flush();
                    render.update(warm.frame(), renderer.render(tree));
                    router.updateRegions(HitTest.capture(render));
                }
            } finally {
                warm.end();
            }

            GoldenImage.assertMatches(golden, 900, 560, 1.0f, frame -> {
                try (var render = RenderTree.create()) {
                    tree.flush();
                    render.update(frame, renderer.render(tree));
                    render.paint(frame);
                }
            });
        }
    }
}
