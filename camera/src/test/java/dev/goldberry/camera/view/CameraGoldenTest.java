package dev.goldberry.camera.view;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.camera.synthetic.SyntheticCamera;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.media.MediaCapabilities;
import dev.goldberry.media.view.MediaStyles;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.image.Fit;

/// `camera-view` as `camera.css` draws it: a synthetic camera's test card,
/// mirrored and not, and a view still asking, on both themes.
///
/// The card is converted on the CPU, as a window with no GPU draws it, which
/// takes the media natives' libyuv: without them these skip.
@DisplayName("camera-view, drawn")
class CameraGoldenTest {

    @BeforeEach
    void natives() {
        RendererRequirement.enforce();
        assumeTrue(MediaCapabilities.nativesAvailable(), "the media natives convert the pictures");
    }

    /// Half a second of the card: the square a quarter of the way across.
    private static SyntheticCamera card() {
        var camera = SyntheticCamera.of(320, 240);
        camera.advance(Duration.ofMillis(500));
        return camera;
    }

    private static Widget scene() {
        var camera = card();
        return new Row(
                List.of(
                        new CameraView(camera, Fit.COVER, Mirror.OFF, Attributes.NONE),
                        new CameraView(camera, Fit.CONTAIN, Mirror.ON, Attributes.NONE),
                        new CameraView(SyntheticCamera.asking(320, 240))),
                Attributes.NONE.id("scene"));
    }

    private static void assertGolden(String name, Theme theme) {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(theme, Density.REGULAR));
        sheets.add(MediaStyles.stylesheet());
        sheets.add(CameraStyles.stylesheet());
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "#scene { padding: 12px; gap: 12px; background: var(--gb-surface); }"
                        + " camera-view { width: 160px; height: 120px; }"));
        var widget = scene();
        GoldenImage.assertMatches(
                name,
                528,
                144,
                1f,
                (size, scale) ->
                        Offscreen.of(size).scale(scale).stylesheets(sheets).render(widget));
    }

    @Test
    @DisplayName("on dark: the card as sent, mirrored and letterboxed, and a view asking")
    void dark() {
        assertGolden("camera-view-dark", Theme.NORD_DARK);
    }

    @Test
    @DisplayName("on light")
    void light() {
        assertGolden("camera-view-light", Theme.NORD_LIGHT);
    }
}
