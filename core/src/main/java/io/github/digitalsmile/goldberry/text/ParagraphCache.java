package io.github.digitalsmile.goldberry.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.digitalsmile.goldberry.text.font.Font;

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

    /// What a cache starts at if nobody says otherwise.
    ///
    /// A screenful of distinct strings, roughly. Small on purpose: the entries
    /// hold `GlyphRun`s, which are six `int[]`s the length of the text, and an
    /// unbounded cache of those is a leak that looks like a feature.
    ///
    /// **A starting point rather than a ceiling since ADR-0299** — see
    /// [#frame()].
    public static final int DEFAULT_CAPACITY = 256;

    /// As large as a self-tuned cache will grow itself.
    ///
    /// Eight thousand entries is a document of eight thousand distinct words —
    /// call it thirty pages of prose — and at roughly 200 bytes an entry that is
    /// under two megabytes. A frame whose working set is larger than this thrashes
    /// exactly as every frame used to, which is the honest failure mode: the
    /// alternative is a cache that grows until something else runs out.
    public static final int MAX_CAPACITY = 8192;

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

    /// How many paragraphs this cache will hold before evicting.
    ///
    /// Mutable since ADR-0299, and read by the eviction hook below on every put —
    /// which is why it is a field rather than the constructor parameter it was.
    private int capacity;

    /// Lookups since the last [#frame()], which is what a frame's working set is
    /// measured as. Counted rather than collected: a set of the keys seen would
    /// allocate per frame to answer a question an over-estimate answers as well.
    private int requestsThisFrame;

    /// The largest working set any frame has shown, for [#highWaterMark()].
    private int highWaterMark;

    private ParagraphCache(int capacity) {
        this.capacity = capacity;
        // Access-ordered, so `removeEldestEntry` evicts the least recently used
        // rather than the oldest. A frame touches the same paragraphs it touched
        // last frame, so recency is the right thing to keep.
        this.entries = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Paragraph> eldest) {
                if (size() <= ParagraphCache.this.capacity) {
                    return false;
                }
                // **Grow rather than evict something this frame is still using.**
                // Access order means the eldest entry is the least recently used,
                // and if this frame has already asked for as many paragraphs as
                // the cache holds then the least recently used one is by
                // definition one *this* frame touched -- so evicting it is
                // throwing away work that is about to be asked for again. Growing
                // here rather than in `frame()` is what makes the very first frame
                // of a long document cheap instead of the one after it
                // (ADR-0299).
                if (requestsThisFrame >= ParagraphCache.this.capacity && ParagraphCache.this.capacity < MAX_CAPACITY) {
                    ParagraphCache.this.capacity = Math.min(MAX_CAPACITY, ParagraphCache.this.capacity * 2);
                    return size() > ParagraphCache.this.capacity;
                }
                return true;
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
            throw new IllegalArgumentException("a cache must hold at least one paragraph, and " + capacity + " is not");
        }
        return new ParagraphCache(capacity);
    }

    /// The paragraph for `text` in `font`, shaping it only if it is not held.
    ///
    /// The returned paragraph is **shared**. It is safe to wrap at different
    /// widths — that is what its memo is for — and it must not be held past the
    /// life of its font, which is true of any paragraph.
    ///
    /// Text that needs bidi is held like any other: [Paragraph#of] approximates
    /// it rather than refusing it (ADR-0218), so there is no longer a string this
    /// cache can be asked for and cannot answer.
    public Paragraph paragraph(Font font, String text) {
        requireOwner();
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");

        requestsThisFrame++;
        var key = new Key(font, text);
        var held = entries.get(key);
        if (held != null) {
            hits++;
            return held;
        }
        // Shaped outside the map rather than in a `computeIfAbsent`: shaping
        // crosses into HarfBuzz, and a mapping function that threw -- an
        // unusable font, a closed buffer -- would leave the map in a state
        // `LinkedHashMap` promises nothing about.
        var shaped = Paragraph.of(font, text);
        misses++;
        entries.put(key, shaped);
        return shaped;
    }

    /// Marks the end of a frame, and grows the cache to fit what that frame
    /// asked for.
    ///
    /// ## A cache smaller than one frame is worse than no cache
    ///
    /// The rule this enforces is arithmetic rather than a heuristic. If a frame
    /// asks for **N** distinct paragraphs and the cache holds **C < N**, then
    /// least-recently-used eviction guarantees that the next frame — asking for
    /// the same N in the same order — misses every one of them: each lookup
    /// evicts the entry the walk is about to reach. The hit rate is not "lower",
    /// it is **zero**, and the cache pays for the eviction on top of the shaping.
    ///
    /// That is not hypothetical. `markdown-view` and `html-view` build one `text`
    /// widget per word (ADR-0295, ADR-0298), so a page of six hundred words asks
    /// for six hundred paragraphs a frame against a cache of 256 — measured at
    /// **287 shapes per frame on a settled tree that had not changed at all**,
    /// which is 4 ms of HarfBuzz per frame to arrive at the glyphs it already had
    /// (ADR-0299).
    ///
    /// So the cache grows in two places, and they answer two different moments:
    /// eviction grows it **during** a frame rather than discard what that frame is
    /// still walking (see the constructor), and this grows it at the end to a
    /// quarter more than the frame asked for, so a document that gains a word does
    /// not re-tune. Neither goes past [#MAX_CAPACITY].
    ///
    /// **It does not shrink.** A window that showed a long document once can show
    /// it again; releasing the memory would cost the next visit the same 4 ms, and
    /// the whole entry is thrown away with the renderer anyway.
    public void frame() {
        requireOwner();
        highWaterMark = Math.max(highWaterMark, requestsThisFrame);
        if (requestsThisFrame > capacity) {
            capacity = Math.min(MAX_CAPACITY, requestsThisFrame + requestsThisFrame / 4);
        }
        requestsThisFrame = 0;
    }

    /// What this cache will hold before it evicts — [#DEFAULT_CAPACITY] until a
    /// frame has asked for more.
    public int capacity() {
        return capacity;
    }

    /// The most paragraphs any one frame has asked this cache for.
    ///
    /// What a diagnostic reads to say "this window's text working set is 620" —
    /// and what a test asserts against to show the cache was sized to fit it.
    public int highWaterMark() {
        return highWaterMark;
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
        return "ParagraphCache[" + entries.size() + " held, " + hits + " hits, " + misses + " misses]";
    }
}
