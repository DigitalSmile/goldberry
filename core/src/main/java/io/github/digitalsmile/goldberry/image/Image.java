package io.github.digitalsmile.goldberry.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import io.github.digitalsmile.goldberry.image.png.PngEncoder;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendDecodedImage;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;

/// A decoded image: pixels, a size, and nothing that has to be closed.
///
/// ## A value, not a handle
///
/// The two `:natives` leaks that were closed before this one taught the
/// difference (ADR-0277, ADR-0282): a *value* can be mirrored into the toolkit's
/// own vocabulary, and a *handle* cannot, because somebody has to own it and say
/// when it dies. So an image is a value. The decoder allocates, its pixels are
/// copied into a [PixelBuffer] Java owns, and its handle is destroyed before
/// [#decode(byte[])] returns — which is why there is no `close()` here and why
/// this class is not `AutoCloseable`, as the proposal for it assumed it would
/// have to be. The pixels are a direct [ByteBuffer] and the collector owns them,
/// like every other buffer in the toolkit.
///
/// The cost of that choice is one copy per decode, paid once. What it buys is an
/// image that can be put in a field, a record, a cache or a document model
/// without a lifetime travelling alongside it.
///
/// ## The pixels
///
/// **Premultiplied BGRA**, like every buffer here, whatever the file held: a PNG
/// with no alpha channel is converted at decode rather than asked about at every
/// blit. [#argb(int, int)] undoes the premultiplication so that a caller reading
/// one pixel reads the `0xAARRGGBB` the rest of the toolkit is written in.
///
/// ## Drawing one
///
/// [Frame#drawImage(Image, double,
/// double)][io.github.digitalsmile.goldberry.paint.Frame#drawImage(Image,double,double)]
/// and its overloads. A canvas painter is handed a frame, so an application draws
/// an image exactly where it draws a path (ADR-0283):
///
/// ```java
/// var logo = Image.decode(Files.readAllBytes(file));
/// new Canvas((frame, size) -> frame.drawImage(logo, 0, 0), attributes);
/// ```
public final class Image {

    /// Premultiplied BGRA, tightly packed. The one format the whole toolkit
    /// blits, so a drawn image needs no conversion and no branch.
    private static final PixelFormat FORMAT = PixelFormat.BGRA32_PREMULTIPLIED;

    private final PixelBuffer pixels;

    /// The same buffer, read-only, handed out by [#pixels()].
    ///
    /// Cached rather than made per call: it is what `Frame` asks for on every
    /// draw, and a per-draw duplicate would be an allocation on the paint path for
    /// no purpose. An image is a value and this is what keeps it one — the
    /// alternative is handing out the writable buffer and asking callers not to
    /// write to it.
    private final PixelBuffer readable;

    private Image(PixelBuffer pixels) {
        this.pixels = pixels;
        this.readable = pixels.asReadOnly();
    }

