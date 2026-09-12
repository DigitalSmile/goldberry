package io.github.digitalsmile.goldberry.image.png;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import io.github.digitalsmile.goldberry.image.Image;

/// An [Image] as PNG bytes.
///
/// ## Why this is Java's and the decoder is not
///
/// Decoding needs a JPEG decoder, which is thousands of lines nobody should write
/// twice, so it is Blend2D's. *Encoding* a PNG is a `Deflater` — which is in
/// `java.base` — wrapped in four chunks and a CRC, so it is this file, and the
/// export list stays three symbols shorter than it would otherwise be. That is
/// ADR-0278's reasoning applied a second time: work the rasterizer does not have
/// to do for us is work that should not cross the boundary (ADR-0283).
///
/// It also means a headless server can turn a rendered frame into a PNG with no
/// native library involved in the encode at all, which is what the offscreen
/// render this unblocks is for.
///
/// ## What it writes
///
/// Deliberately the narrowest PNG that is still a PNG, and the same shape the
/// golden-image harness has always written: **8-bit RGBA, no interlacing, one
/// `IDAT`**. No `tRNS`, no palette, no ancillary chunks. Every viewer reads it,
/// and there is no branch in here for a reader to disagree with.
///
/// The golden harness keeps its own copy of this (`core/src/testFixtures`,
/// `golden.Png`) rather than calling this one, and on purpose: a golden image
/// written *and* read by the code under test proves nothing about either half. Its
/// reader is the independent check on this writer, and the round-trip test between
/// them is what says the two agree.
public final class PngEncoder {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /// Colour type 6: truecolour with alpha.
    private static final byte COLOUR_TYPE_RGBA = 6;

    private static final byte BIT_DEPTH = 8;

    /// Filter type 0 — "None" — at the start of every row.
    ///
    /// PNG's other filters predict a pixel from its neighbours and would compress
    /// a photograph better. They are not free to compute and this encoder's images
    /// are UI-sized, so the bytes written are the pixels themselves and the only
    /// compression is the Deflater's.
    private static final byte FILTER_NONE = 0;

    private PngEncoder() {}

    /// Encodes `image` as PNG bytes.
    ///
    /// The alpha channel is written **unpremultiplied**, because that is what PNG
    /// means by an alpha channel: a half-transparent white pixel is `FF FF FF 80`
    /// in a file and `80 80 80 80` in the buffer, and writing the buffer's bytes
    /// straight out would produce an image that is visibly too dark everywhere it
    /// is translucent.
    public static byte[] encode(Image image) {
        Objects.requireNonNull(image, "image");
        var width = image.width();
        var height = image.height();

        var out = new ByteArrayOutputStream(1024 + width * height * 4);
        out.writeBytes(SIGNATURE);
        chunk(out, "IHDR", header(width, height));
        chunk(out, "IDAT", deflate(rows(image)));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /// The 13-byte `IHDR`: size, depth, colour type, and three zeroes for the
    /// compression, filter and interlace methods — of which PNG defines exactly
    /// one each that anybody uses.
    private static byte[] header(int width, int height) {
        return ByteBuffer.allocate(13)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(width)
                .putInt(height)
                .put(BIT_DEPTH)
                .put(COLOUR_TYPE_RGBA)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0)
                .array();
    }

    /// The raw scanlines: a filter byte and then RGBA, row by row.
    private static byte[] rows(Image image) {
        var width = image.width();
        var height = image.height();
        var raw = new byte[Math.multiplyExact(height, 1 + Math.multiplyExact(width, 4))];
        var at = 0;
        for (var y = 0; y < height; y++) {
            raw[at++] = FILTER_NONE;
            for (var x = 0; x < width; x++) {
                // Through `argb`, which is where premultiplication is undone. The
                // buffer's own bytes are BGRA and premultiplied; a PNG is RGBA and
                // is not.
                var argb = image.argb(x, y);
                raw[at++] = (byte) (argb >> 16);
                raw[at++] = (byte) (argb >> 8);
                raw[at++] = (byte) argb;
                raw[at++] = (byte) (argb >>> 24);
            }
        }
        return raw;
    }

    private static byte[] deflate(byte[] raw) {
        var deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            var out = new ByteArrayOutputStream(raw.length / 2 + 64);
            var chunk = new byte[8192];
            while (!deflater.finished()) {
                var written = deflater.deflate(chunk);
                out.write(chunk, 0, written);
            }
            return out.toByteArray();
        } finally {
            // A Deflater holds native zlib state that the collector will not free
            // for us in any timely way, and an encoder called per frame by a
            // server would accumulate them.
            deflater.end();
        }
    }

    /// Length, type, data, CRC — the shape of every PNG chunk. The CRC covers the
    /// type and the data and **not** the length, which is the one detail a
    /// hand-written encoder gets wrong.
    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        var tag = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(data.length)
                .array());
        out.writeBytes(tag);
        out.writeBytes(data);

        var crc = new CRC32();
        crc.update(tag);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt((int) crc.getValue())
                .array());
    }
}
