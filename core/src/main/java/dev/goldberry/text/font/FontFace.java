package dev.goldberry.text.font;

import java.util.Objects;

import dev.goldberry.assets.BundledAssets;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.natives.harfbuzz.ShapedFont;
import dev.goldberry.paint.GlyphFace;

/// One typeface: everything about a font except the size.
///
/// ```java
/// try (var face = FontFace.bundled(BundledFont.UI);
///         var title = Font.on(face, 18);
///         var body = Font.on(face, 14)) {
///     // two sizes, one parse of Inter
/// }
/// ```
///
/// A face is what several [Font]s share. HarfBuzz and Blend2D each keep their
/// own copy of the file, and Inter is a megabyte and a half, so parsing a face
/// per size would cost an application with four text sizes twelve megabytes of
/// the same outlines; parsing it once here costs three.
///
/// The shaper is here in full, not merely its face. HarfBuzz's font object
/// carries a scale and Goldberry never sets one, so a shaping result is in
/// design units and is correct at every size. One shaping font therefore serves
/// every `Font` over this face, and the size lives only on Blend2D's side. The
/// Blend2D face is here too; the Blend2D font is not, because that is the
/// object the size is on.
///
/// A face must outlive every [Font] made from it. Both libraries keep
/// references from a font into its face, and closing the face first leaves them
/// reading unmapped memory. The natural shape is a face held for as long as the
/// window that draws with it, with the fonts inside that scope, as above.
/// Nothing here is a global cache: faces are owned explicitly, because a
/// process-wide cache of thread-confined native objects would have no hook that
/// could ever close it.
///
/// Confined to the thread that created it, and must be closed.
///
/// Read more:
/// [Faces, fonts and the book](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
public final class FontFace implements AutoCloseable {

    private final String name;
    private final ShapedFont shaper;
    private final GlyphFace painter;
    private final int unitsPerEm;
    private final Coverage coverage;

    private boolean closed;

    private FontFace(String name, byte[] data) {
        this.name = name;

        // Built in order and unwound in reverse: each owns native memory, and a
        // failure partway through must not leak what came before.
        this.shaper = ShapedFont.fromBytes(data);
        try {
            // Read once, here, and never again: it is a property of the face,
            // and every Font over it needs the same number.
            this.unitsPerEm = shaper.unitsPerEm();
            this.painter = GlyphFace.of(name, data);
        } catch (RuntimeException | Error e) {
            shaper.close();
            throw e;
        }
        // Read here because the bytes are here: neither library keeps them where
        // Java can see them, and a face is opened once and kept.
        this.coverage = Coverage.of(data);
    }

    /// Parses a typeface from the bytes of a font file.
    public static FontFace of(String name, byte[] data) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        return new FontFace(name, data);
    }

    /// One of the faces bundled in `goldberry-core`.
    public static FontFace bundled(BundledFont font) {
        Objects.requireNonNull(font, "font");
        return of(font.family(), BundledAssets.font(font));
    }

    /// The family name, for diagnostics.
    public String name() {
        return name;
    }

    /// The face's design grid: 2048 for Inter, 1000 for many others.
    ///
    /// The denominator of the font matrix, `size / units-per-em`, and the units a
    /// shaped run is measured in.
    public int unitsPerEm() {
        requireUsable();
        return unitsPerEm;
    }

    /// The characters this face has glyphs for, read from its `cmap` when it was
    /// opened.
    ///
    /// What a paragraph asks before shaping, when its font has fallback faces:
    /// text this covers is shaped here and nowhere else. Empty for a face whose
    /// `cmap` could not be read, which a paragraph takes as covering everything,
    /// so such a face draws exactly what it drew before fallbacks existed.
    public Coverage coverage() {
        return coverage;
    }

    /// Whether the face has been closed.
    public boolean isClosed() {
        return closed;
    }

    /// Releases both libraries' copies of the typeface.
    ///
    /// Every [Font] over it must be closed first. Nothing here enforces that,
    /// because a reference count would make the ordering invisible rather than
    /// wrong; the try-with-resources form in the class comment makes it
    /// automatic.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            painter.close();
        } finally {
            shaper.close();
        }
    }

    ShapedFont shaper() {
        requireUsable();
        return shaper;
    }

    GlyphFace painter() {
        requireUsable();
        return painter;
    }

    private void requireUsable() {
        if (closed) {
            throw new IllegalStateException("this FontFace has been closed");
        }
    }

    @Override
    public String toString() {
        return "FontFace[" + name + (closed ? ", closed" : "") + "]";
    }
}
