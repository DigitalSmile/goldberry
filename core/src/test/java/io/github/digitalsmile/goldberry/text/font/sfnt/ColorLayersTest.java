package io.github.digitalsmile.goldberry.text.font.sfnt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The `COLR` and `CPAL` reader, against fonts assembled here — [ADR-0393].
///
/// Assembled rather than shipped, because `:core` has no emoji face in it: the
/// face lives in `goldberry-emoji` and the tests that read the real one are
/// there. What is checked here is the format, including the malformed cases a
/// real face never produces and this must not crash on.
class ColorLayersTest {

    @Test
    @DisplayName("a face with no COLR table has no colour glyphs")
    void noTableIsNoColour() {
        assertSame(ColorLayers.NONE, ColorLayers.read(font(new Table[0])));
        assertTrue(ColorLayers.NONE.isEmpty());
    }

    @Test
    @DisplayName("a COLR with no CPAL is no colour either, because there is nothing to fill with")
    void colrWithoutCpal() {
        var colr = colr(new int[][] {{3, 0, 1}}, new int[][] {{10, 0}});
        assertSame(ColorLayers.NONE, ColorLayers.read(font(new Table[] {new Table("COLR", colr)})));
    }

    @Test
    @DisplayName("layers come back in order, each with its palette colour")
    void layersAndTheirColours() {
        var layers = ColorLayers.read(twoGlyphs());

        assertFalse(layers.isEmpty());
        assertEquals(2, layers.size());
        assertEquals(3, layers.paletteSize());

        var first = layers.find(3);
        assertEquals(2, layers.layerCount(first));
        assertEquals(10, layers.layerGlyph(first, 0));
        assertEquals(0xFFFF0000, layers.layerArgb(first, 0, 0xFF000000), "palette entry 0 is opaque red");
        assertEquals(11, layers.layerGlyph(first, 1));
        assertEquals(0xFF00FF00, layers.layerArgb(first, 1, 0xFF000000));

        var second = layers.find(7);
        assertEquals(1, layers.layerCount(second));
        assertEquals(12, layers.layerGlyph(second, 0));
    }

    @Test
    @DisplayName("a glyph with no record is an ordinary outline")
    void aPlainGlyphIsNotFound() {
        assertEquals(-1, ColorLayers.read(twoGlyphs()).find(4));
    }

    @Test
    @DisplayName("palette entry 0xFFFF means whatever colour the text is")
    void theForegroundEntryFollowsTheText() {
        var layers = ColorLayers.read(twoGlyphs());
        var record = layers.find(7);

        assertEquals(0xFF123456, layers.layerArgb(record, 0, 0xFF123456));
        assertEquals(0xFFABCDEF, layers.layerArgb(record, 0, 0xFFABCDEF), "and it is the colour of *this* text");
    }

    @Test
    @DisplayName(
            "a palette index past the end of the palette follows the text rather than reading somebody else's colour")
    void anOutOfRangeEntryFollowsTheText() {
        var colr = colr(new int[][] {{5, 0, 1}}, new int[][] {{20, 99}});
        var layers = ColorLayers.read(font(new Table[] {new Table("COLR", colr), new Table("CPAL", cpal())}));

        assertEquals(0xFF00FF00, layers.layerArgb(layers.find(5), 0, 0xFF00FF00));
    }

