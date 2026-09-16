package io.github.digitalsmile.goldberry.natives.webp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.webp.calls.WebpCalls;

/// The WebP decoder, as one call that hands back pixels Java owns —
/// `docs/gaps.md` G35a, [ADR-0329].
///
/// ## Why it exists
///
/// The rasterizer this toolkit ships is compiled with the PNG, JPEG and QOI
/// codecs and no others, so a WebP — which is what most screenshot tools now
/// write — did not decode at all. The format is VP8, a video codec; there is no
/// pure-Java answer worth writing, and the reference decoder is BSD and small.
///
/// ## A value, not a handle
///
/// [#decode] allocates through libwebp, copies into a Java array and frees the
/// native buffer before it returns, so what comes back is an `int[]` with no
/// lifetime attached — the same bargain
/// [io.github.digitalsmile.goldberry.natives.blend2d.BlendDecodedImage] makes,
/// paid for the same reason: only the decoder knows how big the image is.
///
/// The `MemorySegment` never leaves this class, which is §3.1's rule and the
/// narrower one this keeps — it never leaves the call.
///
/// ## The pixels
///
/// `0xAARRGGBB`, **not** premultiplied: that is the packing every colour in the
/// toolkit is written in, and the premultiplication a buffer needs is
/// `io.github.digitalsmile.goldberry.image.Image`'s to do once. libwebp hands
/// back straight RGBA bytes, so the repacking here is a shift and an or.
public final class Webp {

    private static final class Holder {
        private static final Webp INSTANCE = new Webp(NativeLibrary.get().lookup());
    }

    private final WebpCalls calls;

    private Webp(SymbolLookup lookup) {
        this.calls = WebpCalls.bind(lookup);
    }

    /// The decoder, loading `libgoldberry` on first call.
    public static Webp get() {
        return Holder.INSTANCE;
    }

    /// What one decoded WebP is: a size, and `width * height` pixels of
    /// `0xAARRGGBB` in row-major order.
    ///
    /// A class rather than a record, because a record component may not be an
    /// array: an array is mutable, so a record holding one would have an `equals`
    /// that lies. The pixels are handed over rather than copied — they were
    /// allocated for this call and their one caller turns them straight into an
    /// `Image`.
    public static final class Decoded {

        private final int width;
        private final int height;
        private final int[] pixels;

        Decoded(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /// `width * height` pixels of `0xAARRGGBB`, row-major, not premultiplied.
        public int[] pixels() {
            return pixels;
        }
    }

    /// Whether `bytes` are a WebP whose header parses, and how big it is.
    ///
    /// **Nothing is decoded**, which is what this is for: it is the cheapest
    /// honest answer to "is this a WebP", and the size comes free with it.
    ///
    /// @return the image's size as `{width, height}`, or null when these bytes are
    ///         not a WebP
    public int[] size(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!bytes.hasRemaining()) {
            return null;
        }
        try (var arena = Arena.ofConfined()) {
            var data = copyIn(arena, bytes);
            var width = arena.allocate(ValueLayout.JAVA_INT);
            var height = arena.allocate(ValueLayout.JAVA_INT);
            if (calls.getInfo().call(data, bytes.remaining(), width, height) == 0) {
                return null;
            }
            return new int[] {width.get(ValueLayout.JAVA_INT, 0), height.get(ValueLayout.JAVA_INT, 0)};
        }
    }

    /// Decodes `bytes` into `0xAARRGGBB` pixels.
    ///
    /// @return the image, or null when the bytes are not a WebP this library can
    ///         decode — which is a normal branch and not an error, because the
    ///         bytes came from somewhere nobody controls
    // Restricted: `WebPDecodeRGBA` reports an address and carries no extent, so
    // the pointer has to be resized before anything can be read through it. The
    // extent given is the one the same call just stated -- width times height
    // times four -- which is exactly the region libwebp allocated and no more.
    @SuppressWarnings("restricted")
    public Decoded decode(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!bytes.hasRemaining()) {
            return null;
        }
        try (var arena = Arena.ofConfined()) {
            var data = copyIn(arena, bytes);
            var widthOut = arena.allocate(ValueLayout.JAVA_INT);
            var heightOut = arena.allocate(ValueLayout.JAVA_INT);
            var rgba = calls.decodeRgba().call(data, bytes.remaining(), widthOut, heightOut);
            if (MemorySegment.NULL.equals(rgba)) {
                return null;
            }
            try {
                var width = widthOut.get(ValueLayout.JAVA_INT, 0);
                var height = heightOut.get(ValueLayout.JAVA_INT, 0);
                if (width <= 0 || height <= 0) {
                    return null;
                }
                var count = Math.multiplyExact(width, height);
                var view = rgba.reinterpret(Math.multiplyExact(count, 4L));
                var pixels = new int[count];
                for (var i = 0; i < count; i++) {
                    var r = view.get(ValueLayout.JAVA_BYTE, i * 4L) & 0xFF;
                    var g = view.get(ValueLayout.JAVA_BYTE, i * 4L + 1) & 0xFF;
                    var b = view.get(ValueLayout.JAVA_BYTE, i * 4L + 2) & 0xFF;
                    var a = view.get(ValueLayout.JAVA_BYTE, i * 4L + 3) & 0xFF;
                    pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                return new Decoded(width, height, pixels);
            } finally {
                // libwebp's own free, not the process's: the two need not be the
                // same allocator, and a mismatched pair corrupts the heap rather
                // than reporting anything.
                calls.free().call(rgba);
            }
        }
    }

    /// `bytes` in a confined segment libwebp can read.
    ///
    /// Copied rather than handed over as a direct buffer, because the caller's may
    /// be a heap one — `Image.decode(byte[])` wraps an array — and because a
    /// decoder must not be able to see past the position and limit it was given.
    private static MemorySegment copyIn(Arena arena, ByteBuffer bytes) {
        var length = bytes.remaining();
        var segment = arena.allocate(length);
        MemorySegment.copy(MemorySegment.ofBuffer(bytes), 0, segment, 0, length);
        return segment;
    }
}
