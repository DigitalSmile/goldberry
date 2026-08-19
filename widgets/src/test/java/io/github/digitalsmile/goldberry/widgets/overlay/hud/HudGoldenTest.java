package io.github.digitalsmile.goldberry.widgets.overlay.hud;

import io.github.digitalsmile.goldberry.FrameStats;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.WindowRoot;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a HUD looks like, and — the image that matters — **where it lands**
/// (§14, [ADR-0050]).
///
/// [HudTest] asserts the strings. The placement cannot be asserted the same way:
/// an overlay's position is Yoga's answer to an absolute box with two of its four
/// insets undefined, and the failure mode of getting that wrong is not a wrong
/// number but a plate stretched across the window or pinned to the wrong edge.
/// Both resolve to perfectly reasonable boxes.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class HudGoldenTest {

    /// A rate a machine cannot influence. Every number here is chosen, which is
    /// the whole reason [FrameStats] is an interface.
    private static final FrameStats SIXTY = FrameStats.of(60, 16.7, 2.1, 4_200, 0, 0, 0, 0, 60);

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes id(String id) {
        return new Attributes(id, Set.of(), id);
    }

    private static WidgetRenderer renderer(Theme theme, FrameStats stats) {
        return new WidgetRenderer(
                        List.of(
                                Controls.baseStylesheet(),
                                theme.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, """
                                        #scene  { padding: 12px; align-items: center;
                                                  background: var(--gb-bg) }
                                        #window { padding: 0; background: var(--gb-bg) }
                                        /* Grows, so the image says the overlay
                                           took no space from it. */
                                        #body   { padding: 16px; gap: 8px; flex-grow: 1;
                                                  flex-direction: column;
                                                  background: var(--gb-surface) }
                                        """)),
                        TestFont.get())
                .frames(stats);
    }

    private void paint(String name, Theme theme, FrameStats stats, int width, Widget hud) {
        paint(name, theme, stats, width, 60, hud);
    }

    /// The same, with a height — a HUD is a column now, so the two-reading
    /// default and the seven-reading breakdown are not the same shape (ADR-0150).
    private void paint(String name, Theme theme, FrameStats stats, int width, int height,
            Widget hud) {
        var tree = new ElementTree(new Row(List.of(hud), id("scene")));
        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer(theme, stats).render(tree)));
    }

    /// The breakdown — [ADR-0146], and the only thing that can say whether six
    /// numbers on one plate read as a diagnostic or as a wall.
    ///
    /// The rate is bright, the total is dim, the four stages are dimmer and one
    /// size down. Three ranks on one line is the kind of thing that resolves to
    /// the same numbers whether it works or not.
    @Test
    @DisplayName("the stage breakdown, a line each, with the caption under it")
    void stages() {
        paint("hud-stages", Theme.NORD_DARK,
                FrameStats.of(60, 16.7, 2.1, 4_200, 0.05, 0.29, 0.11, 1.34, 60),
                300, 190, Hud.stages());
    }

    /// **A frame in trouble** — [ADR-0150], and the only thing that can say
    /// whether three colours on one plate read as a diagnostic or as a mess.
    ///
    /// 22 fps, a 45 ms frame, an 11 ms paint and a style that has run away: the
    /// rate and the frame and the paint and the style are over, the raster is
    /// near, and the two that are fine stay quiet. A reader should find the
    /// problem without reading a number.
    @Test
    @DisplayName("readings over their budget are red, near it amber, and the rest quiet")
    void overBudget() {
        paint("hud-over-budget", Theme.NORD_DARK,
                FrameStats.of(22, 45.0, 11.0, 4_200, 0.04, 9.6, 0.2, 3.4, 60),
                300, 190, Hud.stages());
    }

    /// The plate: a dim rate and a dimmer paint time, on the dark theme.
    @Test
    @DisplayName("a hud on the dark theme")
    void dark() {
        paint("hud-dark", Theme.NORD_DARK, SIXTY, 200, new Hud());
    }

    /// The same plate on the light theme, and it is the **same plate** — the one
    /// background token in the toolkit that does not invert, because a HUD lies
    /// over colours the toolkit does not know.
    @Test
    @DisplayName("a hud on the light theme is still a dark plate")
    void light() {
        paint("hud-light", Theme.NORD_LIGHT, SIXTY, 200, new Hud());
    }

    /// All three readings, which is the widest a HUD gets.
    @Test
    @DisplayName("every reading at once")
    void allReadings() {
        paint("hud-readings", Theme.NORD_DARK, SIXTY, 300,
                new Hud(Reading.FPS, Reading.REFRESH, Reading.PAINT));
    }

    /// Dashes rather than zeroes: a HUD with no frame loop over it has measured
    /// nothing, and the image is here because "— fps" and "0 fps" are one
    /// character apart in a test and unmistakable on screen.
    @Test
    @DisplayName("no frame loop, so no numbers")
    void noLoop() {
        paint("hud-no-loop", Theme.NORD_DARK, FrameStats.none(), 300,
                new Hud(Reading.FPS, Reading.REFRESH, Reading.PAINT));
    }

    /// **The overlay layer itself.** A window with content in it and a HUD
    /// floating in the bottom-end corner, [Overlay#WINDOW_MARGIN] from both
    /// edges — rendered through [WindowRoot], which is exactly what the launcher
    /// builds.
    ///
    /// What this image is evidence of: the content still fills the window (an
    /// overlay took no space from it), and the plate is at one corner rather than
    /// stretched across two — which is what an inset of zero on all four edges
    /// would have produced, and which is a legal box.
    @Test
    @DisplayName("a hud pinned to the bottom-end corner of a window")
    void pinnedToACorner() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var root = new WindowRoot(
                new Column(List.of(new Panel(List.of(
                        new Text("The application's own content."),
                        new Text("It fills the window; the HUD lies on top of it.")),
                        id("body"))), id("window")),
                overlays);
        overlays.set(List.of(overlay(new Hud(), Corner.BOTTOM_END)));

        var tree = new ElementTree(root);
        GoldenImage.assertMatches("overlay-corner", 420, 160, 1.0f,
                frame -> BoxPainter.paint(frame,
                        renderer(Theme.NORD_DARK, SIXTY).render(tree)));
    }

    /// The same window with the HUD in the opposite corner, which is the cheapest
    /// possible check that [Corner] is not a decoration.
    @Test
    @DisplayName("and in the top-start one")
    void pinnedToTheOtherCorner() {
        var overlays = Property.<List<Overlay>>of(List.of());
        var root = new WindowRoot(
                new Column(List.of(new Panel(List.of(
                        new Text("The application's own content.")),
                        id("body"))), id("window")),
                overlays);
        overlays.set(List.of(overlay(new Hud(), Corner.TOP_START)));

        var tree = new ElementTree(root);
        GoldenImage.assertMatches("overlay-corner-top-start", 420, 160, 1.0f,
                frame -> BoxPainter.paint(frame,
                        renderer(Theme.NORD_DARK, SIXTY).render(tree)));
    }

    /// What `Host.overlay` builds, without a launcher to build it.
    private static Overlay overlay(Widget widget, Corner corner) {
        return Overlay.of(widget, corner);
    }
}
