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
/// ## One frame, or all of them
///
/// [#decode] reads the first frame, which is what a still picture on a board
/// needs and what most GIFs contain. [#decodeAll] reads the sequence: every
/// frame composited onto the one before it under the file's own disposal rules,
/// each with the delay it declares, plus how many times the file says to loop
/// ([ADR-0382]).
///
/// The disposal model is the whole of why a frame cannot simply be decoded on
/// its own. A GIF frame is usually a **patch** — an optimizer writes only the
/// pixels that changed — and what is under it depends on what the frame before
/// it asked for when it left: keep the canvas, clear the patch back to
/// transparent, or put back what was there before the patch was drawn. A decoder
/// that ignored it draws a trail of every frame at once, which is the classic
/// way an animated GIF renders wrong.
///
/// The clock is still nobody's business here. What comes out is frames and
/// durations; [io.github.digitalsmile.goldberry.image.anim.Animation] is what
/// turns "how long has this been playing" into which of them to draw.
///
/// Each frame is composited onto the **logical screen** the file declares, so an
/// image whose frames are smaller than the canvas comes back the size the file
/// says it is, with transparent pixels around it.
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

    /// The extension that carries the transparent colour index, the frame delay
    /// and what to do with the frame when it is over.
    private static final int GRAPHIC_CONTROL = 0xF9;

    /// The extension a loop count is written in — NETSCAPE2.0, which is not in
    /// any specification and is in almost every animated GIF.
    private static final int APPLICATION = 0xFF;

    /// What a frame asks for when it is over: leave the canvas alone.
    private static final int DISPOSE_KEEP = 1;

    /// Clear the frame's own rectangle back to transparent.
    private static final int DISPOSE_BACKGROUND = 2;

    /// Put back what was under the frame before it was drawn.
    private static final int DISPOSE_PREVIOUS = 3;

    /// What a frame with no delay is worth, in hundredths of a second.
    ///
    /// Browsers agree on this and no specification says it: a file asking for 0
    /// means "as fast as possible", every renderer refuses, and 100 fps is not
    /// what the author wanted either. 10 is the number Firefox and Chromium both
    /// use for delays below their floor.
    private static final int DEFAULT_DELAY_HUNDREDTHS = 10;

    /// Below this, a delay is the default above instead — the same floor, and the
    /// same two browsers.
    private static final int MINIMUM_DELAY_HUNDREDTHS = 2;

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

    /// Every frame of a GIF, each already composited, with how long it is shown
    /// and how many times the file asks to be played.
    ///
    /// @param frames    the frames, in order
    /// @param loopCount how many times to play — **0 means for ever**, which is
    ///                  what the NETSCAPE extension's own 0 means, and 1 is what
    ///                  a file with no such extension gets
    public record Sequence(java.util.List<Frame> frames, int loopCount) {

        public Sequence {
            frames = java.util.List.copyOf(frames);
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("a sequence with no frames in it is not one");
            }
        }

        /// Whether this is one frame — a still picture, which most GIFs are.
        public boolean isStill() {
            return frames.size() == 1;
        }

        /// How long one pass through takes, in milliseconds.
        public int durationMillis() {
            var total = 0;
            for (var frame : frames) {
                total += frame.delayMillis();
            }
            return total;
        }
    }

    /// One frame of a [Sequence]: the whole logical screen as it looks while
    /// that frame is shown, and how long it is shown for.
    public record Frame(Decoded image, int delayMillis) {

        public Frame {
            Objects.requireNonNull(image, "image");
            if (delayMillis < 0) {
                throw new IllegalArgumentException("a frame is shown for " + delayMillis + "ms, which is not a time");
            }
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
        } catch (BufferUnderflowException | IndexOutOfBoundsException | ArithmeticException e) {
            // A truncated file, which is an ordinary thing to be handed and not a
            // bug here. Translated so a caller catches one type -- which is the
            // whole contract, so `ArithmeticException` is here too: the sizes in
            // a GIF are the file's numbers, and an overflow on them is a
            // malformed file rather than a defect in this class.
            throw new GifFormatException("this GIF ends in the middle of a block: " + in.limit() + " bytes", e);
        }
    }

    /// Decodes every frame of `bytes`.
    ///
    /// Each frame is the whole logical screen with that frame drawn onto what the
    /// frames before it left — see the note on disposal above. A GIF with one
    /// frame comes back as a sequence of one, and is exactly what [#decode]
    /// returns.
    ///
    /// @throws GifFormatException if the bytes are not a GIF, or are a GIF that
    ///         ends in the middle of something
    public static Sequence decodeAll(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!looksLikeGif(bytes)) {
            throw new GifFormatException("these bytes do not begin with a GIF signature");
        }
        var in = bytes.slice().order(ByteOrder.LITTLE_ENDIAN);
        try {
            return readAll(in);
        } catch (BufferUnderflowException | IndexOutOfBoundsException | ArithmeticException e) {
            throw new GifFormatException("this GIF ends in the middle of a block: " + in.limit() + " bytes", e);
        }
    }

    /// Every frame, in order, each composited onto what the one before it left.
    private static Sequence readAll(ByteBuffer in) {
        var screen = header(in);
        var frames = new java.util.ArrayList<Frame>();
        var loopCount = 1;

        // The canvas every frame is drawn onto, and what the *next* frame will
        // find there. A GIF frame is a patch on what is already drawn, which is
        // the whole reason this loop carries a canvas rather than decoding each
        // frame on its own.
        var canvas = new int[Math.multiplyExact(screen.width(), screen.height())];
        var control = Control.NONE;

        while (true) {
            var block = Byte.toUnsignedInt(in.get());
            if (block == TRAILER) {
                break;
            }
            if (block == EXTENSION) {
                var label = Byte.toUnsignedInt(in.get());
                if (label == GRAPHIC_CONTROL) {
                    control = graphicControl(in);
                } else if (label == APPLICATION) {
                    var found = loopCount(in);
                    if (found >= 0) {
                        loopCount = found;
                    }
                } else {
                    skipSubBlocks(in);
                }
                continue;
            }
            if (block != IMAGE) {
                throw new GifFormatException(
                        "0x" + Integer.toHexString(block) + " is not a block a GIF may start with here");
            }

            var patch = descriptor(in, screen, control.transparentIndex());
            // What has to go back when this frame is over, captured *before* it
            // is drawn — because "previous" means before, and after is too late.
            var restore = control.disposal() == DISPOSE_PREVIOUS ? canvas.clone() : null;
            draw(canvas, screen, patch);
            frames.add(new Frame(new Decoded(screen.width(), screen.height(), canvas.clone()), control.delayMillis()));

            canvas = disposed(canvas, screen, patch, control.disposal(), restore);
            control = Control.NONE;
        }
        if (frames.isEmpty()) {
            throw new GifFormatException("this GIF has no image in it: the trailer came first");
        }
        return new Sequence(frames, loopCount);
    }

    /// The canvas the next frame starts from.
    private static int[] disposed(int[] canvas, Screen screen, Patch patch, int disposal, int[] restore) {
        return switch (disposal) {
            case DISPOSE_BACKGROUND -> {
                // The frame's own rectangle back to transparent. "Background" is
                // what the specification calls it and transparent is what every
                // renderer does: the background *colour* index has meant nothing
                // since the format left CompuServe.
                for (var y = Math.max(0, patch.top());
                        y < Math.min(screen.height(), patch.top() + patch.height());
                        y++) {
                    for (var x = Math.max(0, patch.left());
                            x < Math.min(screen.width(), patch.left() + patch.width());
                            x++) {
                        canvas[y * screen.width() + x] = 0;
                    }
                }
                yield canvas;
            }
            case DISPOSE_PREVIOUS -> restore == null ? canvas : restore;
            // KEEP, and "no disposal specified" -- which is 0, and which every
            // renderer treats as keep.
            default -> canvas;
        };
    }

    /// The header, up to and including the global colour table.
    /// The most pixels this decoder will allocate for one image: 256 megapixels,
    /// a gigabyte of `int[]`. Comfortably above any picture and well below what
    /// two 16-bit fields can name.
    private static final long MAX_PIXELS = 256L * 1024 * 1024;

    private static Screen header(ByteBuffer in) {
        in.position(6);
        var width = Short.toUnsignedInt(in.getShort());
        var height = Short.toUnsignedInt(in.getShort());
        var packed = Byte.toUnsignedInt(in.get());
        in.get(); // the background colour index, which nothing here uses
        in.get(); // the pixel aspect ratio, which nothing has honoured since 1990
        if (width <= 0 || height <= 0) {
            throw new GifFormatException(
                    "a GIF's logical screen is " + width + "x" + height + ", which is not a size an image can have");
        }
        // Both fields are 16 bits, so a header may legally claim 65535x65535 --
        // four billion pixels, sixteen gigabytes of `int[]`. `Math.multiplyExact`
        // below threw `ArithmeticException` for it, which `decode` does not
        // translate and `Image.decodeGif` therefore does not turn into an
        // `ImageDecodeException` (the 2026-09-18 review, C12). Refused here, in
        // the format's own words, rather than as arithmetic.
        if ((long) width * height > MAX_PIXELS) {
            throw new GifFormatException("a GIF's logical screen is " + width + "x" + height + ", which is "
                    + (long) width * height + " pixels: more than this decoder will allocate");
        }
        int[] palette = null;
        if ((packed & 0x80) != 0) {
            palette = palette(in, 2 << (packed & 0x07));
        }
        return new Screen(width, height, palette);
    }

    /// The logical screen, and the colours a frame falls back to.
    ///
    /// A class rather than a record for [Decoded]'s reason: the palette is an
    /// array, and a record holding one has an `equals` that lies.
    private static final class Screen {

        private final int width;
        private final int height;
        private final int[] palette;

        Screen(int width, int height, int[] palette) {
            this.width = width;
            this.height = height;
            this.palette = palette;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        int[] palette() {
            return palette;
        }
    }

    /// What a graphic control extension said about the frame after it.
    private record Control(int disposal, int delayMillis, int transparentIndex) {

        /// What a frame with no control extension before it gets.
        static final Control NONE = new Control(DISPOSE_KEEP, hundredthsToMillis(0), -1);
    }

    /// Reads a graphic control extension.
    private static Control graphicControl(ByteBuffer in) {
        var size = Byte.toUnsignedInt(in.get());
        var fields = Byte.toUnsignedInt(in.get());
        var hundredths = Short.toUnsignedInt(in.getShort());
        var index = Byte.toUnsignedInt(in.get());
        // The block length is always 4 in every version of the specification, but
        // skipping by what it says rather than by what it should say costs
        // nothing and survives an extension.
        skip(in, Math.max(0, size - 4));
        skipSubBlocks(in);
        return new Control((fields >> 2) & 0x07, hundredthsToMillis(hundredths), (fields & 0x01) != 0 ? index : -1);
    }

    /// A delay in hundredths as milliseconds, with the floor every renderer
    /// applies — see [#DEFAULT_DELAY_HUNDREDTHS].
    private static int hundredthsToMillis(int hundredths) {
        return (hundredths < MINIMUM_DELAY_HUNDREDTHS ? DEFAULT_DELAY_HUNDREDTHS : hundredths) * 10;
    }

    /// The loop count out of a NETSCAPE2.0 application extension, or -1 for any
    /// other application block.
    private static int loopCount(ByteBuffer in) {
        var size = Byte.toUnsignedInt(in.get());
        var identifier = new byte[size];
        in.get(identifier);
        var netscape = new String(identifier, java.nio.charset.StandardCharsets.US_ASCII).startsWith("NETSCAPE");
        var loops = -1;
        var length = Byte.toUnsignedInt(in.get());
        while (length != 0) {
            var at = in.position();
            if (netscape && length >= 3 && Byte.toUnsignedInt(in.get(at)) == 1) {
                loops = Short.toUnsignedInt(in.getShort(at + 1));
            }
            in.position(at + length);
            length = Byte.toUnsignedInt(in.get());
        }
        return loops;
    }

    private static Decoded read(ByteBuffer in) {
        var screen = header(in);
        var control = Control.NONE;
        while (true) {
            var block = Byte.toUnsignedInt(in.get());
            if (block == TRAILER) {
                throw new GifFormatException("this GIF has no image in it: the trailer came first");
            }
            if (block == EXTENSION) {
                var label = Byte.toUnsignedInt(in.get());
                if (label == GRAPHIC_CONTROL) {
                    control = graphicControl(in);
                } else if (label == APPLICATION) {
                    loopCount(in);
                } else {
                    skipSubBlocks(in);
                }
                continue;
            }
            if (block == IMAGE) {
                var patch = descriptor(in, screen, control.transparentIndex());
                // The whole screen, transparent, with the frame drawn into it. A
                // frame smaller than the screen is ordinary -- an optimizer
                // writes one whenever only part of the picture changed -- and
                // cropping to it would hand back an image of a size the file
                // never claimed.
                var argb = new int[Math.multiplyExact(screen.width(), screen.height())];
                draw(argb, screen, patch);
                return new Decoded(screen.width(), screen.height(), argb);
            }
            throw new GifFormatException(
                    "0x" + Integer.toHexString(block) + " is not a block a GIF may start with here");
        }
    }

    /// One frame's own pixels and where they go on the screen. Transparent
    /// pixels are a zero, which is what makes drawing one a test on alpha.
    ///
    /// A class for [Screen]'s reason.
    private static final class Patch {

        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final int[] argb;

        Patch(int left, int top, int width, int height, int[] argb) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.argb = argb;
        }

        int left() {
            return left;
        }

        int top() {
            return top;
        }

        int width() {
            return width;
        }

        int height() {
            return height;
        }

        int[] argb() {
            return argb;
        }
    }

    /// Reads an image descriptor and the LZW data behind it.
    private static Patch descriptor(ByteBuffer in, Screen screen, int transparentIndex) {
        var left = Short.toUnsignedInt(in.getShort());
        var top = Short.toUnsignedInt(in.getShort());
        var width = Short.toUnsignedInt(in.getShort());
        var height = Short.toUnsignedInt(in.getShort());
        var packed = Byte.toUnsignedInt(in.get());
        var interlaced = (packed & 0x40) != 0;

        var palette = screen.palette();
        if ((packed & 0x80) != 0) {
            palette = palette(in, 2 << (packed & 0x07));
        }
        if (palette == null) {
            throw new GifFormatException("this GIF has a frame that names no colour table, global or local");
        }
        if (width <= 0 || height <= 0) {
            throw new GifFormatException(
                    "a GIF frame is " + width + "x" + height + ", which is not a size an image can have");
        }

        var indices = Lzw.decode(in, Math.multiplyExact(width, height));
        var argb = new int[width * height];
        for (var row = 0; row < height; row++) {
            var sourceRow = interlaced ? interlacedRow(row, height) : row;
            for (var column = 0; column < width; column++) {
                var index = indices[row * width + column] & 0xFF;
                if (index == transparentIndex || index >= palette.length) {
                    // Transparent, or an index past the end of the table -- which
                    // real files contain and which every decoder treats as
                    // nothing rather than as a failure.
                    continue;
                }
                argb[sourceRow * width + column] = palette[index];
            }
        }
        return new Patch(left, top, width, height, argb);
    }

    /// Draws a patch onto a canvas, leaving its transparent pixels alone.
    private static void draw(int[] canvas, Screen screen, Patch patch) {
        for (var row = 0; row < patch.height(); row++) {
            var y = patch.top() + row;
            if (y < 0 || y >= screen.height()) {
                continue;
            }
            for (var column = 0; column < patch.width(); column++) {
                var x = patch.left() + column;
                if (x < 0 || x >= screen.width()) {
                    continue;
                }
                var pixel = patch.argb()[row * patch.width() + column];
                if (pixel != 0) {
                    canvas[y * screen.width() + x] = pixel;
                }
            }
        }
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
            skip(in, length);
            length = Byte.toUnsignedInt(in.get());
        }
    }

    /// Moves the cursor on by `count` bytes, or says the file ran out.
    ///
    /// `ByteBuffer.position(int)` throws `IllegalArgumentException` past the
    /// limit, and that is not one of the two types [#decode] translates — so a
    /// sub-block whose length ran off the end of a truncated file came out of
    /// `Image.decodeGif` as an `IllegalArgumentException` rather than an
    /// `ImageDecodeException` (the 2026-09-18 review, C12). Every cursor move
    /// driven by a number the *file* chose goes through here.
    private static void skip(ByteBuffer in, int count) {
        if (count > in.remaining()) {
            throw new GifFormatException("this GIF ends in the middle of a block: " + count
                    + " more bytes were named and " + in.remaining() + " are left");
        }
        in.position(in.position() + count);
    }
}
