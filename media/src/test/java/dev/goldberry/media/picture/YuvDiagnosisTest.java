package dev.goldberry.media.picture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.YuvReference.Chroma;
import dev.goldberry.media.picture.YuvReference.Reading;

/// The diagnosis on a test card drawn under each misreading: it has to name the
/// one that drew it. No native library is involved.
@DisplayName("YuvDiagnosis")
class YuvDiagnosisTest {

    /// A 32×16 BT.601 limited-range I420 card: luma rising across, and chroma in
    /// saturated blocks with hard edges between them, which is what tells the
    /// readings apart.
    private record Card(VideoFrame.ColorMatrix matrix, boolean fullRange) implements YuvSource {

        @Override
        public PixelFormat format() {
            return PixelFormat.I420;
        }

        @Override
        public int width() {
            return 32;
        }

        @Override
        public int height() {
            return 16;
        }

        @Override
        public int code(int plane, int column, int row) {
            if (plane == 0) {
                return 40 + column * 5 + (row & 1) * 3;
            }
            var block = (column / 4) + (row / 4) * 4;
            return plane == 1 ? 40 + (block * 53) % 180 : 216 - (block * 71) % 180;
        }
    }

    private static final Card CARD = new Card(VideoFrame.ColorMatrix.BT601, false);

    @Test
    @DisplayName("the right reading passes, and a level of rounding with it")
    void rightReadingPasses() {
        var picture = YuvReference.argb(CARD);
        assertEquals(Optional.empty(), YuvDiagnosis.mismatch(CARD, picture, 0));
        picture[5] += 0x010000;
        assertEquals(Optional.empty(), YuvDiagnosis.mismatch(CARD, picture, 1));
        assertTrue(YuvDiagnosis.mismatch(CARD, picture, 0).isPresent());
    }

    static Stream<Arguments> misreadings() {
        var right = Reading.of(CARD);
        return Stream.of(
                Arguments.of(new Reading(right.matrix(), false, Chroma.BLENDED, true, false), "Cb and Cr swapped"),
                Arguments.of(new Reading(right.matrix(), false, Chroma.BLENDED, false, true), "red and blue swapped"),
                Arguments.of(
                        new Reading(VideoFrame.ColorMatrix.BT709, false, Chroma.BLENDED, false, false),
                        "BT709 where the picture says BT601"),
                Arguments.of(
                        new Reading(right.matrix(), true, Chroma.BLENDED, false, false),
                        "full range where the picture says limited"),
                Arguments.of(
                        new Reading(right.matrix(), false, Chroma.REPEATED, false, false),
                        "chroma repeated over each 2×2 block"),
                Arguments.of(
                        new Reading(right.matrix(), false, Chroma.BLENDED_LEFT, false, false),
                        "chroma sited on the left column"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("misreadings")
    @DisplayName("a picture drawn under a misreading is put down to that misreading")
    void namesTheMisreading(Reading misreading, String words) {
        var picture = YuvReference.argb(CARD, misreading);
        var message = YuvDiagnosis.mismatch(CARD, picture, 2).orElseThrow();
        assertTrue(message.contains(words), message);
        assertEquals(misreading, YuvDiagnosis.closest(CARD, picture).reading());
    }

    @Test
    @DisplayName("two mistakes at once are both named")
    void twoMistakes() {
        var picture =
                YuvReference.argb(CARD, new Reading(VideoFrame.ColorMatrix.BT601, false, Chroma.REPEATED, true, false));
        var message = YuvDiagnosis.mismatch(CARD, picture, 2).orElseThrow();
        assertTrue(message.contains("Cb and Cr swapped") && message.contains("chroma repeated"), message);
    }

    @Test
    @DisplayName("a picture no misreading explains says so, rather than blaming one")
    void noMisreading() {
        var picture = YuvReference.argb(CARD);
        // Noise of eight levels everywhere: every reading is further off.
        for (var i = 0; i < picture.length; i++) {
            picture[i] ^= (i * 7 % 2 == 0 ? 0x080808 : 0x070707);
        }
        var message = YuvDiagnosis.mismatch(CARD, picture, 2).orElseThrow();
        assertTrue(message.contains("no misreading explains it"), message);
    }
}
