package io.github.digitalsmile.goldberry.paint;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendFontFace;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorLayers;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaints;
import io.github.digitalsmile.goldberry.text.font.sfnt.GlyphOutlines;
import io.github.digitalsmile.goldberry.text.font.sfnt.OutlineSink;

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

    /// The face's `COLR` version 1 paint graphs — Noto Color Emoji's pictures —
    /// indexed once per typeface for [#layers]'s reason, and each graph parsed
    /// on first use (ADR-0456).
    private final ColorPaints paints;

    /// The face's outlines, read in Java — but only for a face with paint graphs
    /// in it, because only a graph clips a fill to a glyph's shape. A face of
    /// letters goes to the rasterizer's own glyph call and never builds a path.
    private final GlyphOutlines outlines;

    /// Each glyph's outline as a [Path], built the first time a graph clips to
    /// it. A Noto glyph is a dozen outlines and the same eyes and mouths recur
    /// across faces, so a reaction bar redrawn at sixty frames a second reads
    /// `glyf` once per shape rather than once per frame.
    private final Map<Integer, Path> paths = new ConcurrentHashMap<>();

    private boolean closed;

    private GlyphFace(String name, byte[] data) {
        this.name = name;
        this.face = BlendFontFace.fromBytes(data);
        this.layers = ColorLayers.read(data);
        this.paints = ColorPaints.read(data);
        this.outlines = paints.isEmpty() ? GlyphOutlines.NONE : GlyphOutlines.read(data);
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

    /// Whether any glyph in this face is drawn in colour — as layers (`COLR`
    /// version 0) or as a paint graph (version 1) — rather than as one outline.
    ///
    /// True of an emoji face and of nothing else anybody ships, which is why a
    /// [GlyphPen] asks once and then never pays for it again.
    public boolean hasColorGlyphs() {
        return !layers.isEmpty() || !paints.isEmpty();
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

    /// The face's paint graphs, for the pen. Package-private for [#handle()]'s
    /// reason.
    ColorPaints paints() {
        return paints;
    }

    /// Design units to the em, as the face's own `head` table says — what a
    /// paint graph's coordinates are scaled by. Known only for a face with paint
    /// graphs, which is the only one that asks.
    int unitsPerEm() {
        return outlines.unitsPerEm();
    }

    /// Glyph `glyphId`'s outline in design units, y up — or [Path#EMPTY] when the
    /// face has no such glyph or its data cannot be read, which a painter fills
    /// as nothing.
    Path outline(int glyphId) {
        return paths.computeIfAbsent(glyphId, id -> {
            var builder = Path.builder();
            var read = outlines.outline(id, new OutlineSink() {
                @Override
                public void moveTo(double x, double y) {
                    builder.moveTo(x, y);
                }

                @Override
                public void lineTo(double x, double y) {
                    builder.lineTo(x, y);
                }

                @Override
                public void quadTo(double cx, double cy, double x, double y) {
                    builder.quadTo(cx, cy, x, y);
                }

                @Override
                public void close() {
                    builder.close();
                }
            });
            return read ? builder.build() : Path.EMPTY;
        });
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
