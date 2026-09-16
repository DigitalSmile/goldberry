package io.github.digitalsmile.goldberry.image.gif;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/// A GIF, as far as its first frame — `docs/gaps.md` G35a, [ADR-0329].
///
/// ## Why this is Java and WebP is not
///
/// The rasterizer this toolkit ships has the PNG, JPEG and QOI codecs compiled
/// into it and no others, so neither GIF nor WebP decoded at all. WebP is VP8, a
/// video codec, and the answer there is the reference library
/// ([io.github.digitalsmile.goldberry.natives.webp.Webp]). GIF is a palette, LZW
/// and a handful of block headers — the whole specification is nine pages — so
/// the answer here is this file, and the toolkit does not take a second native
/// dependency for it.
///
/// It sits beside [io.github.digitalsmile.goldberry.image.png.PngEncoder] and is
/// the same kind of thing: a format small enough that owning it costs less than
/// linking it.
///
/// ## The first frame, and only the first
///
/// An animated GIF is a still image with more frames after it, and this reads the
/// first one. That is a deliberate stop rather than an oversight: what an
/// application does with a GIF here is put a picture on a board, and animation is
/// a scheduler, a frame-disposal model and a clock — none of which belongs in a
/// decoder. A GIF with one frame, which is most of them, decodes exactly.
///
/// The frame is composited onto the **logical screen** the file declares, so an
/// image whose first frame is smaller than the canvas comes back the size the
/// file says it is, with transparent pixels around it.
///
/// ## What comes out
///
/// `0xAARRGGBB`, row-major, **not** premultiplied — the packing every colour in
/// the toolkit is written in, and what
/// [io.github.digitalsmile.goldberry.image.Image#ofArgb] takes. Transparency is
/// the graphic control extension's transparent index, which is the only kind GIF
/// has.
public final class GifDecoder {

    /// Both signatures the format has ever had. `GIF87a` and `GIF89a` differ in
    /// which extension blocks may appear, and every one this reads is optional.
    private static final byte[] SIGNATURE = {'G', 'I', 'F'};

    /// Introduces an extension block — a graphic control, a comment, plain text,
    /// or an application block such as NETSCAPE's loop count.
    private static final int EXTENSION = 0x21;

    /// Introduces an image descriptor, which is the frame itself.
    private static final int IMAGE = 0x2C;

    /// Ends the file.
    private static final int TRAILER = 0x3B;

    /// The extension that carries the transparent colour index and the frame
    /// delay. The delay is read and discarded: see the note on animation above.
    private static final int GRAPHIC_CONTROL = 0xF9;

    /// The rows an interlaced GIF is written in: every eighth from 0, every
    /// eighth from 4, every fourth from 2, every second from 1.
    private static final int[] INTERLACE_START = {0, 4, 2, 1};

    private static final int[] INTERLACE_STEP = {8, 8, 4, 2};

    private GifDecoder() {}

    /// What one decoded GIF is: a size, and `width * height` pixels of
    /// `0xAARRGGBB` in row-major order.
    ///
    /// A class rather than a record, because a record component may not be an
    /// array: an array is mutable, so a record holding one would have an
    /// `equals` that lies and a caller able to change a value from underneath
    /// whoever else holds it. The pixels are handed over rather than copied —
    /// this is the decoder's own buffer, and its one caller turns it straight
    /// into an `Image`.
    public static final class Decoded {

        private final int width;
        private final int height;
        private final int[] argb;

        Decoded(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }

        /// The logical screen's width, which is the image's.
        public int width() {
            return width;
        }

        /// The same, vertically.
        public int height() {
            return height;
        }

        /// `width * height` pixels of `0xAARRGGBB`, row-major.
        public int[] argb() {
            return argb;
        }
    }

