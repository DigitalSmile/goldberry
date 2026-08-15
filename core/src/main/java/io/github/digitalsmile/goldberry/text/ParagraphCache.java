package io.github.digitalsmile.goldberry.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// Shaped paragraphs, kept so the same text is not shaped twice.
///
/// ## Why this and not something else
///
/// Measured on linux-x64, Inter at 14 points, a paragraph of about seventy
/// words (ADR-0037):
///
/// | | median |
/// |---|---|
/// | wrapping, memo hit | 0.02 µs |
/// | the `YGSize` upcall crossing | 0.28 µs |
/// | wrapping, memo miss | 4.8 µs |
/// | **shaping — what this avoids** | **56 µs** |
///
/// Shaping is twelve times a wrap and two hundred times the crossing, so it is
/// the only part of the text path worth a cache at all. Wrapping is memoised
/// inside each [Paragraph] and needs nothing here; the crossing cannot be cached
/// and does not need to be.
///
/// This matters once something rebuilds its tree. Nothing does yet — the widget
/// model is still open (ADR-0004) — and when it does, a paragraph rebuilt per
/// frame would otherwise pay 56 µs to arrive at a `GlyphRun` identical to the
/// last one's.
///
/// ## The key
///
/// `(font, text)`. `docs/ARCHITECTURE.md` §6 specifies (text, resolved text
/// style, width bucket); today a [Font] *is* the resolved text style — a face at
/// a size — and the width bucket belongs to [Paragraph]'s own memo rather than
/// here, because shaping does not depend on width. When the CSS engine arrives
/// with real text styles, this key grows and the rest of the class does not.
///
/// The font is compared by **identity**, not equality: two `Font`s over the same
/// face at the same size are separate native objects, and a `GlyphRun` shaped by
/// one is drawn by the other's Blend2D font. They agree today, and relying on
/// that is the kind of assumption that stops being true when variations or
/// features are set on one of them.
///
/// Confined to the thread that created it, like the fonts it holds.
public final class ParagraphCache {

    /// What a cache holds if nobody says otherwise.
    ///
    /// A screenful of distinct strings, roughly. Small on purpose: the entries
    /// hold `GlyphRun`s, which are six `int[]`s the length of the text, and an
    /// unbounded cache of those is a leak that looks like a feature.
    public static final int DEFAULT_CAPACITY = 256;

    private record Key(Font font, String text) {

        // Identity on the font, equality on the text. Overridden rather than
        // left to the record's own equals, which would compare fonts with
        // Font.equals -- and Font does not define one, so it would be identity
        // anyway, by accident rather than by decision.
        @Override
        public boolean equals(Object other) {
            return other instanceof Key(Font otherFont, String otherText)
                    && font == otherFont
                    && text.equals(otherText);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(font) * 31 + text.hashCode();
        }
    }

    private final Map<Key, Paragraph> entries;
    private final Thread owner = Thread.currentThread();

    private long hits;
    private long misses;

    private ParagraphCache(int capacity) {
        // Access-ordered, so `removeEldestEntry` evicts the least recently used
        // rather than the oldest. A frame touches the same paragraphs it touched
        // last frame, so recency is the right thing to keep.
        this.entries = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Paragraph> eldest) {
                return size() > capacity;
            }
        };
    }

    /// A cache holding up to [#DEFAULT_CAPACITY] paragraphs.
    public static ParagraphCache create() {
        return withCapacity(DEFAULT_CAPACITY);
    }

    /// A cache holding up to `capacity` paragraphs, evicting least-recently-used.
    public static ParagraphCache withCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "a cache must hold at least one paragraph, and " + capacity + " is not");
        }
        return new ParagraphCache(capacity);
    }

    /// The paragraph for `text` in `font`, shaping it only if it is not held.
    ///
    /// The returned paragraph is **shared**. It is safe to wrap at different
    /// widths — that is what its memo is for — and it must not be held past the
    /// life of its font, which is true of any paragraph.
    ///
    /// @throws UnsupportedOperationException if the text is right-to-left, which
    ///         [Paragraph#of] refuses; nothing is cached in that case
    public Paragraph paragraph(Font font, String text) {
        requireOwner();
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");

        var key = new Key(font, text);
        var held = entries.get(key);
        if (held != null) {
            hits++;
            return held;
        }
        // Shaped outside the map: Paragraph.of can throw, and a computeIfAbsent
        // that throws leaves the map in a state LinkedHashMap does not promise
        // anything about.
        var shaped = Paragraph.of(font, text);
        misses++;
        entries.put(key, shaped);
        return shaped;
    }

    /// How many paragraphs are held.
    public int size() {
        requireOwner();
        return entries.size();
    }

    /// How many lookups found a shaped paragraph already here.
    public long hits() {
        return hits;
    }

    /// How many had to shape. Also how many paragraphs have been built through
    /// this cache, evictions included.
    public long misses() {
        return misses;
    }

    /// Forgets everything. The paragraphs themselves need no closing — they hold
    /// no native resources of their own, only a reference to a font that does.
    public void clear() {
        requireOwner();
        entries.clear();
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a ParagraphCache belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "ParagraphCache[" + entries.size() + " held, " + hits + " hits, "
                + misses + " misses]";
    }
}
