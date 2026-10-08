package dev.goldberry.widgets.core.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.image.Image;
import dev.goldberry.image.ImageAddress;
import dev.goldberry.render.model.PhysicalRect;

/// Where an [ImageView]'s pixels come from: a path, a classpath resource, bytes,
/// an asynchronous supplier, an image already in hand, or a rectangle of any of
/// those.
///
/// Sealed, because the loader has to know which of these can be read on the UI
/// thread for free (a [Decoded] one) and which is a file read and a decode that
/// belongs on a virtual thread (every other). Each answers a [#key()] the shared
/// cache is keyed on, so two views of the same file decode it once.
///
/// **An SVG is not decoded here.** There is no vector decoder; an SVG source
/// fails with the decoder's message and the view shows its error state.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#image).
public sealed interface ImageSource {

    /// What the cache calls this source, or **null for a source with nothing to
    /// remember**. Equal keys are the same pixels.
    ///
    /// A key names the *work*: a path, a resource, a digest of some bytes, a name
    /// the application gave its own supplier. Two views naming the same work share
    /// one decode, and the answer outlives both of them in a process-wide map.
    ///
    /// [Decoded] answers null, and that is the whole reason this is nullable. Its
    /// pixels are already in hand, so there is no decode to share; and a key made
    /// from the object's identity would be *wrong* rather than merely useless —
    /// `System.identityHashCode` is not unique, so two application images could
    /// collide and the second view would be handed the first one's picture. Null
    /// says the cache should not be asked, which is both the cheap answer and the
    /// only correct one.
    @Nullable
    String key();

    /// Reads and decodes, blocking. Called off the UI thread by the loader.
    ///
    /// @throws UncheckedIOException when the bytes cannot be read
    /// @throws dev.goldberry.image.ImageDecodeException when
    ///         they are not an image
    Image load();

    /// A file on disk, read when the view first needs it.
    static ImageSource file(Path path) {
        return new File(path);
    }

    /// A resource beside `anchor`, the way
    /// [dev.goldberry.css.Stylesheet#resource] reads one: relative
    /// to `anchor`'s package unless the name starts with `/`.
    static ImageSource resource(Class<?> anchor, String name) {
        return new Resource(anchor, name);
    }

    /// Encoded bytes already in hand — a download, a paste, a database row.
    /// Copied, so an array the caller reuses does not change a picture.
    static ImageSource bytes(byte[] data) {
        return new Bytes(data.clone());
    }

    /// An image that is already decoded. Drawn on the first frame, with no loading
    /// state at all.
    static ImageSource of(Image image) {
        return new Decoded(image);
    }

    /// An application's own work — a thumbnail generator, an HTTP fetch — run on
    /// a virtual thread.
    ///
    /// @param key      what the cache calls its result; two suppliers with one key
    ///                 are trusted to produce the same image
    /// @param supplier blocks as long as it needs to
    static ImageSource supplied(String key, Supplier<Image> supplier) {
        return new Supplied(key, supplier);
    }

    /// The rectangle `rect` of `sheet`, in the sheet's own pixels: one sprite of
    /// a sheet of them.
    ///
    /// Every region of one sheet shares the sheet's one decode — the shared
    /// loader reads the sheet under its own key and cuts each region out of it —
    /// so seventy sprites on one sheet cost one file read and one decode, not
    /// seventy.
    ///
    /// @throws IllegalArgumentException if `rect` is empty
    static ImageSource region(ImageSource sheet, PhysicalRect rect) {
        return new Region(sheet, rect);
    }

    /// A path in markup: `classpath:` names a resource on `loader`, anything else
    /// is a file. A `#xywh=x,y,width,height` fragment makes it a [#region] of
    /// that sheet.
    ///
    /// @throws IllegalArgumentException for a fragment that is not four whole
    ///         numbers with a positive size
    static ImageSource parse(String src, ClassLoader loader) {
        Objects.requireNonNull(src, "src");
        var address = ImageAddress.parse(src);
        var path = address.path();
        ImageSource whole;
        if (path.startsWith(Resource.SCHEME)) {
            var name = path.substring(Resource.SCHEME.length());
            whole = new Resource(null, name.startsWith("/") ? name.substring(1) : name, loader);
        } else {
            whole = new File(Path.of(path));
        }
        var rect = address.region();
        return rect == null ? whole : new Region(whole, rect);
    }

    /// See [ImageSource#file].
    record File(Path path) implements ImageSource {

        public File {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public String key() {
            return "file:" + path.toAbsolutePath().normalize();
        }

        @Override
        public Image load() {
            return Image.decode(path);
        }
    }

