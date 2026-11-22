package dev.goldberry.vector;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.image.ScalableImage;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.vector.document.RootTag;
import dev.goldberry.vector.thorvg.ThorvgEngine;
import dev.goldberry.vector.thorvg.ThorvgPicture;
import dev.goldberry.vector.thorvg.VectorLibrary;
import dev.goldberry.widgets.core.image.Fit;

/// An SVG document, parsed once and drawn at any size.
///
/// ```java
/// try (var icon = Svg.parse(Files.readString(file), 0xFF2E3440)) {
///     Image small = icon.render(new PhysicalSize(24, 24));
///     Image large = icon.render(new PhysicalSize(96, 48), Fit.CONTAIN);
/// }
/// ```
///
/// ThorVG parses the document and its CPU rasterizer draws it, straight into
/// the pixels of the [Image] that comes back: premultiplied BGRA, the one format
/// the toolkit blits. Each [#render] is a raster of exactly the size asked for,
/// so a document is sharp at every size and every display scale, which is what
/// SVG is for. An image is a value; this is a handle, so close it, or let the
/// collector release it.
///
/// An application rarely needs one: with this module on the path,
/// `Image.decode` reads SVG bytes into an image that carries its document, and
/// every place an image is drawn draws it at the device pixels it covers.
///
/// ## `currentColor`
///
/// A document drawn in `currentColor` takes the colour given to [#parse(String,
/// int)], as if it were the CSS `color` of the box it is drawn in: given to the
/// root `<svg>` before ThorVG reads it, so a monochrome icon follows the theme. A
/// root that names its own `color` keeps it, and a document that names its own
/// colours keeps them. Without one, `currentColor` is black.
///
/// ## What is drawn, and what is not
///
/// Shapes, paths, strokes, gradients, patterns, `<use>`, clips, masks, opacity
/// and the `style` attribute and element, as ThorVG reads them. Not `<text>`: the
/// library is built without fonts. Nothing outside the document is ever read: an
/// image embedded as a `data:` URI (PNG or JPEG) is drawn, and an `href` to a
/// file or a URL draws nothing. There is no script, animation or
/// `<foreignObject>`.
///
/// ## Limits
///
/// A document over [#MAX_DOCUMENT_BYTES] is refused before it is parsed, and a
/// raster over [#MAX_SIDE] on a side or [#MAX_PIXELS] in all is refused before
/// it is allocated: a document from a mail or a download can name any size it
/// likes.
///
/// ## Threads
///
/// Safe to share. Every parse, draw and release in the process takes one lock,
/// because ThorVG's own state is process-wide, so two threads drawing two
/// documents take turns.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
public final class Svg implements ScalableImage, AutoCloseable {

    /// The largest document parsed: 16 MiB.
    public static final int MAX_DOCUMENT_BYTES = 16 << 20;

    /// The longest side of a raster: 8192 pixels, as for a decoded video picture.
    public static final int MAX_SIDE = 8192;

    /// The most pixels in a raster: 64 million, 256 MB of pixels.
    public static final long MAX_PIXELS = 64_000_000L;

    /// `currentColor` when none is given: CSS's initial `color`.
    public static final int DEFAULT_CURRENT_COLOR = 0xFF000000;

    private final RootTag root;
    private final int currentColor;
    private final LogicalSize naturalSize;
    private final Optional<ViewBox> viewBox;
    private final boolean stretches;
    private final Handle handle;
    private final Cleaner.Cleanable cleanable;

    private Svg(RootTag root, int currentColor, ThorvgPicture picture) {
        this.root = root;
        this.currentColor = currentColor;
        this.naturalSize = new LogicalSize(picture.width(), picture.height());
        this.viewBox = root.viewBox();
        this.stretches = root.stretches();
        this.handle = new Handle(picture);
        this.cleanable = ThorvgEngine.whenUnreachable(this, handle);
    }

