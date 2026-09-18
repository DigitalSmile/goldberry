package io.github.digitalsmile.goldberry.widgets.core.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.image.Image;

/// The shared loader's memory ([ADR-0358]).
class ImageCacheTest {

    private final List<String> started = new ArrayList<>();
    private final Map<String, CompletableFuture<Image>> pending = new HashMap<>();

    private ImageCache cache(long capacity) {
        return new ImageCache(capacity, source -> {
            started.add(source.key());
            var future = new CompletableFuture<Image>();
            pending.put(source.key(), future);
            return future;
        });
    }

    private static ImageSource named(String key) {
        return ImageSource.supplied(key, () -> {
            throw new AssertionError("the runner is replaced in this test");
        });
    }

    private static Image square(int side) {
        return Image.ofArgb(side, side, new int[side * side]);
    }

    @Test
    @DisplayName("two views asking for one source in the same frame share one decode")
    void oneDecodePerKey() {
        var cache = cache(1 << 20);

        var first = cache.load(named("a"));
        var second = cache.load(named("a"));

        assertSame(first, second);
        assertEquals(List.of("a"), started);
    }

    @Test
    @DisplayName("a failed load is forgotten, so a file that appears later is read again")
    void failureIsForgotten() {
        var cache = cache(1 << 20);
        var first = cache.load(named("missing"));

        pending.get("missing").completeExceptionally(new IllegalStateException("no such file"));
        var second = cache.load(named("missing"));

        assertTrue(first.isCompletedExceptionally());
        assertNotSame(first, second);
        assertEquals(List.of("missing", "missing"), started);
    }

    @Test
    @DisplayName("past its capacity in bytes, the least recently used picture goes first")
    void evictsByBytes() {
        var cache = cache(3 * 4L * 10 * 10);
        for (var key : List.of("a", "b", "c")) {
            cache.load(named(key));
            pending.get(key).complete(square(10));
        }
        cache.load(named("a"));

        cache.load(named("d"));
        pending.get("d").complete(square(10));

        assertEquals(3 * 400L, cache.held());
        cache.load(named("b"));
        assertEquals(List.of("a", "b", "c", "d", "b"), started, "b was evicted; a was used more recently");
    }

    @Test
    @DisplayName("a picture larger than the whole cache is kept for the view that asked")
    void oversizedIsKept() {
        var cache = cache(100);
        var future = cache.load(named("huge"));

        pending.get("huge").complete(square(20));

        assertTrue(future.isDone());
        assertEquals(1, cache.size());
    }

    @Nested
    @DisplayName("an image the application already holds")
    class AlreadyDecoded {

        private ImageCache passThrough() {
            return new ImageCache(1 << 20, source -> CompletableFuture.completedFuture(source.load()));
        }

        /// `Decoded.key()` was `"decoded:" + System.identityHashCode(image)`, and an
        /// identity hash is not unique — the JVM promises only that it does not
        /// change, not that no two objects share one. Two application images under
        /// one key would hand the second view the first one's picture. There is no
        /// key at all now, so there is nothing for two of them to collide in.
        @Test
        @DisplayName("is never confused with another one")
        void everyImageIsItself() {
            var cache = passThrough();
            var first = square(4);
            var second = square(8);

            assertSame(first, cache.load(ImageSource.of(first)).join());
            assertSame(second, cache.load(ImageSource.of(second)).join());
        }

        /// The cache is one map for the process, and an entry in it lives until
        /// enough other pictures push it out. An `Image` the application made and
        /// showed once has no business being held there: there is no decode to
        /// share, because [ImageSource.Decoded#load] hands back what it was given.
        @Test
        @DisplayName("is not kept in a cache that outlives the view showing it")
        void nothingIsPinned() {
            var cache = passThrough();

            cache.load(ImageSource.of(square(10)));

            assertEquals(0, cache.size(), "an image in hand was remembered");
            assertEquals(0L, cache.held());
        }
    }
}
