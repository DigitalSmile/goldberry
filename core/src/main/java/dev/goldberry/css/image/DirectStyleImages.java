package dev.goldberry.css.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import dev.goldberry.Goldberry;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageAddress;

/// The provider this module carries, for an application with no widget
/// catalogue on its path: it reads the file itself and keeps what it decoded.
///
/// The catalogue's provider is the one that matters — it shares the `image`
/// widget's bounded cache. This one remembers every picture a stylesheet named,
/// which is a set the sheets bound, and decodes on a virtual thread while the
/// toolkit runs and in the caller when it does not.
final class DirectStyleImages implements StyleImages {

    /// What a `classpath:` address starts with.
    static final String SCHEME = "classpath:";

    private final Map<String, CompletableFuture<Image>> entries = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Image> load(ImageAddress address) {
        var key = address.toString();
        var existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        var region = address.region();
        var started = region == null
                ? decode(address.path())
                : load(new ImageAddress(address.path(), null)).thenApply(image -> image.cropped(region));
        var raced = entries.putIfAbsent(key, started);
        if (raced != null) {
            return raced;
        }
        var _ = started.whenComplete((image, failure) -> {
            if (failure != null) {
                entries.remove(key, started);
            }
        });
        return started;
    }

    private static CompletableFuture<Image> decode(String path) {
        if (Goldberry.isUiThread()) {
            return Goldberry.async(() -> read(path));
        }
        try {
            return CompletableFuture.completedFuture(read(path));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /// Reads and decodes `path`: a resource on the application's class loader
    /// for `classpath:`, a file otherwise.
    static Image read(String path) {
        if (!path.startsWith(SCHEME)) {
            return Image.decode(Path.of(path));
        }
        var name = path.substring(SCHEME.length());
        name = name.startsWith("/") ? name.substring(1) : name;
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = DirectStyleImages.class.getClassLoader();
        }
        try (var in = loader.getResourceAsStream(name)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("no image resource \"" + name + "\""));
            }
            return Image.decode(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
