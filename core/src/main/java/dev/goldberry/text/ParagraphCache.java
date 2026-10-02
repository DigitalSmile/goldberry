package dev.goldberry.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import dev.goldberry.text.font.Font;

/// Shaped paragraphs, kept so the same text is not shaped twice.
///
/// ```java
/// var cache = ParagraphCache.create();
/// Paragraph prose = cache.paragraph(font, "The same text, every frame.");
/// // ... at the end of each frame:
/// cache.frame();
/// ```
///
/// A widget rarely holds one: the renderer keeps a cache and
/// `Paints.Context.paragraph(style, text)` reads through it, so the same text
/// gets the same [Paragraph] instance each frame. Shaping is the one part of the
/// text path expensive enough to cache, an order of magnitude dearer than a
/// wrap; wrapping is memoised inside each [Paragraph] and needs nothing here.
///
/// The key is `(font, text)`. A [Font] is a face at a size, which is the whole
/// of the resolved text style, and the width is not part of the key because
/// shaping does not depend on it. The font is compared by identity, not
/// equality: two `Font`s over the same face at the same size are separate native
/// objects, and sharing a shaping between them would stop being right the first
/// time a feature or a variation axis was set on one of them.
///
/// Eviction is least-recently-used, and the cache grows to fit a frame. A cache
/// smaller than the number of distinct paragraphs one frame asks for would miss
/// every one of them on the next frame, so the capacity rises, up to
/// [#MAX_CAPACITY], when a frame asks for more than it holds; [#frame] marks the
/// frame boundary. It never shrinks.
///
/// Confined to the thread that created it, like the fonts it holds. The
/// paragraphs need no closing; [#clear] forgets them.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
public final class ParagraphCache {

    /// What a cache starts at if nobody says otherwise: a screenful of distinct
    /// strings, roughly.
    ///
    /// Small on purpose, because each entry holds a [ShapedRun] the length of
    /// its text and an unbounded cache of those is a leak that looks like a
    /// feature. A starting point rather than a ceiling; see [#frame()].
    public static final int DEFAULT_CAPACITY = 256;

    /// As large as a self-tuned cache will grow itself.
    ///
    /// Eight thousand entries is a document of eight thousand distinct words,
    /// call it thirty pages of prose, and at roughly 200 bytes an entry that is
    /// under two megabytes. A frame whose working set is larger than this
    /// thrashes, which is the honest failure mode: the alternative is a cache
    /// that grows until something else runs out.
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

    /// How many characters have been through the shaper, over every miss.
    ///
    /// [#misses] counts paragraphs, and a paragraph is a word in one control
    /// and half a megabyte in another. A keystroke into a long note misses the
    /// cache exactly once either way, so the miss count says nothing about what
    /// the frame cost; this is the number that moves when a control re-shapes
    /// something it did not need to. It is a count, so a test can assert on it
    /// where it could not assert on the milliseconds behind it.
    private long shapedCharacters;

    /// How many paragraphs this cache will hold before evicting. Mutable, and
    /// read by the eviction hook below on every put.
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
            // `super.size()` and not `size()`: the enclosing cache has a `size()`
            // of its own, and an unqualified call from inside the map would read
            // as either until the reader checks which one wins.
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Paragraph> eldest) {
                if (super.size() <= ParagraphCache.this.capacity) {
                    return false;
                }
                // Grow rather than evict something this frame is still using.
                // Access order means the eldest entry is the least recently used,
                // and if this frame has already asked for as many paragraphs as
                // the cache holds then the least recently used one is by
                // definition one this frame touched -- so evicting it is
                // throwing away work that is about to be asked for again. Growing
                // here rather than in `frame()` is what makes the very first frame
                // of a long document cheap instead of the one after it.
                if (requestsThisFrame >= ParagraphCache.this.capacity && ParagraphCache.this.capacity < MAX_CAPACITY) {
                    ParagraphCache.this.capacity = Math.min(MAX_CAPACITY, ParagraphCache.this.capacity * 2);
                    return super.size() > ParagraphCache.this.capacity;
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
    /// The returned paragraph is shared. It is safe to wrap at different widths,
    /// which is what its memo is for, and it must not be held past the life of
    /// its font, which is true of any paragraph.
    ///
    /// Text that needs bidi is held like any other: [Paragraph#of] approximates
    /// it rather than refusing it, so there is no string this cache can be asked
    /// for and cannot answer.
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
        shapedCharacters += text.length();
        entries.put(key, shaped);
        return shaped;
    }

    /// Marks the end of a frame, and grows the cache to fit what that frame
    /// asked for.
    ///
    /// A cache smaller than one frame is worse than no cache, and the rule is
    /// arithmetic rather than a heuristic. If a frame asks for N distinct
    /// paragraphs and the cache holds fewer, least-recently-used eviction
    /// guarantees that the next frame, asking for the same N in the same order,
    /// misses every one of them: each lookup evicts the entry the walk is about
    /// to reach. The hit rate is zero, and the cache pays for the eviction on
    /// top of the shaping. A `markdown-view` builds one `text` widget per word,
    /// so a page of six hundred words against a cache of 256 is exactly that.
    ///
    /// So the cache grows in two places, for two different moments: eviction
    /// grows it during a frame rather than discard what that frame is still
    /// walking, and this grows it at the end to a quarter more than the frame
    /// asked for, so a document that gains a word does not re-tune. Neither goes
    /// past [#MAX_CAPACITY].
    ///
    /// It does not shrink. A window that showed a long document once can show it
    /// again, releasing the memory would cost the next visit the same shaping,
    /// and the whole cache is thrown away with the renderer anyway.
    public void frame() {
        requireOwner();
        highWaterMark = Math.max(highWaterMark, requestsThisFrame);
        if (requestsThisFrame > capacity) {
            capacity = Math.min(MAX_CAPACITY, requestsThisFrame + requestsThisFrame / 4);
        }
        requestsThisFrame = 0;
    }

    /// What this cache will hold before it evicts: [#DEFAULT_CAPACITY] until a
    /// frame has asked for more.
    public int capacity() {
        return capacity;
    }

    /// The most paragraphs any one frame has asked this cache for: the window's
    /// text working set, and what a test asserts against to show the cache was
    /// sized to fit it.
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

    /// How many characters those misses shaped. A keystroke into a long note
    /// misses once whatever its length, so this, not [#misses], is what says
    /// whether a control re-shaped more than it needed to.
    public long shapedCharacters() {
        return shapedCharacters;
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
