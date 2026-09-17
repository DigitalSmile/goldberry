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
}
