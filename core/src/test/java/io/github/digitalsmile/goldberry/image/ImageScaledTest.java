package io.github.digitalsmile.goldberry.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// `Image.scaled(...)` — ADR-0428.
///
/// A resampled **copy**, which is a different operation from a scaled blit: the
/// blit knows the destination size because it is drawing onto something, and
/// this one is told. What the tests below pin is that it is still a *value* —
/// the source is unchanged, nothing has to be closed, and the answer can be
/// encoded and written out.
class ImageScaledTest {

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A `size` × `size` red-and-blue checkerboard, one pixel per square.
    private static Image checkerboard(int size) {
        var argb = new int[size * size];
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                argb[y * size + x] = (x + y) % 2 == 0 ? RED : BLUE;
            }
        }
        return Image.ofArgb(size, size, argb);
    }

    @Nested
    @DisplayName("the size")
    class Size {

        @Test
        @DisplayName("the copy is the size that was asked for, and the original is untouched")
        void resizes() {
            var source = checkerboard(8);
            var scaled = source.scaled(32, 16);

            assertEquals(new PhysicalSize(32, 16), scaled.size());
            assertEquals(32, scaled.width());
            assertEquals(16, scaled.height());
            assertEquals(new PhysicalSize(8, 8), source.size(), "an image is a value and this one did not move");
        }

        @Test
        @DisplayName("asking for the size it already is gives back the same image")
        void identityIsFree() {
            // Not merely an equal image: the same one. An image is a value, so
            // there is nothing a copy could be used for that this cannot, and a
            // caller normalising a batch to one size should not pay a buffer for
            // the ones that already are.
            var source = checkerboard(8);

            assertSame(source, source.scaled(8, 8));
            assertSame(source, source.scaled(8, 8, Resampling.NEAREST));
        }

        @Test
        @DisplayName("a size that is not positive is refused")
        void refusesEmpty() {
            var source = checkerboard(4);

            assertThrows(IllegalArgumentException.class, () -> source.scaled(0, 4));
            assertThrows(IllegalArgumentException.class, () -> source.scaled(4, -1));
            assertThrows(NullPointerException.class, () -> source.scaled(4, 4, null));
        }
    }

    @Nested
    @DisplayName("the filter, which is a choice and not a quality knob")
    class Filter {

        @Test
        @DisplayName("nearest doubles each pixel into a block and invents no colours")
        void nearestInventsNothing() {
            // The property that makes NEAREST worth exposing at all: an upscaled
            // icon keeps the pixels it had. Every output pixel here is one of
            // the two input colours, which no other filter can promise.
            var scaled = checkerboard(4).scaled(8, 8, Resampling.NEAREST);

            for (var y = 0; y < 8; y++) {
                for (var x = 0; x < 8; x++) {
                    var expected = ((x / 2) + (y / 2)) % 2 == 0 ? RED : BLUE;
                    assertEquals(expected, scaled.argb(x, y), "at (" + x + ", " + y + ")");
                }
            }
        }

        @Test
        @DisplayName("the default is not nearest: it mixes")
        void defaultIsSmooth() {
            // If `scaled(w, h)` quietly ignored its filter, or defaulted to
            // NEAREST, this would be identical to the test above.
            var smooth = checkerboard(4).scaled(8, 8);
            var nearest = checkerboard(4).scaled(8, 8, Resampling.NEAREST);

            var differs = false;
            for (var y = 0; y < 8 && !differs; y++) {
                for (var x = 0; x < 8; x++) {
                    if (smooth.argb(x, y) != nearest.argb(x, y)) {
                        differs = true;
                        break;
                    }
                }
            }
            assertTrue(differs, "the default filter should not be nearest-neighbour");
        }

        @Test
        @DisplayName("every filter is reachable and produces an image of the right size")
        void everyFilter() {
            // The switch in `Image` maps four `:core` names onto four `:natives`
            // ones. A pair swapped there is silent — it resamples with the wrong
            // mathematics and the picture still looks like a picture — so what
            // is checked is that each one reaches the library and comes back.
            for (var filter : Resampling.values()) {
                var scaled = checkerboard(8).scaled(4, 4, filter);

                assertEquals(new PhysicalSize(4, 4), scaled.size(), filter.toString());
            }
        }
    }

    @Nested
    @DisplayName("what comes back is an ordinary image")
    class StillAValue {

        @Test
        @DisplayName("a downscale of a flat colour is that colour")
        void flatColourSurvives() {
            // The one resampling result that is exact whatever the filter: every
            // source pixel is the same, so every weighted average of them is it
            // too. A premultiplication applied twice, or an argument swapped for
            // a stride, shows up here as a colour that is not the one put in.
            var argb = new int[16 * 16];
            Arrays.fill(argb, RED);
            var flat = Image.ofArgb(16, 16, argb);

            var scaled = flat.scaled(4, 4);

            for (var y = 0; y < 4; y++) {
                for (var x = 0; x < 4; x++) {
                    assertEquals(RED, scaled.argb(x, y), "at (" + x + ", " + y + ")");
                }
            }
        }

        @Test
        @DisplayName("translucency survives the round trip")
        void keepsAlpha() {
            // Resampling happens in premultiplied space, which is the correct
            // space for it: averaging straight alpha weights a transparent
            // pixel's colour as if it were there. A flat 50% red stays a flat
            // 50% red, give or take the level premultiplied storage costs.
            var argb = new int[16 * 16];
            Arrays.fill(argb, 0x80FF0000);
            var translucent = Image.ofArgb(16, 16, argb);

            var scaled = translucent.scaled(4, 4);

            assertEquals(0x80, scaled.argb(2, 2) >>> 24, "the alpha came through");
            assertTrue((scaled.argb(2, 2) >> 16 & 0xFF) > 0xF0, "and so did the red");
        }

        @Test
        @DisplayName("the copy can be encoded, which is the thing a thumbnail is for")
        void encodes() {
            var thumbnail = checkerboard(64).scaled(16, 16);
            var png = thumbnail.encodePng();

            assertTrue(png.length > 0);
            var round = Image.decode(png);
            assertEquals(new PhysicalSize(16, 16), round.size());
        }

        @Test
        @DisplayName("scaling a scaled image again is ordinary")
        void chains() {
            // Nothing is held open, so there is no lifetime to get wrong: the
            // whole point of ADR-0283's rule surviving into this operation.
            var twice = checkerboard(64).scaled(32, 32).scaled(8, 8);

            assertEquals(new PhysicalSize(8, 8), twice.size());
            assertNotEquals(0, twice.argb(0, 0) >>> 24, "and it still has pixels in it");
        }
    }
}