    /// See [ImageSource#resource]. Either an anchor class or a class loader
    /// finds it; markup has no class to anchor on, so it names a loader.
    ///
    /// @param anchor the class the name is relative to, or null
    /// @param name   the resource name
    /// @param loader the loader for an unanchored name, or null
    record Resource(
            @Nullable Class<?> anchor,
            String name,
            @Nullable ClassLoader loader) implements ImageSource {

        static final String SCHEME = "classpath:";

        public Resource {
            Objects.requireNonNull(name, "name");
            if (anchor == null && loader == null) {
                throw new IllegalArgumentException("a resource is found through a class or a class loader");
            }
        }

        Resource(Class<?> anchor, String name) {
            this(Objects.requireNonNull(anchor, "anchor"), name, null);
        }

        @Override
        public String key() {
            return anchor != null ? "resource:" + anchor.getName() + ":" + name : SCHEME + name;
        }

        @Override
        public Image load() {
            try (var in = anchor != null
                    ? anchor.getResourceAsStream(name)
                    : Objects.requireNonNull(loader).getResourceAsStream(name)) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException(absent()));
                }
                return Image.decode(in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /// Why there was no stream — and the two answers are very different.
        ///
        /// A resource inside a named module is **encapsulated**: a file in a
        /// package of one module is invisible to another unless the package is
        /// `opens`, and `exports` does not do it because it governs types rather
        /// than bytes. The reading happens *here*, in the toolkit's module, so an
        /// application that opened its package to `…goldberry.core` — enough for
        /// a stylesheet — still gets nothing for an image.
        ///
        /// Saying "no image resource" for that case sends somebody looking for a
        /// file that is sitting right where they put it.
        /// [dev.goldberry.css.Stylesheet#resource] had already
        /// learned to tell the two apart; this is the same message for pixels.
        private String absent() {
            var here = ImageSource.class.getModule();
            if (anchor != null
                    && anchor.getModule().isNamed()
                    && !anchor.getModule().isOpen(anchor.getPackageName(), here)) {

                return "the image resource \"" + name + "\" at " + key() + " is encapsulated: module "
                        + anchor.getModule().getName() + " does not open " + anchor.getPackageName()
                        + " to " + here.getName() + ", and JPMS encapsulates resources as well as"
                        + " classes. Add `opens " + anchor.getPackageName() + " to " + here.getName()
                        + ";` to its module-info — the file itself may well be there.";
            }
            return "no image resource \"" + name + "\" at " + key();
        }
    }

    /// See [ImageSource#bytes]. Keyed by a digest of the bytes, so equal bytes
    /// from two places share one decode.
    @SuppressWarnings("ArrayRecordComponent") // copied on the way in; equals and hashCode are by content
    record Bytes(byte[] data) implements ImageSource {

        public Bytes {
            Objects.requireNonNull(data, "data");
        }

        @Override
        public String key() {
            try {
                return "sha256:"
                        + HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("a JDK without SHA-256", e);
            }
        }

        @Override
        public Image load() {
            return Image.decode(data);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Bytes(var bytes) && Arrays.equals(data, bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "Bytes[" + data.length + " bytes]";
        }
    }

    /// See [ImageSource#of].
    record Decoded(Image image) implements ImageSource {

        public Decoded {
            Objects.requireNonNull(image, "image");
        }

        /// **Null: an image in hand is not cached.**
        ///
        /// This used to answer `"decoded:" + System.identityHashCode(image)`, which
        /// was wrong twice over. Identity hashes are not unique — the JVM makes no
        /// such promise and does not keep one — so two application images could
        /// share a key, and the second view to ask would be handed the first one's
        /// picture. And an entry under that key put an object the *application*
        /// owns into a cache that lives as long as the process, where it stayed
        /// until enough other pictures pushed it out.
        ///
        /// Neither cost bought anything. The cache exists to stop a file being
        /// read and decoded twice, and there is nothing here to read or decode:
        /// [#load] hands back what it was given.
        @Override
        public @Nullable String key() {
            return null;
        }

        @Override
        public Image load() {
            return image;
        }
    }

    /// See [ImageSource#supplied].
    record Supplied(String key, Supplier<Image> supplier) implements ImageSource {

        public Supplied {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        public Image load() {
            return Objects.requireNonNull(supplier.get(), "the supplier for " + key + " returned no image");
        }
    }

    /// See [ImageSource#region].
    ///
    /// @param sheet where the whole picture comes from
    /// @param rect  the part of it to show, in the sheet's pixels
    record Region(ImageSource sheet, PhysicalRect rect) implements ImageSource {

        public Region {
            Objects.requireNonNull(sheet, "sheet");
            Objects.requireNonNull(rect, "rect");
            if (rect.isEmpty()) {
                throw new IllegalArgumentException("a region needs a positive size, and " + rect + " has none");
            }
        }

        /// The sheet's key and the rectangle, as markup writes it; null when the
        /// sheet has none, because a region of an image in hand is cut from
        /// pixels nobody decoded.
        @Override
        public @Nullable String key() {
            var whole = sheet.key();
            return whole == null ? null : new ImageAddress(whole, rect).toString();
        }

        /// The sheet, read and decoded, and the rectangle cut out of it. The
        /// shared loader does not come here for a region: it asks for the sheet
        /// under the sheet's own key and cuts from that.
        ///
        /// @throws IllegalArgumentException if the rectangle is not inside the
        ///         sheet
        @Override
        public Image load() {
            return sheet.load().cropped(rect);
        }
    }
}
