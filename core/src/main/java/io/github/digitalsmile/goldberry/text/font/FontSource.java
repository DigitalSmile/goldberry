package io.github.digitalsmile.goldberry.text.font;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Supplier;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.assets.Face;

/// A face an application ships, named the way a stylesheet will ask for it —
/// `docs/gaps.md` G39.
///
/// ```java
/// @Override public List<FontSource> fonts() {
///     return List.of(
///             FontSource.resource("Forum", Weight.REGULAR, Style.UPRIGHT, App.class, "fonts/Forum-Regular.ttf"),
///             FontSource.resource("Golos Text", Weight.REGULAR, Style.UPRIGHT, App.class, "fonts/GolosText.ttf"));
/// }
/// ```
///
/// after which `font-family: Forum` reaches the cascade, `Paragraph` layout, a
/// field's caret and the rasterizer's glyph cache together, because all four
/// already go through one [Fonts] book and the book now knows the face.
///
/// ## The bytes are read when the face is first drawn
///
/// A [Supplier] rather than an array, for [Fonts#bundled()]'s reason: a face is
/// parsed the first time something asks for it, so an application that ships a
/// display face and shows no title never reads it. A supplier that fails is
/// reported once and the text falls back to the UI face. It does not throw from
/// inside a paint pass (ADR-0349).
///
/// ## The matrix is the toolkit's
///
/// [BundledFont.Weight] and [BundledFont.Style] are the two closed pairs §1.4
/// ships. A family that fills only some corners falls back the way Inter's does,
/// through [Face#match], so a shipped family and a bundled one cannot answer
/// `font-weight: 600` differently.
///
/// @param family the family name as a stylesheet writes it; compared ignoring case
/// @param weight which of the two weights this file is
/// @param style  upright or italic
/// @param bytes  the file's contents, asked for once, when the face is first opened
public record FontSource(String family, BundledFont.Weight weight, BundledFont.Style style, Supplier<byte[]> bytes)
        implements Face {

    public FontSource {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(bytes, "bytes");
        if (family.isBlank()) {
            throw new IllegalArgumentException("a face needs a family name a stylesheet can write");
        }
    }

    /// A face read from a resource beside `anchor`, the way
    /// [io.github.digitalsmile.goldberry.css.Stylesheet#resource] reads a
    /// stylesheet.
    ///
    /// @param name the resource name, relative to `anchor`'s package unless it
    ///             starts with `/`
    public static FontSource resource(
            String family, BundledFont.Weight weight, BundledFont.Style style, Class<?> anchor, String name) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(name, "name");
        return new FontSource(family, weight, style, () -> read(anchor, name));
    }

    /// A face whose bytes are already in hand, from a download or a test.
    ///
    /// Copied, so an array the caller changes afterwards does not change a face
    /// that has not been opened yet.
    public static FontSource of(String family, BundledFont.Weight weight, BundledFont.Style style, byte[] data) {
        Objects.requireNonNull(data, "data");
        var copy = data.clone();
        return new FontSource(family, weight, style, () -> copy);
    }

    private static byte[] read(Class<?> anchor, String name) {
        try (var in = anchor.getResourceAsStream(name)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("no font resource \"" + name + "\" beside "
                        + anchor.getName() + "; if the package is in a named module, it has to be opened for"
                        + " resources to be read from it"));
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the font resource " + name, e);
        }
    }

    @Override
    public String toString() {
        return "FontSource[" + family + " " + weight.value() + " " + style.cssName() + "]";
    }
}
