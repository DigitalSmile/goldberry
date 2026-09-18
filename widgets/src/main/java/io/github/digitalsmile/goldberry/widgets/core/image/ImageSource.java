package io.github.digitalsmile.goldberry.widgets.core.image;

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

import io.github.digitalsmile.goldberry.image.Image;

/// Where an [ImageView]'s pixels come from — §1's "path, classpath, bytes, or
/// async supplier", plus an image already in hand.
///
/// Sealed, because the loader has to know which of these can be read on the UI
/// thread for free (a [Decoded] one) and which is a file read and a decode that
/// belongs on a virtual thread (every other). Each answers a [#key()] the shared
/// cache is keyed on, so two views of the same file decode it once.
///
/// **An SVG is not decoded here.** §1 routes `image/svg+xml` to a
/// `goldberry-vector` module that does not exist; an SVG source fails with the
/// decoder's message and the view shows its error state (ADR-0358).
public sealed interface ImageSource {

    /// What the cache calls this source. Equal keys are the same pixels.
    String key();

    /// Reads and decodes, blocking. Called off the UI thread by the loader.
    ///
    /// @throws UncheckedIOException when the bytes cannot be read
    /// @throws io.github.digitalsmile.goldberry.image.ImageDecodeException when
    ///         they are not an image
    Image load();

    /// A file on disk, read when the view first needs it.
    static ImageSource file(Path path) {
        return new File(path);
    }

    /// A resource beside `anchor`, the way
    /// [io.github.digitalsmile.goldberry.css.Stylesheet#resource] reads one: relative
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

    /// A path in markup: `classpath:` names a resource on `loader`, anything else
    /// is a file.
    static ImageSource parse(String src, ClassLoader loader) {
        Objects.requireNonNull(src, "src");
        if (src.startsWith(Resource.SCHEME)) {
            var name = src.substring(Resource.SCHEME.length());
            return new Resource(null, name.startsWith("/") ? name.substring(1) : name, loader);
        }
        return new File(Path.of(src));
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
        /// file that is sitting right where they put it, which is what happened
        /// to the showcase ([ADR-0395]).
        /// [io.github.digitalsmile.goldberry.css.Stylesheet#resource] had already
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

        @Override
        public String key() {
            return "decoded:" + System.identityHashCode(image);
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
}
