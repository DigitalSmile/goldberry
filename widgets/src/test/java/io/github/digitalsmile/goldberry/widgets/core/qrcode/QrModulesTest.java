package io.github.digitalsmile.goldberry.widgets.core.qrcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.qr.Level;
import io.github.digitalsmile.goldberry.qr.QrEncoder;
import io.github.digitalsmile.goldberry.qr.QrMatrix;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// "Modules are whole device pixels at every scale" — the promise, at 100%, 150%
/// and 200%.
///
/// It is the whole of whether a code scans, and it is not a promise a picture
/// can be inspected for: a module edge half a pixel out looks like a slightly
/// soft code and reads as one a phone gives up on. What a test can check is the
/// thing that causes it — that **no painted pixel is between the two colours** —
/// and that is what these do, pixel by pixel over the whole frame.
class QrModulesTest {

    private static final int INK = 0xFF1A1A1A;

    private static final int PAPER = 0xFFFFFFFF;

    /// The background the frame is cleared to, so that "nothing was painted
    /// here" is a colour neither the ink nor the paper can be mistaken for.
    private static final int NOTHING = 0xFF00FF00;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// Paints `matrix` into a frame of `pixels`×`pixels` **device** pixels at
    /// `factor`, and hands back the target to read.
    private static TestFrames.Target painted(QrMatrix matrix, int quietZone, int pixels, float factor) {
        var target = TestFrames.of(pixels, pixels, factor);
        try {
            target.frame().fill(NOTHING);
            var logical = new LogicalSize(pixels / factor, pixels / factor);
            QrModules.paint(target.frame(), logical, matrix, quietZone, INK, PAPER);
        } finally {
            target.end();
        }
        return target;
    }

    @Test
    @DisplayName("every painted pixel is the ink or the paper at 100%, 150% and 200%")
    void everyPixelIsOneOfTwoColours() {
        var matrix = QrEncoder.encode("tg://login?token=AQAAAB8AAAAmaW1wb3J0YW50", Level.M);

        for (var factor : new float[] {1.0f, 1.5f, 2.0f}) {
            var pixels = Math.round(200 * factor);
            var target = painted(matrix, 4, pixels, factor);

            for (var y = 0; y < pixels; y++) {
                for (var x = 0; x < pixels; x++) {
                    var argb = target.pixel(x, y);
                    assertTrue(
                            argb == INK || argb == PAPER || argb == NOTHING,
                            "at " + factor + "x, pixel " + x + "," + y + " is " + Integer.toHexString(argb)
                                    + " — a blend, which is a module edge between two device pixels");
                }
            }
        }
    }

    @Test
    @DisplayName("a module is a whole number of device pixels at every scale")
    void aModuleIsWholePixels() {
        var matrix = QrEncoder.encode("modules", Level.M);
        var count = matrix.size() + 8;

        for (var factor : new float[] {1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f}) {
            // A deliberately awkward box: 137 logical pixels is a fraction of a
            // device pixel at four of these six scales.
            var size = new LogicalSize(137, 137);
            var placement = QrModules.place(count, size, factor);
            assertNotNull(placement, "at " + factor + "x");

            var available = (int) Math.floor(137.0 * factor);
            // 137 logical pixels over 37 modules is three whole logical pixels a
            // module, and the device size is that times the scale.
            var logicalModule = 137 / count;
            assertEquals((int) Math.floor(logicalModule * (double) factor), placement.module(), "at " + factor + "x");
            assertEquals(placement.module() * count, placement.extent(), "at " + factor + "x");
            assertTrue(placement.extent() <= available, "at " + factor + "x the code overflows its box");
        }
    }

    @Test
    @DisplayName("a bigger scale gets more pixels a module, not a blurrier one")
    void scalingUpBuysResolution() {
        var count = QrEncoder.encode("resolution", Level.M).size() + 8;
        var size = new LogicalSize(160, 160);

        var one = QrModules.place(count, size, 1.0f);
        var two = QrModules.place(count, size, 2.0f);
        assertNotNull(one);
        assertNotNull(two);

        // Exactly twice, which is the whole of why the module is quantized in
        // logical pixels before device ones: the code is the same size on the
        // box at both scales, drawn with twice the pixels.
        assertEquals(one.module() * 2, two.module());
        assertEquals(one.extent() * 2, two.extent());
    }

