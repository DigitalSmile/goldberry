package io.github.digitalsmile.goldberry.text.font;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.assets.Face;
import io.github.digitalsmile.goldberry.css.Typography;
import io.github.digitalsmile.goldberry.log.Logs;

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
/// (ADR-0044). So
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
/// ## Faces an application ships
///
/// [#bundled(List)] adds [FontSource]s to the families a `font-family` can name,
/// **after** the bundled ones: a family is looked up among the toolkit's faces
/// first, so an application that ships a file it calls `Inter` has not replaced
/// the face every metric in the design system was drawn against (`docs/gaps.md`
/// G39, ADR-0349). A shipped face is opened lazily like a bundled one, and one
/// whose bytes cannot be read or parsed is reported once and drawn in the UI
/// face from then on.
///
/// ## What it caches
///
/// Faces by [Face] (a [BundledFont] or a [FontSource]), and fonts by (face, size). Those are the two levels
/// ADR-0044
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

    private static final Logger LOG = Logs.of(Fonts.class);

    private final Map<Face, FontFace> faces = new LinkedHashMap<>();
    private final Map<Key, Font> fonts = new LinkedHashMap<>();

    /// What the application ships, searched after the bundled faces.
    private final List<FontSource> shipped;

    /// Shipped faces that could not be opened — asked for once, reported once,
    /// and answered with the UI face after that rather than read again every
    /// frame.
    private final Set<FontSource> unusable = new HashSet<>();

    private boolean closed;

    private record Key(Face face, long size) {}

    private Fonts(List<FontSource> shipped) {
        this.shipped = shipped;
    }

    /// A book over the faces bundled in `goldberry-core`.
    ///
    /// Opens nothing yet: a face is parsed the first time something asks for it,
    /// so an application that never draws code text never pays for JetBrains
    /// Mono. That matters on the start-up path §1's "starts in milliseconds"
    /// claim is measured against
    /// (ADR-0028).
    public static Fonts bundled() {
        return new Fonts(List.of());
    }

    /// A book over the bundled faces **and** the ones an application ships.
    ///
    /// Opens nothing, like [#bundled()]; the sources are only described here.
    /// A source whose family is also a bundled family can never be reached,
    /// because the bundled faces are searched first, and it is logged rather than
    /// refused. A stylesheet that asked for `Inter` still gets Inter, and the
    /// warning says why the file went unused.
    ///
    /// @param shipped the application's faces, in no particular order
    /// @throws IllegalArgumentException if two sources claim the same family,
    ///         weight and style — one of them would never be drawn, and nothing
    ///         could say which
    public static Fonts bundled(List<FontSource> shipped) {
        Objects.requireNonNull(shipped, "shipped");
        var sources = List.copyOf(shipped);
        var seen = new HashSet<String>();
        for (var source : sources) {
            var corner = source.family().toLowerCase(Locale.ROOT) + '/' + source.weight() + '/' + source.style();
            if (!seen.add(corner)) {
                throw new IllegalArgumentException("two faces claim " + source.family() + " "
                        + source.weight().value() + " " + source.style().cssName()
                        + "; one of them could never be drawn");
            }
            if (BundledFont.of(source.family(), source.weight(), source.style()) != null) {
                LOG.warn(
                        "{} names a family the toolkit bundles; `font-family: {}` will keep drawing the bundled"
                                + " face, which is searched first",
                        source,
                        source.family());
            }
        }
        return new Fonts(sources);
    }

    /// The faces this book was given beyond the bundled ones.
    public List<FontSource> shipped() {
        return shipped;
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
        Face face = typography.face();
        if (face == null && !shipped.isEmpty()) {
            face = Face.match(shipped, typography.family(), typography.weight(), typography.style());
        }
        return fontOf(face == null ? BundledFont.UI : face, typography.size());
    }

    /// The font for one bundled face at one size.
    public Font of(BundledFont face, double size) {
        return fontOf(face, size);
    }

    /// The font for one shipped face at one size — the UI face at that size when
    /// the source's bytes cannot be read or parsed.
    ///
    /// @throws IllegalArgumentException if this book was not given `face`
    public Font of(FontSource face, double size) {
        Objects.requireNonNull(face, "face");
        if (!shipped.contains(face)) {
            throw new IllegalArgumentException(face + " is not one of the faces this book was opened with");
        }
        return fontOf(face, size);
    }

    private Font fontOf(Face face, double size) {
        requireUsable();
        Objects.requireNonNull(face, "face");
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("a font size must be positive, not " + size);
        }
        var usable = face instanceof FontSource source && !opens(source) ? BundledFont.UI : face;
        var quantized = Math.round(size * SIZE_QUANTUM);
        var key = new Key(usable, quantized);
        var held = fonts.get(key);
        if (held != null) {
            return held;
        }
        // The sibling is opened **before** the map is written to, and that is not
        // a style choice: it is an entry in this same map, and filling one key
        // from inside another key's `computeIfAbsent` is what `LinkedHashMap`
        // promises nothing about. Hence the get-then-put rather than the compute
        // this used to be.
        var sibling = usable == BundledFont.EMOJI ? null : emojiAt(quantized);
        var font = Font.on(faceFor(usable), quantized / SIZE_QUANTUM).emoji(sibling);
        fonts.put(key, font);
        return font;
    }

    /// The emoji face at one size, or null when nobody brought it.
    ///
    /// **This is where the emoji slot is joined up** — the one place in the
    /// toolkit that decides a paragraph's pictures have a face to be shaped in
    /// (ADR-0393). Silent when the artifact is absent: an application that never
    /// types an emoji should not be told about a font it did not ask for, and
    /// [io.github.digitalsmile.goldberry.assets.BundledAssets#hasEmojiFont()] is
    /// how one that cares asks.
    ///
    /// The emoji font is an ordinary entry in the same map, so it is opened once
    /// per size however it was first asked for, and closed with every other font
    /// in this book.
    private @Nullable Font emojiAt(long quantized) {
        if (!BundledAssets.hasEmojiFont()) {
            return null;
        }
        var key = new Key(BundledFont.EMOJI, quantized);
        var held = fonts.get(key);
        if (held != null) {
            return held;
        }
        var font = Font.on(faceFor(BundledFont.EMOJI), quantized / SIZE_QUANTUM);
        fonts.put(key, font);
        return font;
    }

    /// The typeface for one bundled face, opened on first use.
    public FontFace faceOf(BundledFont face) {
        requireUsable();
        Objects.requireNonNull(face, "face");
        return faceFor(face);
    }

    private FontFace faceFor(Face face) {
        if (face == BundledFont.EMOJI && !BundledAssets.hasEmojiFont()) {
            // The emoji face ships as `goldberry-emoji` and this application did
            // not add it (ADR-0384). Caught here for the reason `opens` below
            // catches its own failure: this runs inside a render pass, and a
            // stylesheet that writes `font-family: OpenMoji` must not be able to
            // turn a window into no text at all. Said once, because a cascade
            // asks per element per frame.
            if (emojiReported.compareAndSet(false, true)) {
                LOG.warn(
                        "the emoji face is not on the module path, so text asking for it is drawn in {}."
                                + " Add io.github.digitalsmile:goldberry-emoji — and its credit — to draw emoji"
                                + " (ADR-0384)",
                        BundledFont.UI.family());
            }
            return faceFor(BundledFont.UI);
        }
        return faces.computeIfAbsent(face, key -> switch (key) {
            case BundledFont bundled -> FontFace.bundled(bundled);
            case FontSource source ->
                FontFace.of(source.family(), source.bytes().get());
        });
    }

    /// Whether the missing emoji face has been mentioned. One line per process,
    /// not one per element per frame.
    private final java.util.concurrent.atomic.AtomicBoolean emojiReported =
            new java.util.concurrent.atomic.AtomicBoolean();

    /// Whether a shipped face opens, trying it the first time it is asked about.
    ///
    /// The failure is caught **here** rather than where the face is drawn: this
    /// runs inside a render pass, and a missing resource in an application's jar
    /// must not become a window with no text in it.
    private boolean opens(FontSource source) {
        if (unusable.contains(source)) {
            return false;
        }
        if (faces.containsKey(source)) {
            return true;
        }
        try {
            faceFor(source);
            return true;
        } catch (RuntimeException e) {
            unusable.add(source);
            LOG.warn(
                    "could not open {}; text asking for it is drawn in {} instead", source, BundledFont.UI.family(), e);
            return false;
        }
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

    private static RuntimeException closeQuietly(AutoCloseable target, @Nullable RuntimeException failure) {
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
        return "Fonts[" + faces.size() + " face(s), " + fonts.size() + " font(s)" + (closed ? ", closed" : "") + "]";
    }
}
