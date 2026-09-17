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

    /// Encodes `0xAARRGGBB` pixels as a WebP — [ADR-0385].
    ///
    /// ## Lossless is not the same trade as lossy
    ///
    /// `quality` is what libwebp's lossy path takes, `0..100`; a **negative**
    /// quality asks for the lossless path instead, which takes no quality
    /// because there is nothing to trade. That matters more here than it sounds:
    /// VP8's transform is worst at flat colour and hard edges, which is what a
    /// screenshot of a user interface is made of — so the lossy path is for
    /// photographs and the lossless one is for anything this toolkit drew.
    ///
    /// @param pixels `width * height` of `0xAARRGGBB`, not premultiplied
    /// @param width  the image's width, in pixels
    /// @param height its height
    /// @param quality `0..100` for the lossy path, or negative for lossless
    /// @return the encoded bytes, or null when libwebp refused — an image larger
    ///         than WebP's 16383-pixel limit on a side is the ordinary reason,
    ///         and it is a normal branch rather than an error
    // Restricted: the encoder reports an address and a length separately, so the
    // pointer has to be resized before the bytes can be read out. The extent is
    // the length that same call just returned.
    @SuppressWarnings("restricted")
    public byte[] encode(int[] pixels, int width, int height, float quality) {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("an image is " + width + "x" + height + ", which is not a size");
        }
        var count = Math.multiplyExact(width, height);
        if (pixels.length < count) {
            throw new IllegalArgumentException(
                    "a " + width + "x" + height + " image needs " + count + " pixels and was given " + pixels.length);
        }
        try (var arena = Arena.ofConfined()) {
            // RGBA bytes, which is the packing libwebp takes and the one the
            // toolkit does not use: the repacking is a shift and a mask, here
            // rather than at the call site.
            var rgba = arena.allocate(Math.multiplyExact(count, 4L));
            for (var i = 0; i < count; i++) {
                var argb = pixels[i];
                rgba.set(ValueLayout.JAVA_BYTE, i * 4L, (byte) ((argb >>> 16) & 0xFF));
                rgba.set(ValueLayout.JAVA_BYTE, i * 4L + 1, (byte) ((argb >>> 8) & 0xFF));
                rgba.set(ValueLayout.JAVA_BYTE, i * 4L + 2, (byte) (argb & 0xFF));
                rgba.set(ValueLayout.JAVA_BYTE, i * 4L + 3, (byte) ((argb >>> 24) & 0xFF));
            }
            var out = arena.allocate(ValueLayout.ADDRESS);
            var stride = Math.multiplyExact(width, 4);
            var size = quality < 0
                    ? calls.encodeLosslessRgba().call(rgba, width, height, stride, out)
                    : calls.encodeRgba().call(rgba, width, height, stride, Math.min(100f, quality), out);
            var encoded = out.get(ValueLayout.ADDRESS, 0);
            if (size <= 0 || MemorySegment.NULL.equals(encoded)) {
                return null;
            }
            try {
                var view = encoded.reinterpret(size);
                var result = new byte[Math.toIntExact(size)];
                MemorySegment.copy(view, ValueLayout.JAVA_BYTE, 0, result, 0, result.length);
                return result;
            } finally {
                calls.free().call(encoded);
            }
        }
    }

    /// Every frame of an animated WebP, each a **fully composited canvas** —
    /// [ADR-0385].
    ///
    /// Where animated GIF needed a disposal model written in Java (ADR-0382),
    /// this one is upstream's: `WebPAnimDecoderGetNext` hands back the whole
    /// canvas as it looks at that moment. What is left here is copying each one
    /// out before asking for the next, because the buffer belongs to the decoder
    /// and is reused.
    ///
    /// The timestamps libwebp reports are **cumulative** — the moment a frame
    /// stops being shown — and what a caller wants is a duration, so they are
    /// differenced here. The last frame has no successor to subtract from, so it
    /// keeps whatever the one before it lasted, and a single-frame animation
    /// falls back to the timestamp itself.
    ///
    /// @return the animation, or null when the bytes are not one — a still WebP
    ///         included, which is a normal branch and what routes a decode
    // Restricted: the frame buffer is an address with no extent, resized to the
    // canvas the decoder itself reported.
    @SuppressWarnings("restricted")
    public Animated decodeAnimation(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!bytes.hasRemaining()) {
            return null;
        }
        try (var arena = Arena.ofConfined()) {
            // The decoder points at these bytes and copies nothing, so they must
            // outlive it — which the arena is what guarantees.
            var data = copyIn(arena, bytes);
            var webpData = arena.allocate(WEBP_DATA_BYTES);
            webpData.set(ValueLayout.ADDRESS, 0, data);
            webpData.set(ValueLayout.JAVA_LONG, 8, bytes.remaining());

            var options = arena.allocate(ANIM_OPTIONS_BYTES);
            options.fill((byte) 0);
            if (calls.animOptionsInit().call(options, WebpCalls.DEMUX_ABI_VERSION) == 0) {
                return null;
            }
            var decoder = calls.animNew().call(webpData, options, WebpCalls.DEMUX_ABI_VERSION);
            if (MemorySegment.NULL.equals(decoder)) {
                return null;
            }
            try {
                var info = arena.allocate(ANIM_INFO_BYTES);
                info.fill((byte) 0);
                if (calls.animGetInfo().call(decoder, info) == 0) {
                    return null;
                }
                var width = info.get(ValueLayout.JAVA_INT, 0);
                var height = info.get(ValueLayout.JAVA_INT, 4);
                var loopCount = info.get(ValueLayout.JAVA_INT, 8);
                if (width <= 0 || height <= 0) {
                    return null;
                }

                var frames = new java.util.ArrayList<Decoded>();
                var ends = new java.util.ArrayList<Integer>();
                var buffer = arena.allocate(ValueLayout.ADDRESS);
                var timestamp = arena.allocate(ValueLayout.JAVA_INT);
                var count = Math.multiplyExact(width, height);
                while (calls.animHasMoreFrames().call(decoder) != 0) {
                    if (calls.animGetNext().call(decoder, buffer, timestamp) == 0) {
                        break;
                    }
                    var canvas = buffer.get(ValueLayout.ADDRESS, 0).reinterpret(Math.multiplyExact(count, 4L));
                    var pixels = new int[count];
                    for (var i = 0; i < count; i++) {
                        var r = canvas.get(ValueLayout.JAVA_BYTE, i * 4L) & 0xFF;
                        var g = canvas.get(ValueLayout.JAVA_BYTE, i * 4L + 1) & 0xFF;
                        var b = canvas.get(ValueLayout.JAVA_BYTE, i * 4L + 2) & 0xFF;
                        var a = canvas.get(ValueLayout.JAVA_BYTE, i * 4L + 3) & 0xFF;
                        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    frames.add(new Decoded(width, height, pixels));
                    ends.add(timestamp.get(ValueLayout.JAVA_INT, 0));
                }
                if (frames.isEmpty()) {
                    return null;
                }
                return new Animated(frames, durations(ends), loopCount);
            } finally {
                calls.animDelete().call(decoder);
            }
        }
    }

    /// Cumulative end times as per-frame durations.
    private static int[] durations(java.util.List<Integer> ends) {
        var durations = new int[ends.size()];
        var previous = 0;
        for (var i = 0; i < ends.size(); i++) {
            durations[i] = Math.max(0, ends.get(i) - previous);
            previous = ends.get(i);
        }
        if (durations.length > 1 && durations[durations.length - 1] == 0) {
            // The last frame's end is its own start when a file says nothing
            // about it: it lasts as long as the one before it rather than no time
            // at all.
            durations[durations.length - 1] = durations[durations.length - 2];
        }
        return durations;
    }

    /// Every frame of an animation, with how long each is shown and how many
    /// times the file asks to be played.
    ///
    /// A class rather than a record for [Decoded]'s reason: the durations are an
    /// array.
    public static final class Animated {

        private final java.util.List<Decoded> frames;
        private final int[] durations;
        private final int loopCount;

        Animated(java.util.List<Decoded> frames, int[] durations, int loopCount) {
            this.frames = java.util.List.copyOf(frames);
            this.durations = durations.clone();
            this.loopCount = loopCount;
        }

        /// The frames, in order, each the whole canvas.
        public java.util.List<Decoded> frames() {
            return frames;
        }

        /// How long each frame is shown, in milliseconds.
        public int[] durations() {
            return durations.clone();
        }

        /// How many times to play — **0 is for ever**, which is what the format
        /// means by it and what a GIF's NETSCAPE block means by it too.
        public int loopCount() {
            return loopCount;
        }
    }

    /// `sizeof(WebPData)`: a pointer and a `size_t`.
    private static final long WEBP_DATA_BYTES = 16;

    /// `sizeof(WebPAnimDecoderOptions)`: a colour mode, a flag and seven words of
    /// padding upstream reserved for later use.
    private static final long ANIM_OPTIONS_BYTES = 4 + 4 + 7 * 4L;

    /// `sizeof(WebPAnimInfo)`: five `uint32` fields and four of padding.
    private static final long ANIM_INFO_BYTES = 9 * 4L;

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
