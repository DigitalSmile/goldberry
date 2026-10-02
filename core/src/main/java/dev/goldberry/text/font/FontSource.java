package dev.goldberry.text.font;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.assets.Face;

/// A face an application ships, named the way a stylesheet will ask for it.
///
/// ```java
/// @Override public List<FontSource> fonts() {
///     return List.of(
///             FontSource.stream("Forum", 400, Style.UPRIGHT,
///                     () -> App.class.getResourceAsStream("fonts/Forum-Regular.ttf")),
///             FontSource.stream("Forum", 700, Style.UPRIGHT,
///                     () -> App.class.getResourceAsStream("fonts/Forum-Bold.ttf")));
/// }
/// ```
///
/// After that, `font-family: Forum` reaches the cascade, paragraph layout, a
/// field's caret and the rasterizer's glyph cache together, because all four go
/// through the one [Fonts] book and the book knows the face.
///
/// **Read the file with your own code.** The lambda above runs in the
/// application's module, so it reads a resource in a package nobody opened. A
/// resource named to [#resource(String, int, BundledFont.Style, Class, String)]
/// is read by the toolkit's module instead, and on the module path the package
/// holding it must be opened to `dev.goldberry.core`.
///
/// The bytes are a [Supplier] rather than an array because a face is parsed the
/// first time something asks for it, so an application that ships a display
/// face and shows no title never parses it. The book does look for every file
/// when it opens, so a missing one is a warning at start naming the face rather
/// than a surprise on the screen that first uses it. A supplier that fails is
/// reported once and the text falls back to the UI face; it does not throw
/// from inside a paint pass.
///
/// The weight is a CSS number, 1 to 1000. A family registered at 500, 600, 700
/// and 800 gives each of them to the stylesheet that asks, and a weight it does
/// not have resolves to the nearest one by [Face#match], the rule Inter's own
/// faces answer by. The bundled families are searched first, so a shipped file
/// named `Inter` is never reached.
///
/// Read more: [Shipping a face](https://goldberry.dev/docs/guide/text.html#shipping-a-face).
///
/// @param family the family name as a stylesheet writes it; compared ignoring case
/// @param weight the CSS weight this file is drawn at, 1 to 1000
/// @param style  upright or italic
/// @param bytes  the file's contents, asked for once, when the face is first opened
public record FontSource(String family, int weight, BundledFont.Style style, Supplier<byte[]> bytes) implements Face {

    /// @throws IllegalArgumentException if the family is blank or the weight is
    ///         outside 1 to 1000
    public FontSource {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(bytes, "bytes");
        if (family.isBlank()) {
            throw new IllegalArgumentException("a face needs a family name a stylesheet can write");
        }
        Face.requireWeight(weight);
    }

    /// A face at one of the two named weights.
    public FontSource(String family, BundledFont.Weight weight, BundledFont.Style style, Supplier<byte[]> bytes) {
        this(family, Objects.requireNonNull(weight, "weight").value(), style, bytes);
    }

    /// A face read from a stream the application opens, when the face is first
    /// used.
    ///
    /// ```java
    /// FontSource.stream("Forum", 400, Style.UPRIGHT, () -> App.class.getResourceAsStream("fonts/Forum.ttf"))
    /// ```
    ///
    /// The form that works in a modular application without opening anything:
    /// the supplier is the application's code, so the resource is read with the
    /// application's own access. It is called once when the book opens, to see
    /// that the file is there, and the stream is closed unread; and once more
    /// when the face is first drawn. A supplier that answers null is a face
    /// that is not there.
    public static FontSource stream(
            String family, int weight, BundledFont.Style style, Supplier<? extends InputStream> stream) {
        Objects.requireNonNull(stream, "stream");
        return new FontSource(family, weight, style, new StreamBytes(stream));
    }

    /// A face read from a resource beside `anchor`, the way a stylesheet is read
    /// from one.
    ///
    /// The toolkit's module does the reading, so on the module path the package
    /// that holds the file must be opened to `dev.goldberry.core`, and a
    /// package that is not is reported naming the line to add. [#stream] reads
    /// it with the application's access instead and needs no `opens`.
    ///
    /// @param name the resource name, relative to `anchor`'s package unless it
    ///             starts with `/`
    public static FontSource resource(
            String family, int weight, BundledFont.Style style, Class<?> anchor, String name) {
        return new FontSource(family, weight, style, new ResourceBytes(anchor, name));
    }

