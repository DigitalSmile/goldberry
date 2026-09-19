package io.github.digitalsmile.goldberry.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.digitalsmile.goldberry.image.gif.GifDecoder;
import io.github.digitalsmile.goldberry.image.gif.GifFormatException;
import io.github.digitalsmile.goldberry.image.png.PngEncoder;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendDecodedImage;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendImage;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendScaledImage;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendImageScaleFilter;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;
import io.github.digitalsmile.goldberry.natives.webp.Webp;
import io.github.digitalsmile.goldberry.render.Clipboard;
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
/// ## Off a clipboard, and onto one
///
/// [#fromClipboard] and [#toClipboard] — a pasted screenshot is the most common
/// way anything reaches a board, and a copied picture is how it leaves. They are
/// here rather than on [Clipboard] because a clipboard is bytes and a MIME type:
/// a backend implementing one should not have to know what a PNG is (ADR-0286).
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

    /// Decodes PNG, JPEG, QOI, GIF or WebP bytes.
    ///
    /// The format comes from the bytes, not from a name or an argument, so a file
    /// with the wrong extension decodes anyway and a `Content-Type` nobody set
    /// does not matter.
    ///
    /// **Five formats, three codecs.** PNG, JPEG and QOI are the rasterizer's
    /// own. GIF and WebP are not — the rasterizer is compiled without them — so
    /// the bytes are sniffed first and routed: a WebP goes to libwebp, which is
    /// linked into the same native library, and a GIF to
    /// [GifDecoder], which is Java. Why the two differ is
    /// [ADR-0329]'s subject: VP8 is a video codec and GIF is nine pages
    /// (`docs/gaps.md` G35a).
    ///
    /// An **animated** GIF decodes to its first frame. See [GifDecoder].
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
        // Sniffed before anything else, because two of the five formats are not
        // the rasterizer's and it would refuse them (`docs/gaps.md` G35a,
        // [ADR-0329]). The magic bytes, never a file name: that is what
        // "the format comes from the bytes" means.
        var format = ImageFormat.of(bytes);
        if (format == ImageFormat.GIF) {
            return decodeGif(bytes);
        }
        if (format == ImageFormat.WEBP) {
            return decodeWebp(bytes);
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
                    "these " + bytes.remaining() + " bytes are not an image any codec here (PNG, JPEG, QOI,"
                            + " GIF, WebP) recognises",
                    e);
        }
    }

    /// A GIF, through the toolkit's own decoder.
    ///
    /// The exception is translated here for [BlendException]'s reason: an
    /// application catching a failed paste must not have to name which codec
    /// refused it.
    private static Image decodeGif(ByteBuffer bytes) {
        try {
            var decoded = GifDecoder.decode(bytes);
            return ofArgb(decoded.width(), decoded.height(), decoded.argb());
        } catch (GifFormatException e) {
            throw new ImageDecodeException("these " + bytes.remaining() + " bytes begin like a GIF and are not one", e);
        }
    }

    /// A WebP, through libwebp.
    ///
    /// Null back from the decoder is "these bytes are not a WebP I can read",
    /// which for a lossless-or-lossy container with an animation form it does not
    /// decode is a normal answer rather than a fault.
    private static Image decodeWebp(ByteBuffer bytes) {
        var decoded = Webp.get().decode(bytes);
        if (decoded == null) {
            throw new ImageDecodeException("these " + bytes.remaining()
                    + " bytes begin like a WebP and are not one this decoder reads — an animated WebP is the"
                    + " usual reason, since only a still frame is decoded");
        }
        return ofArgb(decoded.width(), decoded.height(), decoded.pixels());
    }

    /// Decodes every frame — [ADR-0382].
    ///
    /// **Every image is an animation**, and most are an animation of one frame:
    /// a PNG, a JPEG, a QOI, a WebP and a GIF with one frame in it all come back
    /// as a still. What this adds over [#decode] is the rest of an animated
    /// GIF's frames, each composited under the file's own disposal rules, with
    /// the delay it declares and the number of times it asks to be played.
    ///
    /// An **animated WebP** is every frame too, since [ADR-0385] linked
    /// `webpdemux`: libwebp composites each canvas itself, so the disposal model
    /// GIF needs in Java is upstream's there.
    ///
    /// @throws ImageDecodeException if no codec recognises the bytes, or the
    ///         image is malformed
    public static io.github.digitalsmile.goldberry.image.anim.Animation decodeAnimation(ByteBuffer bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (ImageFormat.of(bytes) == ImageFormat.WEBP) {
            var animated = Webp.get().decodeAnimation(bytes);
            if (animated == null) {
                // A still WebP, which is most of them: the container is the same
                // and only an animated one has frames to walk (ADR-0385).
                return io.github.digitalsmile.goldberry.image.anim.Animation.still(decode(bytes));
            }
            var frames = new java.util.ArrayList<io.github.digitalsmile.goldberry.image.anim.Animation.Frame>(
                    animated.frames().size());
            var durations = animated.durations();
            for (var i = 0; i < animated.frames().size(); i++) {
                var frame = animated.frames().get(i);
                frames.add(new io.github.digitalsmile.goldberry.image.anim.Animation.Frame(
                        ofArgb(frame.width(), frame.height(), frame.pixels()), durations[i]));
            }
            return new io.github.digitalsmile.goldberry.image.anim.Animation(frames, animated.loopCount());
        }
        if (!GifDecoder.looksLikeGif(bytes)) {
            return io.github.digitalsmile.goldberry.image.anim.Animation.still(decode(bytes));
        }
        try {
            var sequence = GifDecoder.decodeAll(bytes);
            var frames = new java.util.ArrayList<io.github.digitalsmile.goldberry.image.anim.Animation.Frame>(
                    sequence.frames().size());
            for (var frame : sequence.frames()) {
                var decoded = frame.image();
                frames.add(new io.github.digitalsmile.goldberry.image.anim.Animation.Frame(
                        ofArgb(decoded.width(), decoded.height(), decoded.argb()), frame.delayMillis()));
            }
            return new io.github.digitalsmile.goldberry.image.anim.Animation(frames, sequence.loopCount());
        } catch (GifFormatException e) {
            throw new ImageDecodeException(String.valueOf(e.getMessage()), e);
        }
    }

    /// The same, from an array.
    public static io.github.digitalsmile.goldberry.image.anim.Animation decodeAnimation(byte[] bytes) {
        return decodeAnimation(ByteBuffer.wrap(Objects.requireNonNull(bytes, "bytes")));
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

    /// The MIME type an image is put on a clipboard as, and the first one looked
    /// for when reading one off.
    ///
    /// PNG because it is lossless, carries an alpha channel and is what every
    /// desktop and every browser offers — a screenshot copied out of anything
    /// arrives as this.
    public static final String PNG_MIME = "image/png";

    /// The types [#fromClipboard] will try, in order.
    ///
    /// Only the ones the decoder can actually read (ADR-0283): a clipboard
    /// advertising a type this toolkit has no codec for is a paste it cannot do,
    /// and saying so by finding nothing is better than throwing from inside a
    /// decoder that was handed bytes it does not know.
    ///
    /// `image/webp` and `image/gif` joined the list the day the two codecs did
    /// ([ADR-0329]) — which is the whole of what a set like this is for: it is a
    /// statement about what can be decoded, so it moves when that does.
    private static final List<String> CLIPBOARD_MIMES =
            List.of(PNG_MIME, "image/jpeg", "image/jpg", "image/qoi", "image/webp", "image/gif");

    /// Whether `clipboard` holds an image this toolkit can decode.
    ///
    /// The cheap question — it asks what the platform has already advertised and
    /// does not fetch anything.
    public static boolean onClipboard(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        return CLIPBOARD_MIMES.stream().anyMatch(clipboard::has);
    }

    /// The image on `clipboard`, or empty when it holds none this toolkit can
    /// read.
    ///
    /// **This is a paste**, so it is a round trip to whichever application owns
    /// the clipboard — see [Clipboard#read]. Bytes that are advertised and then
    /// fail to decode raise [ImageDecodeException] rather than coming back empty:
    /// the clipboard said it had a PNG, and an application offering to paste
    /// should be told that it lied.
    public static Optional<Image> fromClipboard(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        for (var mime : CLIPBOARD_MIMES) {
            if (!clipboard.has(mime)) {
                continue;
            }
            var bytes = clipboard.read(mime);
            if (bytes.length > 0) {
                return Optional.of(decode(bytes));
            }
        }
        return Optional.empty();
    }

    /// Puts this image on `clipboard` as a PNG, replacing whatever was there.
    ///
    /// Encoding happens **now** rather than when somebody pastes, which is a
    /// choice: the platform's own offer is lazy (ADR-0286), and an image that
    /// encoded on demand would hold a reference to itself for as long as it was
    /// on the clipboard and encode again for every paste. A UI-sized PNG is
    /// milliseconds and a copy is a deliberate act.
    ///
    /// @return whether the platform accepted it
    public boolean toClipboard(Clipboard clipboard) {
        Objects.requireNonNull(clipboard, "clipboard");
        return clipboard.write(PNG_MIME, encodePng());
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

    /// A **copy** of this image, resampled to `width` × `height` — [ADR-0428].
    ///
    /// ## Not the same thing as drawing one smaller
    ///
    /// Scaling for the screen happens at the blit, where the destination size is
    /// known and nothing is kept:
    /// `frame.drawImage(image, x, y, width, height)` resamples on its way onto
    /// the surface and produces no pixels anybody owns. This produces pixels —
    /// a thumbnail to write to a file, an icon resampled once and drawn a
    /// hundred times, an over-sized paste cut down before it goes into a
    /// document. If the answer is going straight onto a frame, this is the
    /// wrong method and costs a buffer for nothing.
    ///
    /// ## The filter
    ///
    /// [Resampling#LANCZOS], which is the right answer for the operation this
    /// exists for: a thumbnail is a **downscale**, and a downscale wants as much
    /// of the source averaged in as possible. It is the wrong answer for
    /// doubling a 16×16 icon, which wants [Resampling#NEAREST] — see
    /// [Resampling] for why the choice is a real one rather than a quality knob,
    /// and use [#scaled(int, int, Resampling)] to make it.
    ///
    /// Asking for the size it already is returns **this image**, not a copy: an
    /// image is a value, so there is nothing a copy could be used for that this
    /// cannot.
    ///
    /// @throws IllegalArgumentException if the size is not positive
    /// @throws ImageScaleException if the rasterizer refuses — running out of
    ///         memory for a size a caller computed is the ordinary reason
    public Image scaled(int width, int height) {
        return scaled(width, height, Resampling.LANCZOS);
    }

    /// [#scaled(int, int)] with the filter said out loud.
    ///
    /// @param filter how the pixels that are not there are invented
    /// @throws IllegalArgumentException if the size is not positive
    /// @throws ImageScaleException if the rasterizer refuses
    public Image scaled(int width, int height, Resampling filter) {
        Objects.requireNonNull(filter, "filter");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "a scaled image needs a positive size, and " + width + "x" + height + " is not");
        }
        if (width == width() && height == height()) {
            return this;
        }
        // Two native objects and neither outlives the statement. The source is a
        // view over pixels Java already owns, which is the rule everywhere in
        // this toolkit; the destination is the exception, because
        // `bl_image_scale` resizes and allocates the destination itself and has
        // no form that writes into a buffer somebody else owns (ADR-0283's
        // exception, widened by ADR-0428).
        try (var source = BlendImage.wrapping(pixels.pixels(), width(), height(), pixels.stride());
                var scaled = BlendScaledImage.scale(source, width, height, toBlend(filter))) {

            var buffer = PixelBuffer.allocate(new PhysicalSize(width, height), FORMAT);
            scaled.copyInto(buffer.pixels(), buffer.stride());
            return new Image(buffer);
        } catch (BlendException e) {
            // Translated here for the reason a failed decode is: an application
            // must not have to name a type from `:natives` to catch this.
            throw new ImageScaleException(
                    "the rasterizer would not resample this " + width() + "x" + height() + " image to " + width + "x"
                            + height,
                    e);
        }
    }

    /// The rasterizer's name for a [Resampling].
    ///
    /// A switch and not an ordinal: the two enums agree today and a `:core` type
    /// must not be pinned to the order of a `:natives` one.
    private static BlendImageScaleFilter toBlend(Resampling filter) {
        return switch (filter) {
            case NEAREST -> BlendImageScaleFilter.NEAREST;
            case BILINEAR -> BlendImageScaleFilter.BILINEAR;
            case BICUBIC -> BlendImageScaleFilter.BICUBIC;
            case LANCZOS -> BlendImageScaleFilter.LANCZOS;
        };
    }

    /// This image as PNG bytes.
    ///
    /// 8-bit RGBA, not interlaced, one `IDAT` — see [PngEncoder] for why the
    /// encoder is Java's rather than the rasterizer's.
    public byte[] encodePng() {
        return PngEncoder.encode(this);
    }

    /// This image as **lossless** WebP bytes — [ADR-0385].
    ///
    /// The lossless path, because that is what a picture this toolkit drew wants:
    /// VP8's transform is worst at flat colour and hard edges, which is what a
    /// user interface is made of. A lossless WebP of a screen is typically a
    /// third of the PNG.
    ///
    /// @throws ImageEncodeException if libwebp refuses — an image wider or taller
    ///         than WebP's 16383-pixel limit is the ordinary reason
    public byte[] encodeWebp() {
        return encodeWebp(-1);
    }

    /// The same, at a **lossy** quality — for a photograph, which is the case the
    /// lossy path is good at.
    ///
    /// @param quality `0..100`; a negative number asks for the lossless path,
    ///                which is what [#encodeWebp()] passes
    /// @throws ImageEncodeException if libwebp refuses
    public byte[] encodeWebp(float quality) {
        var pixels = new int[Math.multiplyExact(width(), height())];
        for (var y = 0; y < height(); y++) {
            for (var x = 0; x < width(); x++) {
                pixels[y * width() + x] = argb(x, y);
            }
        }
        var encoded = Webp.get().encode(pixels, width(), height(), quality);
        if (encoded == null) {
            throw new ImageEncodeException("libwebp would not encode this " + width() + "x" + height()
                    + " image. WebP's limit is 16383 pixels on a side; a picture larger than that is a PNG.");
        }
        return encoded;
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