    /// Decodes PNG, JPEG or QOI bytes.
    ///
    /// The format comes from the bytes, not from a name or an argument, so a file
    /// with the wrong extension decodes anyway and a `Content-Type` nobody set
    /// does not matter.
    ///
    /// @throws ImageDecodeException if no codec recognises the bytes, or the image
    ///         is malformed
    /// @throws IllegalArgumentException if there are no bytes at all
    public static Image decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return decode(ByteBuffer.wrap(bytes));
    }

    /// [#decode(byte[])] over a buffer, whose position and limit are honoured.
    public static Image decode(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!bytes.hasRemaining()) {
            throw new IllegalArgumentException("there is nothing to decode: no bytes were given");
        }
        // The decoder's allocation lives exactly as long as this try block. That
        // is the whole of the exception to "Goldberry never asks Blend2D to
        // allocate pixels" (ADR-0031, ADR-0283): only the decoder knows how big
        // the image is, so it allocates, and the pixels are Java's again one
        // statement later.
        try (var decoded = BlendDecodedImage.decode(bytes)) {
            var size = new PhysicalSize(decoded.width(), decoded.height());
            var buffer = PixelBuffer.allocate(size, FORMAT);
            decoded.copyInto(buffer.pixels(), buffer.stride());
            return new Image(buffer);
        } catch (BlendException e) {
            // Translated here rather than propagated. An application catching a
            // failed paste must not have to name a type from `:natives` to do it
            // — that is the leak this whole family of changes is about.
            throw new ImageDecodeException(
                    "these " + bytes.remaining() + " bytes are not an image any built-in codec (PNG, JPEG, QOI)"
                            + " recognises",
                    e);
        }
    }

    /// Reads a file and decodes it.
    ///
    /// @throws UncheckedIOException if the file cannot be read — which is a
    ///         different failure from its contents not being an image, and is
    ///         reported as a different type
    /// @throws ImageDecodeException if the bytes are not an image
    public static Image decode(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            return decode(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    /// An image built from `0xAARRGGBB` pixels, row-major, **not** premultiplied.
    ///
    /// The packing every colour in the toolkit is written in — a CSS colour, a
    /// `fillPath` argument, an `argb()` read back from here. The premultiplication
    /// the buffer needs happens here, once, so that nobody has to remember that
    /// `0x80FFFFFF` and `0x80808080` are the same colour told two different ways.
    ///
    /// What it is for: an image an application computed rather than loaded — a
    /// generated thumbnail, a QR code, a test fixture — and the way to reach
    /// [#encodePng()] without a decoder having been involved at all.
    ///
    /// @param argb `width * height` pixels
    /// @throws IllegalArgumentException if the size is not positive, or the array
    ///         is not exactly that many pixels
    public static Image ofArgb(int width, int height, int[] argb) {
        Objects.requireNonNull(argb, "argb");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "an image needs a positive size, and " + width + "x" + height + " is not");
        }
        var expected = Math.multiplyExact(width, height);
        if (argb.length != expected) {
            throw new IllegalArgumentException("a " + width + "x" + height + " image is " + expected + " pixels, and "
                    + argb.length + " were given");
        }
        var buffer = PixelBuffer.allocate(new PhysicalSize(width, height), FORMAT);
        var pixels = buffer.pixels();
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                pixels.putInt(y * buffer.stride() + x * 4, premultiply(argb[y * width + x]));
            }
        }
        return new Image(buffer);
    }

    /// An image over pixels somebody else rasterized.
    ///
    /// **The buffer is handed over, not borrowed.** Nothing is copied, so a caller
    /// that writes to it afterwards is writing into an image that presents itself
    /// as a value — pass [PixelBuffer#asReadOnly()] if that has to be impossible
    /// rather than merely agreed.
    ///
    /// What it is for is a frame that has already been painted: an offscreen
    /// render hands its buffer over here rather than copying a megabyte to say the
    /// same thing (ADR-0284). An application that rasterized something itself can
    /// do the same.
    ///
    /// @throws IllegalArgumentException if the buffer is not premultiplied BGRA —
    ///         the one format the toolkit blits — or has no pixels in it
    public static Image of(PixelBuffer pixels) {
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.format() != FORMAT) {
            throw new IllegalArgumentException("an image is " + FORMAT + " and this buffer is " + pixels.format()
                    + "; converting one silently would be a copy nobody asked for");
        }
        if (pixels.size().isEmpty()) {
            throw new IllegalArgumentException("an image needs a positive size, and " + pixels.size() + " has none");
        }
        return new Image(pixels);
    }

    /// The size in pixels — the image's own, which is not a logical size: a
    /// 64&times;64 icon is 64 device pixels whatever display it is shown on, and
    /// how big it *looks* is what `drawImage` is told.
    public PhysicalSize size() {
        return pixels.size();
    }

    public int width() {
        return pixels.size().width();
    }

    public int height() {
        return pixels.size().height();
    }

    /// The whole image as a rectangle — the default source rectangle of a blit,
    /// and the thing a crop is checked against.
    public PhysicalRect bounds() {
        return PhysicalRect.of(pixels.size());
    }

    /// One pixel as `0xAARRGGBB`, **not** premultiplied.
    ///
    /// The inverse of what [#ofArgb] does, and not exactly: a translucent pixel
    /// loses precision going into a premultiplied buffer and cannot get it back,
    /// so a round trip is exact for opaque and fully transparent pixels and within
    /// a level or so otherwise. That is a property of premultiplied storage rather
    /// than of this method, and it is why the toolkit's own colours are the
    /// unpremultiplied ones.
    ///
    /// @throws IndexOutOfBoundsException if the coordinates are outside the image
    public int argb(int x, int y) {
        if (x < 0 || y < 0 || x >= width() || y >= height()) {
            throw new IndexOutOfBoundsException(
                    "(" + x + ", " + y + ") is outside a " + width() + "x" + height() + " image");
        }
        return unpremultiply(readable.pixels().getInt(y * pixels.stride() + x * 4));
    }

    /// The pixels, as a read-only view: premultiplied BGRA, at [PixelBuffer#stride()].
    ///
    /// Public because drawing an image happens in another package and an offscreen
    /// render will happen in a third. Read-only because this is a value — there is
    /// no operation on an image that mutates it, and a writable view handed out
    /// would make that a convention instead of a fact.
    public PixelBuffer pixels() {
        return readable;
    }

    /// This image as PNG bytes.
    ///
    /// 8-bit RGBA, not interlaced, one `IDAT` — see [PngEncoder] for why the
    /// encoder is Java's rather than the rasterizer's.
    public byte[] encodePng() {
        return PngEncoder.encode(this);
    }

    /// Premultiplies an `0xAARRGGBB` colour into the buffer's own form.
    ///
    /// `(c * a + 127) / 255` rather than `c * a / 255`: the second truncates, so
    /// mid-grey at half alpha comes out a level darker and a gradient built this
    /// way bands visibly against the same gradient drawn by the rasterizer.
    private static int premultiply(int argb) {
        var a = argb >>> 24;
        if (a == 0xFF) {
            return argb;
        }
        if (a == 0) {
            // Fully transparent, and the RGB goes with it. Keeping the colour
            // would be harmless here and is not worth a branch elsewhere.
            return 0;
        }
        var r = (argb >> 16) & 0xFF;
        var g = (argb >> 8) & 0xFF;
        var b = argb & 0xFF;
        return a << 24 | ((r * a + 127) / 255) << 16 | ((g * a + 127) / 255) << 8 | (b * a + 127) / 255;
    }

    /// The inverse, as far as an inverse exists.
    private static int unpremultiply(int premultiplied) {
        var a = premultiplied >>> 24;
        if (a == 0xFF) {
            return premultiplied;
        }
        if (a == 0) {
            return 0;
        }
        var r = (premultiplied >> 16) & 0xFF;
        var g = (premultiplied >> 8) & 0xFF;
        var b = premultiplied & 0xFF;
        return a << 24
                | Math.min(255, (r * 255 + a / 2) / a) << 16
                | Math.min(255, (g * 255 + a / 2) / a) << 8
                | Math.min(255, (b * 255 + a / 2) / a);
    }

    @Override
    public String toString() {
        return "Image[" + width() + "x" + height() + "]";
    }
}
