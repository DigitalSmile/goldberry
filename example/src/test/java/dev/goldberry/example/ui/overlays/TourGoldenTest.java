package dev.goldberry.example.ui.overlays;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widgets.overlay.tour.Stop;
import dev.goldberry.widgets.overlay.tour.Tours;

/// The showcase's tour over the Overlays screen, as a picture.
///
/// The only way to see what a tour is: the veil is a fact about pixels, which
/// region is dimmed and which is not, and no assertion on the widget tree says
/// whether the thing being described is the lit one.
///
/// Through an `Offscreen` session, whose host answers `anchor` from the frame it
/// last painted, as a window's does: a stop finds its target where the settled
/// screen drew it.
class TourGoldenTest {

    private static final int WIDTH = 900;
    private static final int HEIGHT = 560;

    /// Past the tour's arrival: the card fades in and the cut-out travels.
    private static final Duration ARRIVED = Duration.ofMillis(400);

    /// The first of the showcase's own stops.
    @Test
    @DisplayName("a stop lights its target and dims the rest")
    void stop() {
        shoot("tour-stop", ShowcaseTour.stops());
    }

    /// A target at the far right, the case the placement has to clamp: a card
    /// centred on it would hang off the window. The bar's Switch button is the
    /// smallest thing at that edge.
    @Test
    @DisplayName("a card centred on a target near the edge stays on screen")
    void nearTheEdge() {
        shoot(
                "tour-edge",
                List.of(
                        new Stop("theme", "Switch the light", "A small target at the right-hand edge."),
                        ShowcaseTour.stops().getFirst()));
    }

    private void shoot(String golden, List<Stop> stops) {
        RendererRequirement.enforce();
        try (var scene = new ShowcaseScene();
                var fonts = Fonts.bundled()) {
            var sheets = scene.stylesheets(Theme.NORD_DARK);
            // One scale: a tour's card is placed against a rectangle the frame
            // reported, and the sweep's other scales would each settle their own.
            GoldenImage.assertMatchesAtOneScale(golden, WIDTH, HEIGHT, 1.0f, (size, scale) -> {
                try (var session = Offscreen.of(size)
                        .scale(scale)
                        .stylesheets(sheets)
                        .fonts(fonts)
                        .session(scene.root(ShowcaseTour.SCREEN))) {
                    // A frame first, so the targets have been painted to be found.
                    session.frame();
                    assertNotNull(Tours.start(session.host(), stops), "the tour did not start");
                    // The card is placed a frame after the cut-out, from the size it
                    // came out at, and fades in after that.
                    session.frame();
                    session.advance(ARRIVED);
                    session.frame();
                    session.advance(ARRIVED);
                    return session.frame();
                }
            });
        }
    }
}
