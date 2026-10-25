package dev.goldberry.assets.prepare;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.assets.download.Downloader;

/// The cache fetches an archive once, and keeps what another build fetched
/// beside it.
class AssetCacheTest {

    private static final byte[] BYTES = "the archive".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    private int requests;

    private Downloader downloader() {
        return new Downloader(
                uri -> {
                    requests++;
                    return new Downloader.Response(200, new ByteArrayInputStream(BYTES));
                },
                1,
                Duration.ZERO,
                ignored -> {});
    }

    private Asset asset() throws IOException {
        var file = Files.write(directory.resolve("reference"), BYTES);
        var sha256 = AssetCache.sha256(file);
        Files.delete(file);
        return new Asset(
                "inter", "4.0", "https://example.invalid/inter.zip", sha256, Map.of(), Map.of(), null, null);
    }

    @Test
    @DisplayName("downloads an archive once, and finds it on disk the next time")
    void fetchesOnce() throws IOException {
        var asset = asset();
        try (var cache = new AssetCache(directory, downloader())) {
            var first = cache.fetch(asset);
            var second = cache.fetch(asset);
            assertEquals(first, second);
            assertArrayEquals(BYTES, Files.readAllBytes(first));
        }
        assertEquals(1, requests);
    }

    @Test
    @DisplayName("keeps the archive another build put in place while its own move was refused")
    void anotherBuildWon() throws IOException {
        var asset = asset();
        // Windows' answer when the other build has the archive open to hash it.
        AssetCache.Mover refused = (partial, target) -> {
            Files.copy(partial, target);
            throw new FileSystemException(target.toString(), null, "being used by another process");
        };
        try (var cache = new AssetCache(directory, downloader(), refused)) {
            var target = cache.fetch(asset);
            assertArrayEquals(BYTES, Files.readAllBytes(target));
            try (var files = Files.list(directory)) {
                assertEquals(1, files.count(), "no partial download left behind");
            }
        }
    }

    @Test
    @DisplayName("fails a refused move when what is in place is not the archive")
    void refusedWithNothingRight() throws IOException {
        var asset = asset();
        var failure = new FileSystemException("inter.zip", null, "being used by another process");
        AssetCache.Mover refused = (partial, target) -> {
            Files.writeString(target, "something else");
            throw failure;
        };
        try (var cache = new AssetCache(directory, downloader(), refused)) {
            assertSame(failure, assertThrows(FileSystemException.class, () -> cache.fetch(asset)));
        }
    }
}