    /// Parses the bytes from `bytes`' position to its limit, without moving it,
    /// with `currentColor` black.
    ///
    /// @throws ImageDecodeException     when the bytes are not an SVG document
    ///                                  ThorVG can read, or are over
    ///                                  [#MAX_DOCUMENT_BYTES]
    /// @throws IllegalArgumentException when there are no bytes at all
    /// @throws IllegalStateException    when `libgoldberry-vector` is not
    ///                                  available, naming why
    public static Svg parse(ByteBuffer bytes) {
        return parse(bytes, DEFAULT_CURRENT_COLOR);
    }

    /// [#parse(ByteBuffer)] with `currentColor` the colour `argb`, `0xAARRGGBB`
    /// and not premultiplied, as every colour in the toolkit is written.
    public static Svg parse(ByteBuffer bytes, int currentColor) {
        Objects.requireNonNull(bytes, "bytes");
        var document = new byte[requireSize(bytes.remaining())];
        bytes.duplicate().get(document);
        return parse(document, currentColor);
    }

    /// Parses a document held as text, with `currentColor` black.
    ///
    /// @throws ImageDecodeException     as [#parse(ByteBuffer)]
    /// @throws IllegalArgumentException when the text is empty
    public static Svg parse(String document) {
        return parse(document, DEFAULT_CURRENT_COLOR);
    }

    /// [#parse(String)] with `currentColor` the colour `argb`.
    public static Svg parse(String document, int currentColor) {
        Objects.requireNonNull(document, "document");
        var bytes = document.getBytes(StandardCharsets.UTF_8);
        requireSize(bytes.length);
        return parse(bytes, currentColor);
    }

    private static Svg parse(byte[] document, int currentColor) {
        var root = RootTag.find(document);
        var colored = root.withCurrentColor(currentColor);
        var picture = ThorvgEngine.call(library -> ThorvgPicture.load(library, colored));
        return new Svg(root, currentColor, picture);
    }

    private static int requireSize(int length) {
        if (length == 0) {
            throw new IllegalArgumentException("there is nothing to parse: no bytes were given");
        }
        if (length > MAX_DOCUMENT_BYTES) {
            throw new ImageDecodeException("an SVG document of " + length + " bytes is over the " + MAX_DOCUMENT_BYTES
                    + " this module parses");
        }
        return length;
    }

    /// The size the document gives itself, in its user units, which are CSS
    /// pixels: its `width` and `height`, or its view box's where it names none,
    /// or the bounds of what it draws where it names neither.
    @Override
    public LogicalSize naturalSize() {
        return naturalSize;
    }

    /// The document's `viewBox`, or empty when it has none.
    public Optional<ViewBox> viewBox() {
        return viewBox;
    }

    /// The colour `currentColor` was given, `0xAARRGGBB`.
    public int currentColor() {
        return currentColor;
    }

    /// The document drawn into a viewport of `size` pixels, as a browser draws an
    /// SVG image of that size: its view box placed in the viewport by its own
    /// `preserveAspectRatio`, centred and whole unless it says otherwise, and
    /// stretched only when it says `none`. A document with no view box is
    /// stretched to the size.
    ///
    /// @throws IllegalArgumentException when `size` is empty, over [#MAX_SIDE] on
    ///                                  a side or over [#MAX_PIXELS]
    /// @throws IllegalStateException    when this document is closed
    @Override
    public Image render(PhysicalSize size) {
        var buffer = allocate(size);
        ThorvgEngine.call(library -> {
            var picture = handle.open();
            if (needsOwnViewport(size)) {
                drawInOwnViewport(library, buffer);
            } else {
                picture.drawInto(library, buffer, 0, 0, size.width(), size.height());
            }
            return buffer;
        });
        return Image.of(buffer);
    }

    /// The document placed in a raster of `size` pixels by `fit`:
    ///
    /// - [Fit#CONTAIN] the whole document as large as fits, centred, the rest of
    ///   the raster transparent;
    /// - [Fit#COVER] the raster filled, the document centred and cut to its
    ///   shape;
    /// - [Fit#NONE] one user unit to a pixel, centred;
    /// - [Fit#FILL] the raster as the document's viewport: [#render(PhysicalSize)],
    ///   where the document's own `preserveAspectRatio` decides whether it is
    ///   stretched.
    ///
    /// @throws IllegalArgumentException as [#render(PhysicalSize)]
    /// @throws IllegalStateException    when this document is closed
    public Image render(PhysicalSize size, Fit fit) {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(fit, "fit");
        double across = size.width() / (double) naturalSize.width();
        double down = size.height() / (double) naturalSize.height();
        return switch (fit) {
            case FILL -> render(size);
            case CONTAIN -> placed(size, Math.min(across, down));
            case COVER -> placed(size, Math.max(across, down));
            case NONE -> placed(size, 1);
        };
    }

