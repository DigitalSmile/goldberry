package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.nio.ByteBuffer;

import org.jspecify.annotations.Nullable;

/// A face's `CPAL` table, read for its first palette.
///
/// Shared by both colour formats: `COLR` version 0 ([ColorLayers]) and version 1
/// ([ColorPaints]) index the same table the same way, and two copies of a
/// twenty-line reader are two places for the byte order to be wrong.
final class Palette {

    /// The palette index that means "whatever colour the text is".
    ///
    /// A face uses it for the parts of a glyph that should follow the surrounding
    /// prose rather than the palette.
    static final int FOREGROUND = 0xFFFF;

    private Palette() {}

    /// Palette zero, as `0xAARRGGBB`, or null when the table says nothing usable.
    ///
    /// The first palette and not a chosen one: `CPAL` allows several — a light
    /// set and a dark set — and choosing between them is a question about a
    /// theme that nothing has asked yet. When it is asked, this is where it goes.
    static int @Nullable [] first(ByteBuffer cpal) {
        var entries = Short.toUnsignedInt(cpal.getShort(2));
        var palettes = Short.toUnsignedInt(cpal.getShort(4));
        var records = Short.toUnsignedInt(cpal.getShort(6));
        var recordsOffset = cpal.getInt(8);
        if (entries == 0 || palettes == 0 || records == 0) {
            return null;
        }
        var first = Short.toUnsignedInt(cpal.getShort(12));
        if (first + entries > records) {
            return null;
        }
        var colours = new int[entries];
        for (var i = 0; i < entries; i++) {
            // BGRA in the file, in that order, one byte each.
            var at = recordsOffset + (first + i) * 4;
            var blue = Byte.toUnsignedInt(cpal.get(at));
            var green = Byte.toUnsignedInt(cpal.get(at + 1));
            var red = Byte.toUnsignedInt(cpal.get(at + 2));
            var alpha = Byte.toUnsignedInt(cpal.get(at + 3));
            colours[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        return colours;
    }
}
