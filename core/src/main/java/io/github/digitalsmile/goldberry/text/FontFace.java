package io.github.digitalsmile.goldberry.text;

import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendFontFace;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapedFont;
import java.util.Objects;

/// One typeface — everything about a font except the size.
///
/// A [Font] used to own the whole chain: HarfBuzz's face and Blend2D's, each
/// with its own copy of the file. Inter is a megabyte and a half, so two copies
/// per `Font` and one `Font` per size meant an application with four text sizes
/// carrying twelve megabytes of the same outlines. This is the thing they share
/// (ADR-0044).
///
/// ## What is here and what is not
///
/// The **shaper** is here in full, not merely its face. HarfBuzz's font object
/// carries a scale, and Goldberry never sets one — a shaping result is in design
/// units and is therefore correct at every size (ADR-0034). So one `hb_font_t`
/// serves every `Font` over this face, and the size lives only on Blend2D's side.
///
/// The **Blend2D face** is here; the Blend2D *font* is not, because that is
/// precisely the object the size is on.
///
/// ## Lifetime
///
/// A face must outlive every [Font] made from it — Blend2D and HarfBuzz both
/// keep references, and closing this first leaves them reading unmapped memory.
/// The natural shape is a face held for as long as the window that draws with it,
/// with the fonts inside that scope:
///
/// ```java
/// try (var face = FontFace.bundled(BundledFont.UI);
///         var title = Font.on(face, 18);
///         var body = Font.on(face, 14)) {
///     // ...
/// }
/// ```
///
/// Nothing here is a global cache. Faces are owned explicitly, the way every
/// other native-backed object in the toolkit is — a process-wide cache of
/// thread-confined objects would have to be per-thread, and a per-thread cache of
/// native memory is a leak with no hook to close it.
///
/// Confined to the thread that created it, and must be closed.
public final class FontFace implements AutoCloseable {

    private final String name;
    private final ShapedFont shaper;
    private final BlendFontFace painter;
    private final int unitsPerEm;

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
            this.painter = BlendFontFace.fromBytes(data);
        } catch (RuntimeException | Error e) {
            shaper.close();
            throw e;
        }
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

    /// The face's design grid — 2048 for Inter, 1000 for many others.
    ///
    /// The denominator of the font matrix, and the number that decides whether a
    /// glyph run is in the units Blend2D expects (ADR-0034).
    public int unitsPerEm() {
        requireUsable();
        return unitsPerEm;
    }

    /// Whether the face has been closed.
    public boolean isClosed() {
        return closed;
    }

    /// Releases both libraries' copies of the typeface.
    ///
    /// **Every [Font] over it must be closed first.** Nothing here enforces
    /// that — a reference count would, and would also make the ordering
    /// invisible rather than wrong — so it is stated, and the scoped form above
    /// is what makes it automatic.
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

    BlendFontFace painter() {
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
