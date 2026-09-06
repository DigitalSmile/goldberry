package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The colour model, with no widget, no font and no frame.
///
/// The two things worth asserting here are the ones a picture cannot show: that a
/// colour survives the round trip through HSV **without drift**, which is §4's
/// word, and that a hue is kept when the colour it came from has none — which is
/// the whole reason this model exists beside the hex the field holds.
class HsvColorTest {

    @Nested
    @DisplayName("the round trip")
    class RoundTrip {

        /// §4: "round-trips to hex without drift". Every one of the 24-bit
        /// corners and a spread of ordinary colours, because a conversion that
        /// drifts usually drifts at the extremes.
        @Test
        @DisplayName("every channel survives sRGB to HSV and back")
        void exact() {
            for (var argb : new int[] {
                0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
                0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF, 0xFF88C0D0, 0xFFBF616A,
                0xFFA3BE8C, 0xFF2E3440, 0xFF010203, 0xFFFEFDFC
            }) {
                assertEquals(argb, HsvColor.ofArgb(argb).toArgb(), () -> HsvColor.hex(argb) + " drifted");
            }
        }

        @Test
        @DisplayName("and so does the alpha")
        void alpha() {
            for (var a : new int[] {0x00, 0x01, 0x7F, 0x80, 0xFE, 0xFF}) {
                var argb = a << 24 | 0x88C0D0;
                assertEquals(argb, HsvColor.ofArgb(argb).toArgb());
            }
        }

        /// A sweep rather than a sample: 4096 colours across the cube, because a
        /// conversion that is wrong for one sector of the wheel is right for the
        /// other five and a handful of literals would miss it.
        @Test
        @DisplayName("across the whole cube, at every sixteenth step")
        void sweep() {
            for (var r = 0; r < 256; r += 16) {
                for (var g = 0; g < 256; g += 16) {
                    for (var b = 0; b < 256; b += 16) {
                        var argb = 0xFF000000 | r << 16 | g << 8 | b;
                        assertEquals(argb, HsvColor.ofArgb(argb).toArgb(), () -> HsvColor.hex(argb) + " drifted");
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("the hue a colour does not carry")
    class PowerlessHue {

        /// The reason the picker keeps an `HsvColor` rather than deriving one:
        /// dragging to the left edge is dragging to a grey, and a grey has no
        /// hue to put back in the slider.
        @Test
        @DisplayName("a grey read on its own has no hue")
        void greyHasNone() {
            assertEquals(0, HsvColor.ofArgb(0xFF808080).hue());
        }

        /// CSS Color 4's powerless-hue rule, which `Oklch` already applies for the
        /// same reason.
        @Test
        @DisplayName("but withArgb keeps the one being dragged")
        void keptOnDrag() {
            var blue = new HsvColor(210, 0.6, 0.8, 1);

            assertEquals(210, blue.withArgb(0xFF808080).hue());
            assertEquals(0, blue.withArgb(0xFF000000).saturation());
            assertEquals(210, blue.withArgb(0xFF000000).hue());
        }

        @Test
        @DisplayName("and takes the new one when there is a new one")
        void replacedWhenReal() {
            var blue = new HsvColor(210, 0.6, 0.8, 1);

            assertEquals(0, blue.withArgb(0xFFFF0000).hue());
        }
    }

    @Nested
    @DisplayName("hex")
    class Hex {

        /// Eight digits only when they say something: `#88c0d0ff` in a stylesheet
        /// is a reader wondering what the `ff` is for.
        @Test
        @DisplayName("is six digits when opaque and eight when it is not")
        void digits() {
            assertEquals("#88c0d0", HsvColor.hex(0xFF88C0D0));
            assertEquals("#88c0d080", HsvColor.hex(0x8088C0D0));
        }

        /// `CssColor.parse`'s answer, which is every spelling §8's subset accepts
        /// — not a syntax this widget invented.
        @Test
        @DisplayName("reads back every spelling the engine accepts, with or without the hash")
        void parses() {
            assertEquals(0xFF88C0D0, HsvColor.parse("#88c0d0"));
            assertEquals(0xFF88C0D0, HsvColor.parse("88c0d0"));
            assertEquals(0xFF88C0D0, HsvColor.parse("  #88C0D0  "));
            assertEquals(0xFFFF0000, HsvColor.parse("red"));
        }

        /// Including CSS's **four-digit** form, which is `#rgba` — found by
        /// asserting that `#88c0` was rubbish and being told it is a colour with
        /// no alpha in it. The engine's spellings are the engine's, and a picker
        /// that second-guessed them would refuse text a stylesheet accepts.
        @Test
        @DisplayName("including the four-digit form, which is #rgba and not a typo")
        void fourDigits() {
            assertEquals(0x008888CC, HsvColor.parse("#88c0"));
        }

        @Test
        @DisplayName("and answers null for anything else")
        void refuses() {
            assertNull(HsvColor.parse(""));
            assertNull(HsvColor.parse("#12345"));
            assertNull(HsvColor.parse("not a colour"));
        }
    }

    @Nested
    @DisplayName("the components")
    class Components {

        @Test
        @DisplayName("hue wraps and the rest clamp")
        void bounds() {
            assertEquals(10, new HsvColor(370, 0, 0, 1).hue());
            assertEquals(350, new HsvColor(-10, 0, 0, 1).hue());
            assertEquals(1, new HsvColor(0, 4, 0, 1).saturation());
            assertEquals(0, new HsvColor(0, -1, 0, 1).value());
        }

        @Test
        @DisplayName("the pure hue is the plane's top-right corner")
        void hueArgb() {
            assertEquals(0xFFFF0000, new HsvColor(0, 0.2, 0.3, 0.4).hueArgb());
            assertEquals(0xFF00FF00, new HsvColor(120, 0, 0, 1).hueArgb());
        }

        /// `alpha=#false` refuses translucent values, and this is what makes one
        /// opaque without touching anything else.
        @Test
        @DisplayName("opaque changes the alpha and nothing else")
        void opaque() {
            var translucent = new HsvColor(210, 0.6, 0.8, 0.5);

            assertEquals(new HsvColor(210, 0.6, 0.8, 1), translucent.opaque());
        }
    }
}