    /// The document at `scale` user units to a pixel, centred in `size`.
    private Image placed(PhysicalSize size, double scale) {
        var buffer = allocate(size);
        var width = (float) (naturalSize.width() * scale);
        var height = (float) (naturalSize.height() * scale);
        var x = (size.width() - width) / 2;
        var y = (size.height() - height) / 2;
        ThorvgEngine.call(library -> {
            handle.open().drawInto(library, buffer, x, y, width, height);
            return buffer;
        });
        return Image.of(buffer);
    }

    /// Releases the parsed document. Later renders throw; a second close does
    /// nothing.
    @Override
    public void close() {
        cleanable.clean();
    }

    /// Whether a viewport of `size` needs the document parsed for it: one with a
    /// view box it places rather than stretches, in a viewport whose shape is
    /// not its own. ThorVG resizes a parsed document by stretching it, so a
    /// viewport of another shape is given to the root and ThorVG places the view
    /// box in it by the document's own rules. Within half a pixel on both axes,
    /// the shape is the same and the stretch is not visible.
    boolean needsOwnViewport(PhysicalSize size) {
        if (viewBox.isEmpty() || stretches) {
            return false;
        }
        var w = naturalSize.width();
        var h = naturalSize.height();
        var mismatch = Math.abs((double) size.width() * h - (double) size.height() * w);
        return mismatch >= 0.5 * Math.max(w, h);
    }

    private void drawInOwnViewport(VectorLibrary library, PixelBuffer buffer) {
        var size = buffer.size();
        var replacements = new LinkedHashMap<String, String>();
        replacements.put("width", Integer.toString(size.width()));
        replacements.put("height", Integer.toString(size.height()));
        if (!root.namesItsColor()) {
            replacements.put("color", RootTag.cssColor(currentColor));
        }
        var viewport = ThorvgPicture.load(library, root.with(replacements));
        try {
            viewport.drawInto(library, buffer, 0, 0, size.width(), size.height());
        } finally {
            viewport.release(library);
        }
    }

    private static PixelBuffer allocate(PhysicalSize size) {
        Objects.requireNonNull(size, "size");
        if (size.isEmpty()) {
            throw new IllegalArgumentException("a document is drawn at a positive size, and " + size + " is not one");
        }
        if (size.width() > MAX_SIDE || size.height() > MAX_SIDE || size.pixelCount() > MAX_PIXELS) {
            throw new IllegalArgumentException("a raster of " + size + " is past the largest drawn, " + MAX_SIDE
                    + " on a side and " + MAX_PIXELS + " pixels");
        }
        return PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
    }

    @Override
    public String toString() {
        return "Svg[" + naturalSize.width() + "x" + naturalSize.height()
                + viewBox.map(box -> ", viewBox " + box.x() + " " + box.y() + " " + box.width() + " " + box.height())
                        .orElse("")
                + (handle.isReleased() ? ", closed" : "") + "]";
    }

    /// The picture, and whether it has been released: shared by the document and
    /// its cleaner, which must not refer to the document.
    private static final class Handle implements Runnable {

        private final ThorvgPicture picture;

        /// Guarded by the engine's lock; volatile so that `toString` can read it.
        private volatile boolean released;

        Handle(ThorvgPicture picture) {
            this.picture = picture;
        }

        /// The picture, inside the engine's lock.
        ThorvgPicture open() {
            if (released) {
                throw new IllegalStateException("this SVG document is closed");
            }
            return picture;
        }

        boolean isReleased() {
            return released;
        }

        @Override
        public void run() {
            ThorvgEngine.call(library -> {
                if (!released) {
                    released = true;
                    picture.release(library);
                }
                return picture;
            });
        }
    }
}