    /// The same, at one of the two named weights.
    public static FontSource resource(
            String family, BundledFont.Weight weight, BundledFont.Style style, Class<?> anchor, String name) {
        return resource(family, Objects.requireNonNull(weight, "weight").value(), style, anchor, name);
    }

    /// A face whose bytes are already in hand, from a download or a test.
    ///
    /// Copied, so an array the caller changes afterwards does not change a face
    /// that has not been opened yet.
    public static FontSource of(String family, int weight, BundledFont.Style style, byte[] data) {
        Objects.requireNonNull(data, "data");
        var copy = data.clone();
        return new FontSource(family, weight, style, () -> copy);
    }

    /// The same, at one of the two named weights.
    public static FontSource of(String family, BundledFont.Weight weight, BundledFont.Style style, byte[] data) {
        return of(family, Objects.requireNonNull(weight, "weight").value(), style, data);
    }

    /// Why this face's file cannot be read, found without reading it, or empty
    /// when it looks readable or there is no way to tell short of reading it.
    ///
    /// What the book asks when it opens: a resource or a stream is looked for
    /// and closed, and bytes from anywhere else are taken on trust until the
    /// face is drawn.
    Optional<String> problem() {
        return bytes instanceof Probed probed ? probed.problem() : Optional.empty();
    }

    @Override
    public String toString() {
        return "FontSource[" + family + " " + weight + " " + style.cssName() + "]";
    }

    /// Bytes from somewhere that can be looked at without reading it all.
    private sealed interface Probed extends Supplier<byte[]> {

        Optional<String> problem();
    }

    /// The application's own stream. Compared by the supplier, so two sources
    /// built from one constant are one face.
    private record StreamBytes(Supplier<? extends InputStream> stream) implements Probed {

        @Override
        public byte[] get() {
            try (var in = stream.get()) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException("the font stream supplier answered null"));
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the font stream", e);
            }
        }

        @Override
        public Optional<String> problem() {
            try (var in = stream.get()) {
                return in == null ? Optional.of("its stream supplier answered null") : Optional.empty();
            } catch (IOException | RuntimeException e) {
                return Optional.of("its stream could not be opened: " + e);
            }
        }
    }

    /// A resource beside a class, read by this module.
    private record ResourceBytes(Class<?> anchor, String name) implements Probed {

        private ResourceBytes {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public byte[] get() {
            try (var in = anchor.getResourceAsStream(name)) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException(missing()));
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the font resource " + name, e);
            }
        }

        @Override
        public Optional<String> problem() {
            try (var in = anchor.getResourceAsStream(name)) {
                return in == null ? Optional.of(missing()) : Optional.empty();
            } catch (IOException e) {
                return Optional.of("its resource " + name + " could not be opened: " + e);
            }
        }

        /// Why the resource was not found, telling a missing file from an
        /// encapsulated one: the second is a file sitting exactly where it was
        /// put, and "not found" alone sends somebody looking for it.
        private String missing() {
            var module = anchor.getModule();
            var reader = FontSource.class.getModule();
            var to = reader.isNamed() ? " to " + reader.getName() : "";
            var path = name.startsWith("/")
                    ? name.substring(1)
                    : anchor.getPackageName().replace('.', '/') + "/" + name;
            var slash = path.lastIndexOf('/');
            var pkg = slash < 0 ? "" : path.substring(0, slash).replace('/', '.');
            var where = "no font resource \"" + name + "\" beside " + anchor.getName() + ". ";
            if (module.isNamed()
                    && !pkg.isEmpty()
                    && module.getPackages().contains(pkg)
                    && !module.isOpen(pkg, reader)) {
                return where + "Module " + module.getName() + " does not open " + pkg + to
                        + ", and JPMS encapsulates resources as well as classes. Add `opens " + pkg + to
                        + ";` to its module-info, or read the file with your own code:"
                        + " FontSource.stream(family, weight, style, () -> " + anchor.getSimpleName()
                        + ".class.getResourceAsStream(\"" + name + "\"))";
            }
            return where + "It is missing from src/main/resources/" + path;
        }
    }
}
