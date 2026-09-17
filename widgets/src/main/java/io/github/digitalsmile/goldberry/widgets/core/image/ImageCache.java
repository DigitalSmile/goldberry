package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.github.digitalsmile.goldberry.image.Image;

/// The shared loader's memory: at most one decode per [ImageSource#key()], and a
/// ceiling on the pixels kept.
///
/// **It holds futures, not images**, so two views asking for one file in the
/// same frame share one decode rather than racing two. A load that fails is
/// forgotten when it fails, so a file that appears later is read again.
///
/// Bounded by bytes rather than by count, because a count treats a 16×16 icon
/// and a 6000×4000 photograph as the same cost. Least recently used goes first;
/// a load still in flight counts nothing, since nobody knows its size yet.
///
/// Confined to one thread at a time by its own lock: views ask on the UI thread
/// and loads complete there too, so the lock is uncontended, and it is here so
/// that a view built on another thread is not a corruption.
final class ImageCache implements ImageLoader {

    /// 256 MiB of premultiplied pixels — about forty 4K photographs.
    static final long DEFAULT_CAPACITY = 256L * 1024 * 1024;

    static final ImageCache SHARED = new ImageCache(DEFAULT_CAPACITY, ImageLoader::run);

    private final long capacity;
    private final Function<ImageSource, CompletableFuture<Image>> runner;
    private final Map<String, CompletableFuture<Image>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long held;

    ImageCache(long capacity, Function<ImageSource, CompletableFuture<Image>> runner) {
        this.capacity = capacity;
        this.runner = runner;
    }

    @Override
    public CompletableFuture<Image> load(ImageSource source) {
        var key = source.key();
        CompletableFuture<Image> started;
        synchronized (this) {
            var existing = entries.get(key);
            if (existing != null) {
                return existing;
            }
            started = new CompletableFuture<>();
            entries.put(key, started);
        }
        var _ = runner.apply(source).whenComplete((image, failure) -> {
            synchronized (this) {
                if (failure != null) {
                    entries.remove(key, started);
                } else if (entries.get(key) == started) {
                    held += bytes(image);
                    evict(key);
                }
            }
            if (failure != null) {
                started.completeExceptionally(failure);
            } else {
                started.complete(image);
            }
        });
        return started;
    }

    /// How many bytes the finished entries hold.
    synchronized long held() {
        return held;
    }

    /// How many keys are remembered, finished or not.
    synchronized int size() {
        return entries.size();
    }

    private void evict(String keep) {
        var iterator = entries.entrySet().iterator();
        while (held > capacity && iterator.hasNext()) {
            var entry = iterator.next();
            var future = entry.getValue();
            if (entry.getKey().equals(keep) || !future.isDone() || future.isCompletedExceptionally()) {
                continue;
            }
            held -= bytes(future.join());
            iterator.remove();
        }
    }

    private static long bytes(Image image) {
        return 4L * image.width() * image.height();
    }
}
