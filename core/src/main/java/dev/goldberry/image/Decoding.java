package dev.goldberry.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.Function;

import dev.goldberry.image.gif.GifDecoder;
import dev.goldberry.image.gif.GifFormatException;
import dev.goldberry.natives.blend2d.BlendDecodedImage;
import dev.goldberry.natives.blend2d.error.BlendException;
import dev.goldberry.natives.webp.Webp;
import dev.goldberry.render.model.PhysicalSize;

/// Decoding, routed by what the bytes are: the rasterizer's own formats through
/// Blend2D, GIF through the toolkit's decoder and WebP through libwebp.
///
/// [Image#decode(ByteBuffer)] and [Image#decodeInto] both come through here,
/// so the two agree on what is an image and on how a refusal is worded.
final class Decoding {

    private Decoding() {}

    /// An image a Java-side decoder produced: `0xAARRGGBB`, row-major, not
    /// premultiplied. Never compared or hashed: it carries one decoder's
    /// output to one writer, so the array's identity semantics do not matter.
    @SuppressWarnings("ArrayRecordComponent")
    record Argb(int width, int height, int[] pixels) {}

    /// A GIF's first frame.
    static Argb gif(ByteBuffer bytes) {
        try {
            var decoded = GifDecoder.decode(bytes);
            return new Argb(decoded.width(), decoded.height(), decoded.argb());
        } catch (GifFormatException e) {
            throw new ImageDecodeException("these " + bytes.remaining() + " bytes begin like a GIF and are not one", e);
        }
    }

    /// A still WebP.
    ///
    /// Null back from the decoder is "these bytes are not a WebP I can read",
    /// which for a lossless-or-lossy container with an animation form it does
    /// not decode is a normal answer rather than a fault.
    static Argb webp(ByteBuffer bytes) {
        var decoded = Webp.get().decode(bytes);
        if (decoded == null) {
            throw new ImageDecodeException("these " + bytes.remaining()
                    + " bytes begin like a WebP and are not one this decoder reads — an animated WebP is the"
                    + " usual reason, since only a still frame is decoded");
        }
        return new Argb(decoded.width(), decoded.height(), decoded.pixels());
    }

    /// What a refusal from the rasterizer's codecs says.
    static ImageDecodeException refused(ByteBuffer bytes, BlendException cause) {
        // Translated rather than propagated. An application catching a failed
        // paste must not have to name a type from `:natives` to do it.
        return new ImageDecodeException(
                "these " + bytes.remaining() + " bytes are not an image any codec here (PNG, JPEG, QOI,"
                        + " GIF, WebP) recognises",
                cause);
    }

    /// Decodes `bytes` into the memory `target` hands back for the decoded
    /// size; see [Image#decodeInto].
    static PhysicalSize into(ByteBuffer bytes, ChannelOrder order, Function<PhysicalSize, PixelTarget> target) {
        var argb = switch (ImageFormat.of(bytes)) {
            case GIF -> gif(bytes);
            case WEBP -> webp(bytes);
            case PNG, JPEG, QOI, UNKNOWN -> null;
        };
        if (argb != null) {
            var size = new PhysicalSize(argb.width(), argb.height());
            write(argb, order, targetFor(size, target));
            return size;
        }
        try (var decoded = BlendDecodedImage.decode(bytes)) {
            var size = new PhysicalSize(decoded.width(), decoded.height());
            var into = targetFor(size, target);
            decoded.copyInto(into.pixels(), into.stride(), order.swapsRedAndBlue());
            return size;
        } catch (BlendException e) {
            throw refused(bytes, e);
        }
    }

    /// What `target` answers for `size`, checked before a byte is written.
    private static PixelTarget targetFor(PhysicalSize size, Function<PhysicalSize, PixelTarget> target) {
        var into = Objects.requireNonNull(target.apply(size), "the target function returned no memory");
        into.requireRoomFor(size);
        return into;
    }

    /// Writes `argb` premultiplied into `into`, a row at a time through one
    /// reused `int[]`.
    ///
    /// Each row goes in as one bulk put through a little-endian view, so
    /// `0xAARRGGBB` lands as `B G R A` whatever the platform's own order.
    private static void write(Argb argb, ChannelOrder order, PixelTarget into) {
        var pixels = into.pixels();
        var base = pixels.position();
        var row = new int[argb.width()];
        var swap = order.swapsRedAndBlue();
        for (var y = 0; y < argb.height(); y++) {
            for (var x = 0; x < row.length; x++) {
                var pixel = Image.premultiply(argb.pixels()[y * row.length + x]);
                row[x] = swap ? swapRedAndBlue(pixel) : pixel;
            }
            pixels.slice(base + y * into.stride(), row.length * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asIntBuffer()
                    .put(0, row);
        }
    }

    /// `0xAARRGGBB` with red and blue exchanged.
    static int swapRedAndBlue(int pixel) {
        return (pixel & 0xFF00FF00) | ((pixel >>> 16) & 0xFF) | ((pixel & 0xFF) << 16);
    }
}
