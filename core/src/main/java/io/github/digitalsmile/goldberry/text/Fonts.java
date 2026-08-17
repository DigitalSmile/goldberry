package io.github.digitalsmile.goldberry.text;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Typography;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// Every face and size a window draws with, opened once and kept.
///
/// The cascade resolves a [Typography] per node and the painter needs a [Font];
/// this is what joins them. Without it, `font-size: 20px` on one heading would
/// mean parsing Inter again — 681 µs and a second copy of a megabyte and a half
/// — on **every frame**, because a widget tree is rebuilt and rendered from
/// scratch each time.
///
/// ## Why this is owned and not global
///
/// A `Font` and a `FontFace` are thread-confined and hold native memory that has
/// to be released. A process-wide cache would therefore have to be per-thread,
/// and a per-thread cache of native memory has no hook that would ever free it
/// ([ADR-0044](../../../../../../book/src/adr/0044-one-face-many-sizes.md)). So
/// this is an ordinary object an application opens and closes, normally for the
/// life of the window that renders through it:
///
/// ```java
/// try (var fonts = Fonts.bundled()) {
///     var renderer = new WidgetRenderer(stylesheets, fonts);
///     // ...
/// }
/// ```
///
/// Closing it closes every font and face it opened, in that order — which is the
/// ordering `FontFace` documents and nothing else enforces.
///
/// ## What it caches
///
/// Faces by [BundledFont], and fonts by (face, size). Those are the two levels
/// [ADR-0044](../../../../../../book/src/adr/0044-one-face-many-sizes.md)
/// established: a face is size-independent because Goldberry never scales the
/// shaper, so a second size costs 4.4 µs rather than 681 and no second copy of
/// the file.
///
/// The size is rounded to a thousandth of a pixel before it is used as a key. Two
/// `13.000000000000002`s from different `em` chains are the same font to any
/// reader, and a cache that disagreed would open one face per frame and look
/// exactly like a leak.
///
/// Confined to the thread that created it, and must be closed.
public final class Fonts implements AutoCloseable {

    /// Key precision: a thousandth of a logical pixel, which is far below what
    /// any rasterizer distinguishes and far above floating-point noise.
    private static final double SIZE_QUANTUM = 1000.0;

    private final Map<BundledFont, FontFace> faces = new LinkedHashMap<>();
    private final Map<Key, Font> fonts = new LinkedHashMap<>();

    private boolean closed;

    private record Key(BundledFont face, long size) {
    }

    private Fonts() {
    }

    /// A book over the faces bundled in `goldberry-core`.
    ///
    /// Opens nothing yet: a face is parsed the first time something asks for it,
    /// so an application that never draws code text never pays for JetBrains
    /// Mono. That matters on the start-up path §1's "starts in milliseconds"
    /// claim is measured against
    /// ([ADR-0028](../../../../../../book/src/adr/0028-the-start-up-timeline.md)).
    public static Fonts bundled() {
        return new Fonts();
    }

    /// The font a resolved style asks for.
    ///
    /// Falls back to the UI face when the family names nothing bundled. §6.1 has
    /// no fallback *cascade* — a missing glyph is `.notdef` on purpose — but a
    /// missing **family** is a stylesheet naming a font that was never shipped,
    /// and drawing that in Inter is better than a window with no text in it. It
    /// is logged by the cascade when the name fails to parse and silent here when
    /// it merely does not match, which is the same distinction `var()` draws.
    public Font of(Typography typography) {
        Objects.requireNonNull(typography, "typography");
        var face = typography.face();
        return of(face == null ? BundledFont.UI : face, typography.size());
    }

    /// The font for one bundled face at one size.
    public Font of(BundledFont face, double size) {
        requireUsable();
        Objects.requireNonNull(face, "face");
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("a font size must be positive, not " + size);
        }
        var quantized = Math.round(size * SIZE_QUANTUM);
        return fonts.computeIfAbsent(
                new Key(face, quantized),
                key -> Font.on(faceOf(key.face()), key.size() / SIZE_QUANTUM));
    }

    /// The typeface for one bundled face, opened on first use.
    public FontFace faceOf(BundledFont face) {
        requireUsable();
        Objects.requireNonNull(face, "face");
        return faces.computeIfAbsent(face, FontFace::bundled);
    }

    /// How many distinct fonts are open — diagnostics, and what a test asserts
    /// when it wants to know a frame did not open a new one.
    public int openFonts() {
        return fonts.size();
    }

    /// How many typefaces are open.
    public int openFaces() {
        return faces.size();
    }

    public boolean isClosed() {
        return closed;
    }

    /// Closes every font, then every face.
    ///
    /// In that order, and not the other way round: Blend2D and HarfBuzz both keep
    /// references from a font into its face, and closing the face first leaves
    /// them reading unmapped memory. A failure closing one does not stop the
    /// rest — a leaked handle is better than a leaked handle *and* an
    /// unrecoverable window.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (var font : fonts.values()) {
            failure = closeQuietly(font, failure);
        }
        fonts.clear();
        for (var face : faces.values()) {
            failure = closeQuietly(face, failure);
        }
        faces.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException closeQuietly(AutoCloseable target, RuntimeException failure) {
        try {
            target.close();
            return failure;
        } catch (RuntimeException e) {
            if (failure == null) {
                return e;
            }
            failure.addSuppressed(e);
            return failure;
        } catch (Exception e) {
            var wrapped = new IllegalStateException("could not close " + target, e);
            if (failure == null) {
                return wrapped;
            }
            failure.addSuppressed(wrapped);
            return failure;
        }
    }

    private void requireUsable() {
        if (closed) {
            throw new IllegalStateException("this Fonts has been closed");
        }
    }

    @Override
    public String toString() {
        return "Fonts[" + faces.size() + " face(s), " + fonts.size() + " font(s)"
                + (closed ? ", closed" : "") + "]";
    }
}