    @Test
    @DisplayName("the code is centred, with the slack split between the two sides")
    void theCodeIsCentred() {
        var count = QrEncoder.encode("centred", Level.M).size() + 8;

        var placement = QrModules.place(count, new LogicalSize(200, 200), 1.0f);
        assertNotNull(placement);

        var slack = 200 - placement.extent();
        assertEquals(slack / 2, placement.left());
        assertEquals(slack / 2, placement.top());
    }

    @Test
    @DisplayName("a box too small for one logical pixel a module draws nothing at all")
    void tooSmallDrawsNothing() {
        var matrix = QrEncoder.encode("too small", Level.M);
        var count = matrix.size() + 8;

        assertNull(QrModules.place(count, new LogicalSize(count - 1, count - 1), 1.0f));
        // And still nothing at 200%, where there would be room for one device
        // pixel a module: a widget that appeared when the window moved to
        // another display would be worse than one that is absent on both.
        assertNull(QrModules.place(count, new LogicalSize(count - 1, count - 1), 2.0f));

        var target = painted(matrix, 4, 16, 1.0f);
        for (var y = 0; y < 16; y++) {
            for (var x = 0; x < 16; x++) {
                assertEquals(
                        NOTHING,
                        target.pixel(x, y),
                        "a 21-module code in sixteen pixels is a grey square, not a small QR code");
            }
        }
    }

    @Test
    @DisplayName("the quiet zone is paper all the way round the code")
    void theQuietZoneIsPaper() {
        var matrix = QrEncoder.encode("quiet", Level.M);
        var quietZone = 4;
        var count = matrix.size() + 2 * quietZone;
        var placement = QrModules.place(count, new LogicalSize(count * 4, count * 4), 1.0f);
        assertNotNull(placement);
        var target = painted(matrix, quietZone, count * 4, 1.0f);

        var edge = quietZone * placement.module();
        for (var i = 0; i < placement.extent(); i++) {
            var x = placement.left() + i;
            var y = placement.top() + i;
            assertEquals(PAPER, target.pixel(x, placement.top() + edge - 1), "the row above the code");
            assertEquals(PAPER, target.pixel(placement.left() + edge - 1, y), "the column left of the code");
            assertEquals(PAPER, target.pixel(x, placement.top() + placement.extent() - edge), "the row below it");
            assertEquals(PAPER, target.pixel(placement.left() + placement.extent() - edge, y), "the column right");
        }
    }

    @Test
    @DisplayName("the top-left finder is drawn where the matrix says it is")
    void theFinderLandsWhereItShould() {
        var matrix = QrEncoder.encode("finder", Level.M);
        var quietZone = 4;
        var count = matrix.size() + 2 * quietZone;
        var pixels = count * 4;
        var placement = QrModules.place(count, new LogicalSize(pixels, pixels), 1.0f);
        assertNotNull(placement);
        var target = painted(matrix, quietZone, pixels, 1.0f);

        // The middle of every module of the finder and its light ring, read
        // back out of the pixels.
        for (var row = 0; row < 8; row++) {
            for (var column = 0; column < 8; column++) {
                var x = placement.left() + (column + quietZone) * placement.module() + placement.module() / 2;
                var y = placement.top() + (row + quietZone) * placement.module() + placement.module() / 2;
                assertEquals(
                        matrix.isDark(column, row) ? INK : PAPER, target.pixel(x, y), "module " + column + "," + row);
            }
        }
    }

    @Test
    @DisplayName("no quiet zone means the code fills the box")
    void noQuietZoneFillsTheBox() {
        var matrix = QrEncoder.encode("edge to edge", Level.M);

        var without = QrModules.place(matrix.size(), new LogicalSize(100, 100), 1.0f);
        var with = QrModules.place(matrix.size() + 8, new LogicalSize(100, 100), 1.0f);
        assertNotNull(without);
        assertNotNull(with);

        assertTrue(without.module() > with.module(), "eight fewer modules across should buy a bigger module");
    }
}
