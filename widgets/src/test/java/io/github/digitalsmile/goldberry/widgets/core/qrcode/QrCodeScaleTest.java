package io.github.digitalsmile.goldberry.widgets.core.qrcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.offscreen.Offscreen;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// The promise through the **whole stack**: cascade, Yoga, the render tree, the
/// rasterizer — at 100%, 150% and 200%.
///
/// [QrModulesTest] drives the painter against a size it was handed. This one
/// does not hand it anything: the box comes out of a real layout, the padding
/// below is deliberately odd so the code's corner is nowhere convenient, and the
/// question is whether a module edge still lands on a device pixel after all of
/// that. It is a different claim from the painter's, and it rests on something
/// the painter cannot check — that layout rounds a box to the pixel grid before
/// a painter ever sees it.
///
/// The check is grey. The ink and the paper are the only two neutral colours in
/// the picture — Nord's surfaces are blue and its borders are blue — so any
/// pixel with equal channels that is neither `#1a1a1a` nor `#ffffff` can only
/// have come from mixing the two, which is a module edge between two pixels.
class QrCodeScaleTest {

    private static final int WIDTH = 640;

    private static final int HEIGHT = 640;

    private static final int INK = 0xFF1A1A1A;

    private static final int PAPER = 0xFFFFFFFF;

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    private static Image render(float factor) {
        // 7px of padding and a 1px border on the column, so the code's own
        // corner is at an awkward logical offset -- 8, which is 12 device pixels
        // at 150% and would be 8.5 if anything rounded the wrong way.
        var sheet = Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "column { padding: 7px; border: 1px solid #5e81ac; background: #2e3440 }"
                        + " qr-code { width: 213px; height: 191px }");
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(sheet);
        return Offscreen.of(WIDTH, HEIGHT)
                .scale(factor)
                .stylesheets(sheets)
                .render(new Column(new QrCode("tg://login?token=AQAAAB8AAAAmaW1wb3J0YW50")));
    }

    @ParameterizedTest(name = "at {0}x")
    @ValueSource(floats = {1.0f, 1.5f, 2.0f})
    @DisplayName("no module edge falls between two device pixels")
    void noModuleEdgeIsBlended(float factor) {
        var image = render(factor);

        var ink = 0;
        var paper = 0;
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                var argb = image.argb(x, y);
                if (argb == INK) {
                    ink++;
                    continue;
                }
                if (argb == PAPER) {
                    paper++;
                    continue;
                }
                var red = argb >>> 16 & 0xFF;
                var green = argb >>> 8 & 0xFF;
                var blue = argb & 0xFF;
                assertTrue(
                        red != green || green != blue,
                        "at " + factor + "x, pixel " + x + "," + y + " is the neutral "
                                + Integer.toHexString(argb) + " — a mix of the ink and the paper, which is a"
                                + " module edge between two device pixels");
            }
        }

        assertTrue(ink > 500, "at " + factor + "x hardly any dark modules were drawn: " + ink);
        assertTrue(paper > 500, "at " + factor + "x hardly any light modules were drawn: " + paper);
    }

    @ParameterizedTest(name = "at {0}x")
    @ValueSource(floats = {1.0f, 1.5f, 2.0f})
    @DisplayName("the code is square however oblong its box is")
    void theCodeStaysSquare(float factor) {
        var image = render(factor);

        // The paper is the code's own background, so its bounding box is the
        // code's -- quiet zone included. A box of 213x191 that produced an
        // oblong picture would mean the module size was taken per axis.
        var left = image.width();
        var right = -1;
        var top = image.height();
        var bottom = -1;
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                if (image.argb(x, y) != PAPER && image.argb(x, y) != INK) {
                    continue;
                }
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }

        assertTrue(right > left, "at " + factor + "x nothing was drawn");
        assertEquals(right - left, bottom - top, "at " + factor + "x the code came out oblong");
    }
}