    /// Whether `bytes` begin with a GIF signature.
    ///
    /// Cheap and total: it reads six bytes and decodes nothing, which is what
    /// makes it usable as the route into the decoder rather than as a check
    /// inside it.
    public static boolean looksLikeGif(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.remaining() < 6) {
            return false;
        }
        var at = bytes.position();
        for (var i = 0; i < SIGNATURE.length; i++) {
            if (bytes.get(at + i) != SIGNATURE[i]) {
                return false;
            }
        }
        // "87" or "89" -- the version, which this does not otherwise care about.
        return bytes.get(at + 3) == '8'
                && (bytes.get(at + 4) == '7' || bytes.get(at + 4) == '9')
                && bytes.get(at + 5) == 'a';
    }

    /// Decodes the first frame of `bytes`.
    ///
    /// The buffer's position and limit are honoured and its position is not
    /// moved: a private read-only view is taken, so a caller may decode the same
    /// bytes twice.
    ///
    /// @throws GifFormatException if the bytes are not a GIF, or are a GIF that
    ///         ends in the middle of something
    public static Decoded decode(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!looksLikeGif(bytes)) {
            throw new GifFormatException("these bytes do not begin with a GIF signature");
        }
        // Little-endian, which is what every multi-byte field in the format is.
        var in = bytes.slice().order(ByteOrder.LITTLE_ENDIAN);
        try {
            return read(in);
        } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
            // A truncated file, which is an ordinary thing to be handed and not a
            // bug here. Translated so a caller catches one type.
            throw new GifFormatException("this GIF ends in the middle of a block: " + in.limit() + " bytes", e);
        }
    }

    private static Decoded read(ByteBuffer in) {
        in.position(6);
        var screenWidth = Short.toUnsignedInt(in.getShort());
        var screenHeight = Short.toUnsignedInt(in.getShort());
        var packed = Byte.toUnsignedInt(in.get());
        in.get(); // the background colour index, which this does not use: see below
        in.get(); // the pixel aspect ratio, which nothing has honoured since 1990

        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new GifFormatException("a GIF's logical screen is " + screenWidth + "x" + screenHeight
                    + ", which is not a size an image can have");
        }

        int[] globalPalette = null;
        if ((packed & 0x80) != 0) {
            globalPalette = palette(in, 2 << (packed & 0x07));
        }

        var transparentIndex = -1;
        while (true) {
            var block = Byte.toUnsignedInt(in.get());
            if (block == TRAILER) {
                throw new GifFormatException("this GIF has no image in it: the trailer came first");
            }
            if (block == EXTENSION) {
                var label = Byte.toUnsignedInt(in.get());
                if (label == GRAPHIC_CONTROL) {
                    var size = Byte.toUnsignedInt(in.get());
                    var fields = Byte.toUnsignedInt(in.get());
                    in.getShort(); // the delay, in hundredths: animation is not read
                    var index = Byte.toUnsignedInt(in.get());
                    transparentIndex = (fields & 0x01) != 0 ? index : -1;
                    // The block length is always 4 in every version of the
                    // specification, but skipping by what it says rather than by
                    // what it should say costs nothing and survives an extension.
                    in.position(in.position() + Math.max(0, size - 4));
                    skipSubBlocks(in);
                } else {
                    skipSubBlocks(in);
                }
                continue;
            }
            if (block == IMAGE) {
                return frame(in, screenWidth, screenHeight, globalPalette, transparentIndex);
            }
            throw new GifFormatException(
                    "0x" + Integer.toHexString(block) + " is not a block a GIF may start with here");
        }
    }

    /// The first image descriptor and the frame behind it, composited onto the
    /// logical screen.
    private static Decoded frame(
            ByteBuffer in, int screenWidth, int screenHeight, int[] globalPalette, int transparentIndex) {

        var left = Short.toUnsignedInt(in.getShort());
        var top = Short.toUnsignedInt(in.getShort());
        var width = Short.toUnsignedInt(in.getShort());
        var height = Short.toUnsignedInt(in.getShort());
        var packed = Byte.toUnsignedInt(in.get());
        var interlaced = (packed & 0x40) != 0;

        var palette = globalPalette;
        if ((packed & 0x80) != 0) {
            palette = palette(in, 2 << (packed & 0x07));
        }
        if (palette == null) {
            throw new GifFormatException("this GIF's first frame names no colour table, global or local");
        }
        if (width <= 0 || height <= 0) {
            throw new GifFormatException(
                    "a GIF frame is " + width + "x" + height + ", which is not a size an image can have");
        }

        var indices = Lzw.decode(in, Math.multiplyExact(width, height));

        // The whole screen, transparent, with the frame drawn into it. A frame
        // smaller than the screen is ordinary -- an optimizer writes one whenever
        // only part of the picture changed -- and cropping to it would hand back
        // an image of a size the file never claimed.
        var argb = new int[Math.multiplyExact(screenWidth, screenHeight)];
        for (var row = 0; row < height; row++) {
            var sourceRow = interlaced ? interlacedRow(row, height) : row;
            var y = top + sourceRow;
            if (y < 0 || y >= screenHeight) {
                continue;
            }
            for (var column = 0; column < width; column++) {
                var x = left + column;
                if (x < 0 || x >= screenWidth) {
                    continue;
                }
                var index = indices[row * width + column] & 0xFF;
                if (index == transparentIndex || index >= palette.length) {
                    // Transparent, or an index past the end of the table -- which
                    // real files contain and which every decoder treats as
                    // nothing rather than as a failure.
                    continue;
                }
                argb[y * screenWidth + x] = palette[index];
            }
        }
        return new Decoded(screenWidth, screenHeight, argb);
    }

    /// Which row of the image the `n`-th row of an interlaced frame is.
    private static int interlacedRow(int n, int height) {
        var seen = 0;
        for (var pass = 0; pass < INTERLACE_START.length; pass++) {
            var start = INTERLACE_START[pass];
            var step = INTERLACE_STEP[pass];
            var rows = start >= height ? 0 : (height - start + step - 1) / step;
            if (n < seen + rows) {
                return start + (n - seen) * step;
            }
            seen += rows;
        }
        // Past the end of the last pass, which a well-formed file cannot reach:
        // the four passes cover every row exactly once.
        return n;
    }

    /// `count` RGB triples, as `0xFFRRGGBB`.
    private static int[] palette(ByteBuffer in, int count) {
        var colours = new int[count];
        for (var i = 0; i < count; i++) {
            var r = Byte.toUnsignedInt(in.get());
            var g = Byte.toUnsignedInt(in.get());
            var b = Byte.toUnsignedInt(in.get());
            colours[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return colours;
    }

    /// Walks a chain of length-prefixed sub-blocks to its terminating zero.
    private static void skipSubBlocks(ByteBuffer in) {
        var length = Byte.toUnsignedInt(in.get());
        while (length != 0) {
            in.position(in.position() + length);
            length = Byte.toUnsignedInt(in.get());
        }
    }
}