    @Test
    @DisplayName("asking for a layer a record does not have is refused rather than answered")
    void layerIndexIsChecked() {
        var layers = ColorLayers.read(twoGlyphs());
        var record = layers.find(7);

        assertThrows(IndexOutOfBoundsException.class, () -> layers.layerGlyph(record, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> layers.layerArgb(record, 1, 0));
    }

    @Test
    @DisplayName("base glyph records out of order are refused, because a binary search would answer at random")
    void unsortedRecordsAreRefused() {
        var colr = colr(new int[][] {{9, 0, 1}, {2, 1, 1}}, new int[][] {{10, 0}, {11, 0}});
        var font = font(new Table[] {new Table("COLR", colr), new Table("CPAL", cpal())});

        assertSame(ColorLayers.NONE, ColorLayers.read(font));
    }

    @Test
    @DisplayName("a record pointing past the layer array is refused")
    void anOverrunningRecordIsRefused() {
        // Two layers claimed, one written: a file that would have this reader
        // draw a glyph the font never described.
        var colr = colr(new int[][] {{3, 0, 2}}, new int[][] {{10, 0}});
        var font = font(new Table[] {new Table("COLR", colr), new Table("CPAL", cpal())});
        assertSame(ColorLayers.NONE, ColorLayers.read(font));
    }

    @Test
    @DisplayName("a COLR version nobody has seen is refused rather than guessed at")
    void anUnknownVersionIsRefused() {
        var colr = colr(new int[][] {{3, 0, 1}}, new int[][] {{10, 0}});
        colr[1] = 9;

        var font = font(new Table[] {new Table("COLR", colr), new Table("CPAL", cpal())});
        assertSame(ColorLayers.NONE, ColorLayers.read(font));
    }

    @Test
    @DisplayName("bytes that are not a font at all are no colour, not an exception")
    void rubbishIsRefused() {
        assertSame(ColorLayers.NONE, ColorLayers.read(new byte[] {1, 2, 3}));
        assertSame(ColorLayers.NONE, ColorLayers.read(new byte[0]));
    }

    @Test
    @DisplayName("a collection is refused, because this API names no face inside one")
    void aCollectionIsRefused() {
        var bytes = font(new Table[] {new Table("COLR", colr(new int[][] {{3, 0, 1}}, new int[][] {{10, 0}}))});
        bytes[0] = 't';
        bytes[1] = 't';
        bytes[2] = 'c';
        bytes[3] = 'f';

        assertNull(TableDirectory.table(bytes, TableDirectory.tag('C', 'O', 'L', 'R')));
    }

    @Test
    @DisplayName("the directory finds a table by its tag and stops at its end")
    void theDirectoryFindsATable() {
        var colr = colr(new int[][] {{3, 0, 1}}, new int[][] {{10, 0}});
        var bytes = font(new Table[] {new Table("COLR", colr), new Table("CPAL", cpal())});

        var found = TableDirectory.table(bytes, TableDirectory.tag('C', 'O', 'L', 'R'));
        assertNotNull(found);
        assertEquals(colr.length, found.limit(), "the slice is the table and nothing after it");
        assertNull(TableDirectory.table(bytes, TableDirectory.tag('g', 'l', 'y', 'f')));
    }

    @Test
    @DisplayName("it says so in a sentence")
    void readable() {
        assertTrue(ColorLayers.read(twoGlyphs()).toString().contains("3 layers"));
    }

    // --- the fonts these tests are made of ------------------------------------

    private record Table(String tag, byte[] bytes) {}

    /// Glyph 3 in red then green, and glyph 7 in whatever the text is.
    private static byte[] twoGlyphs() {
        var colr = colr(new int[][] {{3, 0, 2}, {7, 2, 1}}, new int[][] {{10, 0}, {11, 1}, {12, 0xFFFF}});
        return font(new Table[] {new Table("COLR", colr), new Table("CPAL", cpal())});
    }

    /// A `COLR` version 0.
    ///
    /// @param records one `{glyphId, firstLayer, layerCount}` per base glyph
    /// @param layers  one `{glyphId, paletteIndex}` per layer
    private static byte[] colr(int[][] records, int[][] layers) {
        var size = 14 + records.length * 6 + layers.length * 4;
        var out = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        out.putShort((short) 0);
        out.putShort((short) records.length);
        out.putInt(14);
        out.putInt(14 + records.length * 6);
        out.putShort((short) layers.length);
        for (var record : records) {
            out.putShort((short) record[0]);
            out.putShort((short) record[1]);
            out.putShort((short) record[2]);
        }
        for (var layer : layers) {
            out.putShort((short) layer[0]);
            out.putShort((short) layer[1]);
        }
        return out.array();
    }

    /// Red, green, blue — one palette, in the BGRA order the table is written in.
    private static byte[] cpal() {
        var out = ByteBuffer.allocate(14 + 3 * 4).order(ByteOrder.BIG_ENDIAN);
        out.putShort((short) 0);
        out.putShort((short) 3);
        out.putShort((short) 1);
        out.putShort((short) 3);
        out.putInt(14);
        out.putShort((short) 0);
        out.put(new byte[] {0, 0, (byte) 255, (byte) 255});
        out.put(new byte[] {0, (byte) 255, 0, (byte) 255});
        out.put(new byte[] {(byte) 255, 0, 0, (byte) 255});
        return out.array();
    }

    /// The smallest thing [TableDirectory] will read: a header, a record per
    /// table, and the tables after them.
    private static byte[] font(Table[] tables) {
        var directory = ByteBuffer.allocate(12 + tables.length * 16).order(ByteOrder.BIG_ENDIAN);
        directory.putInt(0x00010000);
        directory.putShort((short) tables.length);
        directory.putShort((short) 0);
        directory.putShort((short) 0);
        directory.putShort((short) 0);

        var at = 12 + tables.length * 16;
        for (var table : tables) {
            directory.putInt(TableDirectory.tag(
                    table.tag().charAt(0), table.tag().charAt(1),
                    table.tag().charAt(2), table.tag().charAt(3)));
            directory.putInt(0);
            directory.putInt(at);
            directory.putInt(table.bytes().length);
            at += table.bytes().length;
        }

        var out = new ByteArrayOutputStream();
        out.writeBytes(directory.array());
        for (var table : tables) {
            out.writeBytes(table.bytes());
        }
        return out.toByteArray();
    }
}
