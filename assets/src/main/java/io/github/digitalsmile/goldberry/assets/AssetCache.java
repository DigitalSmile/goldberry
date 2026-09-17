package io.github.digitalsmile.goldberry.assets;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.github.digitalsmile.goldberry.assets.download.Downloader;

/// Fetches pinned archives and proves they are the ones that were pinned.
///
/// The cache is the reason a build that downloads 90 MB of fonts is tolerable:
/// an archive whose checksum already matches is not fetched again, so the cost
/// is paid once per checkout rather than once per build.
///
/// The checksum is not an integrity nicety. It is the difference between "we
/// pinned a version" and "we pinned an artifact": GitHub permits a release asset
/// to be replaced and a tag to be moved, and a font that changed underneath us
/// would change how every application renders, with no version number moving to
/// say so.
public final class AssetCache {

    private final Path directory;
    private final Downloader downloader;

    public AssetCache(Path directory) {
        this(directory, Downloader.standard());
    }

    /// A cache that downloads through `downloader` — a test's, or the build's.
    public AssetCache(Path directory, Downloader downloader) {
        this.directory = directory;
        this.downloader = downloader;
    }

    /// Returns the archive's path, downloading it only if what is on disk is not
    /// already the right bytes.
    ///
    /// A transient failure is retried by the [Downloader]; a wrong hash is not.
    ///
    /// @throws IOException if the download fails, or succeeds and hashes wrong
    public Path fetch(Asset asset) throws IOException {
        var target = directory.resolve(asset.archiveName());
        if (Files.isRegularFile(target) && sha256(target).equals(asset.sha256())) {
            return target;
        }

        Files.createDirectories(directory);
        // Downloaded beside the target and moved into place, so an interrupted
        // build leaves no half-file that the next one would treat as a cache hit
        // -- it would fail the checksum, but it would fail it every time, and the
        // error would be about corruption rather than about being interrupted.
        var partial = Files.createTempFile(directory, asset.name(), ".part");
        try {
            downloader.copyTo(URI.create(asset.url()), partial);
            var actual = sha256(partial);
            if (!actual.equals(asset.sha256())) {
                throw new IOException(
                        asset.name() + ": expected SHA-256 " + asset.sha256()
                                + " but " + asset.url() + " hashed to " + actual
                                + ".\nThe upstream release changed, or the download was corrupted."
                                + " Do not update the checksum without establishing which.");
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    /// Downloads a small text file — a licence — straight into memory.
    public String fetchText(String url) throws IOException {
        return new String(downloader.readAll(URI.create(url)), StandardCharsets.UTF_8);
    }

    /// The SHA-256 of a file, as lowercase hex.
    public static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("a JDK without SHA-256", e);
        }
        try (var in = Files.newInputStream(file)) {
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
