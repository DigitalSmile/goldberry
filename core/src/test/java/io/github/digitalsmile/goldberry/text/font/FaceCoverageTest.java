package io.github.digitalsmile.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;

/// What a face has glyphs for — [ADR-0386].
///
/// Asserted against the faces this jar ships, because a fixture font would be a
/// `cmap` somebody wrote to match the reader. Inter is the format 4 case; the
/// emoji face is the format 12 one and is tested in `:emoji`, where it lives.
class FaceCoverageTest {

    private static int[] coverage(BundledFont font) {
        return FaceCoverage.codePoints(BundledAssets.font(font));
    }

    @Test
    @DisplayName("the UI face has the Latin alphabet and then some")
    void inter() {
        var points = coverage(BundledFont.UI);

        assertTrue(points.length > 1000, () -> "only " + points.length + " code points");
        for (var character : "AZaz09 ,.".toCharArray()) {
            var wanted = character;
            assertTrue(Arrays.binarySearch(points, wanted) >= 0, () -> "no glyph for '" + wanted + "'");
        }
        // A face that covered these would be a face this test is not reading
        // correctly: Inter has no CJK and no emoji.
        assertTrue(Arrays.binarySearch(points, 0x4E00) < 0, "Inter has no CJK");
    }

    @Test
    @DisplayName("the answer is sorted, which is what a binary search rests on")
    void sorted() {
        var points = coverage(BundledFont.CODE);
        for (var i = 1; i < points.length; i++) {
            var at = i;
            assertTrue(points[at] > points[at - 1], () -> "out of order, or a duplicate, at " + at);
        }
    }

    @Test
    @DisplayName("bytes that are not a font are an empty answer, not an exception")
    void notAFont() {
        // A caller is asking what is in a face. "Nothing I can tell you" is an
        // answer it can act on; an exception from inside a picker is not.
        assertEquals(0, FaceCoverage.codePoints(new byte[] {1, 2, 3}).length);
        assertEquals(0, FaceCoverage.codePoints(new byte[0]).length);
    }

    @Test
    @DisplayName("and a truncated one is too")
    void truncated() {
        var font = BundledAssets.font(BundledFont.UI);
        var half = Arrays.copyOf(font, font.length / 2);

        // Every read is bounds-checked by the buffer, so a file that stops in the
        // middle of a table stops here rather than reporting a wrong answer.
        assertEquals(0, FaceCoverage.codePoints(Arrays.copyOf(font, 40)).length, "a header and nothing else");
        FaceCoverage.codePoints(half);
    }

    /// This class used to walk the table directory itself, and the copy had the
    /// directory's tag comparison without its `offset + length > limit` check: a
    /// `cmap` record claiming a table that runs off the end of the file was read
    /// anyway, out of whatever bytes followed it. It goes through
    /// [io.github.digitalsmile.goldberry.text.font.sfnt.TableDirectory#table] now,
    /// which hands back a **slice** that ends where the table says it does.
    ///
    /// The face's own bytes with one number changed, so what is under test is the
    /// check rather than a fixture somebody wrote to fail it.
    @Test
    @DisplayName("a cmap record that claims more bytes than the file has is refused")
    void cmapPastTheEndOfTheFile() {
        var font = BundledAssets.font(BundledFont.UI);
        var honest = FaceCoverage.codePoints(font);
        assertTrue(honest.length > 0, "the face this is measured against has a readable cmap");

        var lying = font.clone();
        var record = cmapRecord(lying);
        // The length field of the `cmap` record, four bytes after the offset. The
        // whole file's length, which from any non-zero offset runs past the end —
        // and does not overflow, so it is the check that refuses it rather than an
        // index computation wrapping into a negative number.
        write32(lying, record + 12, lying.length);

        assertEquals(
                0,
                FaceCoverage.codePoints(lying).length,
                "a table that does not fit in the file was read out of the bytes after it");
    }

    /// Where the `cmap` record sits in `font`'s table directory.
    private static int cmapRecord(byte[] font) {
        var cmap = ('c' << 24) | ('m' << 16) | ('a' << 8) | 'p';
        var tables = ((font[4] & 0xFF) << 8) | (font[5] & 0xFF);
        for (var i = 0; i < tables; i++) {
            var at = 12 + i * 16;
            if (read32(font, at) == cmap) {
                return at;
            }
        }
        throw new AssertionError("the bundled UI face has no cmap");
    }

    private static int read32(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24)
                | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8)
                | (bytes[at + 3] & 0xFF);
    }

    private static void write32(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 24);
        bytes[at + 1] = (byte) (value >>> 16);
        bytes[at + 2] = (byte) (value >>> 8);
        bytes[at + 3] = (byte) value;
    }
}
