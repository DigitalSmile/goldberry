package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The cache that keeps shaping off the frame path.
class ParagraphCacheTest {

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    @Test
    @DisplayName("the same text in the same font is shaped once")
    void repeatedTextIsShapedOnce() {
        var cache = ParagraphCache.create();

        var first = cache.paragraph(font, "Goldberry");
        var again = cache.paragraph(font, "Goldberry");

        // Identity: the whole point is that the second call did not shape.
        assertSame(first, again);
        assertEquals(1, cache.misses());
        assertEquals(1, cache.hits());
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("different text is a different paragraph")
    void differentTextIsNotShared() {
        var cache = ParagraphCache.create();

        var one = cache.paragraph(font, "one");
        var two = cache.paragraph(font, "two");

        assertNotSame(one, two);
        assertEquals(2, cache.size());
        assertEquals(2, cache.misses());
    }

    @Test
    @DisplayName("the same text in a different font is a different paragraph")
    void theFontIsPartOfTheKey() {
        try (var larger = Font.bundled(BundledFont.UI, 28)) {
            var cache = ParagraphCache.create();

            var small = cache.paragraph(font, "Goldberry");
            var large = cache.paragraph(larger, "Goldberry");

            // The runs are identical in design units (ADR-0034), so it is
            // tempting to share them. They must not be: a Paragraph measures and
            // paints through its font, so one keyed only by text would report
            // the wrong height and draw at the wrong size.
            assertNotSame(small, large);
            assertEquals(small.font(), font);
            assertEquals(large.font(), larger);
            assertTrue(large.layout(1000).width() > small.layout(1000).width());
        }
    }

    @Test
    @DisplayName("two fonts over the same face at the same size are still distinct")
    void fontsAreComparedByIdentity() {
        try (var twin = Font.bundled(BundledFont.UI, 14)) {
            var cache = ParagraphCache.create();

            var first = cache.paragraph(font, "Goldberry");
            var second = cache.paragraph(twin, "Goldberry");

            // They agree about everything measurable today, and they are separate
            // native objects. Sharing on the strength of "same face, same size"
            // would stop being true the moment either gets font features or a
            // variation axis set on it.
            assertNotSame(first, second);
            assertEquals(2, cache.misses());
        }
    }

    @Test
    @DisplayName("a full cache evicts the least recently used, not the oldest")
    void evictionIsByRecency() {
        var cache = ParagraphCache.withCapacity(2);

        cache.paragraph(font, "first");
        cache.paragraph(font, "second");
        // Touch "first" so "second" becomes the least recent.
        var first = cache.paragraph(font, "first");
        cache.paragraph(font, "third");

        assertEquals(2, cache.size());
        // "first" survived because it was used recently, even though it is the
        // oldest. A frame touches what the last frame touched, which is why
        // recency is the right thing to keep.
        assertSame(first, cache.paragraph(font, "first"));
        // "second" was evicted, so this shapes again.
        var missesBefore = cache.misses();
        cache.paragraph(font, "second");
        assertEquals(missesBefore + 1, cache.misses());
    }

    @Test
    @DisplayName("clearing forgets everything")
    void clearingEmptiesIt() {
        var cache = ParagraphCache.create();
        var before = cache.paragraph(font, "Goldberry");

        cache.clear();

        assertEquals(0, cache.size());
        assertNotSame(before, cache.paragraph(font, "Goldberry"));
    }

    @Test
    @DisplayName("text the shaper refuses is not cached")
    void refusedTextIsNotHeld() {
        var cache = ParagraphCache.create();

        assertThrows(
                UnsupportedOperationException.class,
                () -> cache.paragraph(font, "مرحبا بالعالم"));

        // Neither a null entry nor a miss counted for something that never
        // became a paragraph.
        assertEquals(0, cache.size());
        assertEquals(0, cache.misses());
    }

    @Test
    @DisplayName("a capacity of zero or less is refused")
    void impossibleCapacityIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ParagraphCache.withCapacity(0));
        assertThrows(IllegalArgumentException.class, () -> ParagraphCache.withCapacity(-1));
    }

    @Test
    @DisplayName("a cached paragraph still wraps at any width")
    void sharingDoesNotFreezeTheWrap() {
        var cache = ParagraphCache.create();
        var text = "The quick brown fox jumps over the lazy dog, twice, for good measure.";

        var paragraph = cache.paragraph(font, text);
        var wide = paragraph.layout(500).lineCount();
        var narrow = cache.paragraph(font, text).layout(120).lineCount();

        // Sharing a paragraph shares its shaping, not its wrap. Two callers at
        // two widths must both get their own answer.
        assertTrue(narrow > wide, () -> narrow + " lines at 120 against " + wide + " at 500");
        assertEquals(wide, paragraph.layout(500).lineCount(), "and going back still works");
    }
}
