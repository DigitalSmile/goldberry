package dev.goldberry.vector.view;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// Where an [SvgView]'s document comes from: a file, a classpath resource, text
/// written inline, or bytes in hand.
///
/// A view keeps the document's bytes rather than one drawing of them, because
/// `currentColor` is the view's CSS `color` and a colour that changes (a theme,
/// `:hover`) is the document parsed again with it.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
public sealed interface SvgSource {

    /// What the parsed documents are remembered by: equal keys are the same
    /// document.
    String key();

    /// Reads the bytes, blocking. Called off the UI thread while the toolkit is
    /// running.
    ///
    /// @throws UncheckedIOException when they cannot be read
    byte[] read();

    /// A file on disk, read when the view first needs it.
    static SvgSource file(Path path) {
        return new File(path);
    }

    /// A resource beside `anchor`: relative to its package unless the name starts
    /// with `/`.
    static SvgSource resource(Class<?> anchor, String name) {
        return new Resource(anchor, null, name);
    }

    /// A document written out in full: `<svg …>…</svg>`.
    static SvgSource text(String document) {
        return new Text(document);
    }

    /// Encoded bytes already in hand: a download, a paste, a database row.
    /// Copied, so an array the caller reuses does not change a picture.
    static SvgSource bytes(byte[] document) {
        return new Bytes(document.clone());
    }

    /// A path in markup: `classpath:` names a resource on `loader`, anything else
    /// is a file.
    static SvgSource parse(String src, ClassLoader loader) {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(loader, "loader");
        if (src.startsWith(Resource.SCHEME)) {
            var name = src.substring(Resource.SCHEME.length());
            return new Resource(null, loader, name.startsWith("/") ? name.substring(1) : name);
        }
        return new File(Path.of(src));
    }

    /// See [SvgSource#file].
    record File(Path path) implements SvgSource {

        public File {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public String key() {
            return "file:" + path.toAbsolutePath().normalize();
        }

        @Override
        public byte[] read() {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path, e);
            }
        }
    }

    /// See [SvgSource#resource] and [SvgSource#parse]: either an anchor class or a
    /// class loader finds it.
    ///
    /// @param anchor the class a relative name is resolved beside, or null
    /// @param loader the loader a `classpath:` name is read from, or null
    /// @param name   the resource's name
    record Resource(@Nullable Class<?> anchor, @Nullable ClassLoader loader, String name) implements SvgSource {

        /// What a `classpath:` path starts with.
        public static final String SCHEME = "classpath:";

        public Resource {
            Objects.requireNonNull(name, "name");
            if ((anchor == null) == (loader == null)) {
                throw new IllegalArgumentException("a resource is found by an anchor class or a class loader");
            }
        }

        @Override
        public String key() {
            return anchor != null ? "resource:" + anchor.getName() + ":" + name : SCHEME + name;
        }

        @Override
        public byte[] read() {
            try (var in = anchor != null
                    ? anchor.getResourceAsStream(name)
                    : Objects.requireNonNull(loader).getResourceAsStream(name)) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException("no SVG resource \"" + name + "\""));
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /// See [SvgSource#text].
    ///
    /// @param document the document, as written
    record Text(String document) implements SvgSource {

        public Text {
            Objects.requireNonNull(document, "document");
        }

        @Override
        public String key() {
            return "svg-text:" + digest(document.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public byte[] read() {
            return document.getBytes(StandardCharsets.UTF_8);
        }
    }

    /// See [SvgSource#bytes]. A class rather than a record, because an array
    /// compares by identity and two copies of one document are one source.
    final class Bytes implements SvgSource {

        private final byte[] data;

        Bytes(byte[] data) {
            this.data = data;
        }

        @Override
        public String key() {
            return "svg-bytes:" + digest(data);
        }

        @Override
        public byte[] read() {
            return data.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Bytes bytes && Arrays.equals(data, bytes.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "SvgSource.Bytes[" + data.length + " bytes]";
        }
    }

    /// The SHA-256 of `bytes`, in hex: a key for a document held in memory.
    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java has SHA-256", e);
        }
    }
}
