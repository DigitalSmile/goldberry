package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendFontFace;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorLayers;

/// A typeface, as the **rasterizer** sees it — `docs/gaps.md` G14.
///
/// The other half of a typeface is the shaper's, and
/// [io.github.digitalsmile.goldberry.text.font.FontFace] is what owns one of
/// each. They are built from the same bytes and know nothing about each other,
/// which `docs/ARCHITECTURE.md` §6 asks for and ADR-0034 explains the cost of.
///
/// ## Why it is here
///
/// Because drawing a glyph is `paint`'s, and the handle that draws one was
/// `text.font`'s. That split is what left `Frame.drawGlyphs` public with two
/// `:natives` types in its signature — the last of the module boundary's leaks,
/// and the reason `:core` could not drop `requires transitive` (ADR-0290).
///
/// **Nothing about this type is native.** It is made from a `byte[]`, it answers
/// an `int`, and it closes. The `BlendFontFace` inside it never leaves this
/// package.
///
/// ## Lifetime
///
/// One face, many sizes, one copy of the bytes: a [GlyphPen] over this face does
/// **not** close it, and this must outlive every pen made from it. Confined to
/// the thread that created it.
public final class GlyphFace implements AutoCloseable {

    private final String name;
    private final BlendFontFace face;

    /// The face's colour glyphs, read once from the same bytes the rasterizer
    /// was given.
    ///
    /// Here and not in a pen, because it is a property of the **typeface**: one
    /// face serves every size, and parsing 57,000 layer records per size would be
    /// the thing ADR-0044 split this type out to stop.
    private final ColorLayers layers;

    private boolean closed;

    private GlyphFace(String name, byte[] data) {
        this.name = name;
        this.face = BlendFontFace.fromBytes(data);
        this.layers = ColorLayers.read(data);
    }

    /// Parses a typeface out of a font file's bytes.
    ///
    /// @param name what to call it in a diagnostic
    /// @param data a font file's contents, copied rather than referenced
    /// @throws io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException
    ///         if the bytes are not a font the rasterizer can read
    public static GlyphFace of(String name, byte[] data) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        return new GlyphFace(name, data);
    }

    /// What this face is called, for a diagnostic.
    public String name() {
        return name;
    }

    /// Whether any glyph in this face is drawn as coloured layers rather than as
    /// one outline.
    ///
    /// True of an emoji face and of nothing else anybody ships, which is why a
    /// [GlyphPen] asks once and then never pays for it again.
    public boolean hasColorGlyphs() {
        return !layers.isEmpty();
    }

    /// Whether it has been closed.
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        face.close();
    }

    /// The face's colour glyphs, for the pen that draws them. Package-private
    /// for [#handle()]'s reason.
    ColorLayers layers() {
        return layers;
    }

    /// The rasterizer's own handle. Package-private, which is the whole point:
    /// [GlyphPen] is in this package and nothing else needs it.
    BlendFontFace handle() {
        if (closed) {
            throw new IllegalStateException("this GlyphFace has been closed");
        }
        return face;
    }

    @Override
    public String toString() {
        return "GlyphFace[" + name + (closed ? ", closed" : "") + "]";
    }
}
