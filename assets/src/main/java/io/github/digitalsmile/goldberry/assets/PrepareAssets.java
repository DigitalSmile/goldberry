package io.github.digitalsmile.goldberry.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/// Prepares every bundled asset into resources `:core` packages.
///
/// Run from Gradle, but an ordinary `main` — which means it can be run by hand
/// when something goes wrong, and its parts can be unit tested. That is the whole
/// reason `:assets` is a Java project rather than fifty lines of build script:
/// compiling 1544 SVGs into path data is real logic, and real logic deserves
/// tests rather than a comment saying it works.
///
/// ```
/// PrepareAssets <cache-dir> <resource-dir> [<licenses-dir>]
/// ```
///
/// The licence directory is optional and, when given, the verbatim upstream
/// texts are written into it. That is a *vendoring* step, run by hand and
/// committed, not part of the ordinary build: ADR-0015 wants those texts in the
/// repository so it is self-describing, and a generated file inside a tracked
/// directory that nobody committed is worse than no file at all.
public final class PrepareAssets {

    /// Where the fonts and the icon table land inside the jar.
    static final String RESOURCE_ROOT = "io/github/digitalsmile/goldberry/assets";

    private PrepareAssets() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: PrepareAssets <cache-dir> <resource-dir> [<licenses-dir>]");
            System.exit(2);
            return;
        }
        var cache = new AssetCache(Path.of(args[0]));
        var resources = Path.of(args[1]).resolve(RESOURCE_ROOT);
        var licences = args.length > 2 ? Path.of(args[2]) : null;

        Files.createDirectories(resources);

        for (var asset : Asset.all()) {
            var archive = cache.fetch(asset);
            extract(asset, archive, resources);
            if (licences != null) {
                vendorLicence(asset, archive, licences);
            }
        }

        compileIcons(cache.fetch(Asset.LUCIDE), resources);
    }

    /// Pulls the individual entries an asset contributes out of its archive.
    private static void extract(Asset asset, Path archive, Path resources) throws IOException {
        if (asset.extract().isEmpty()) {
            return;
        }
        try (var zip = new ZipFile(archive.toFile())) {
            for (var wanted : asset.extract().entrySet()) {
                var entry = zip.getEntry(wanted.getKey());
                if (entry == null) {
                    throw new IOException(
                            asset.archiveName() + " has no entry '" + wanted.getKey()
                                    + "'. The upstream archive layout changed with the version"
                                    + " bump — the checksum passed, so this is a rename, not a"
                                    + " corrupted download.");
                }
                var target = resources.resolve(wanted.getValue());
                Files.createDirectories(target.getParent());
                try (var in = zip.getInputStream(entry)) {
                    Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("  " + asset.name() + " -> " + wanted.getValue()
                        + " (" + Files.size(target) / 1024 + " KiB)");
            }
        }
    }

    /// Compiles Lucide's SVGs into the icon table.
    private static void compileIcons(Path archive, Path resources) throws IOException {
        var svgs = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".svg")) {
                    continue;
                }
                var file = entry.getName();
                var name = file.substring(file.lastIndexOf('/') + 1, file.length() - ".svg".length());
                try (var in = zip.getInputStream(entry)) {
                    svgs.put(name, in.readAllBytes());
                }
            }
        }
        if (svgs.isEmpty()) {
            throw new IOException("the Lucide archive contained no SVGs at all");
        }

        var table = new IconCompiler().compileAll(svgs);
        var target = resources.resolve("icons/lucide.txt");
        Files.createDirectories(target.getParent());

        try (var writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("# Lucide " + Asset.LUCIDE.version()
                    + ", compiled from SVG. ISC licence: licenses/lucide.txt\n");
            writer.write("# <name>\\t<path data>, in a 24x24 box,"
                    + " stroked at 2px with round caps and joins.\n");
            for (var icon : new TreeMap<>(table).entrySet()) {
                writer.write(icon.getKey());
                writer.write('\t');
                writer.write(icon.getValue());
                writer.write('\n');
            }
        }
        System.out.println("  lucide -> icons/lucide.txt (" + table.size() + " icons, "
                + Files.size(target) / 1024 + " KiB)");
    }

    /// Writes the verbatim upstream licence text into `licenses/`.
    private static void vendorLicence(Asset asset, Path archive, Path licences) throws IOException {
        Files.createDirectories(licences);
        for (var wanted : asset.licence().entrySet()) {
            try (var zip = new ZipFile(archive.toFile())) {
                var entry = zip.getEntry(wanted.getKey());
                if (entry == null) {
                    throw new IOException(
                            asset.archiveName() + " has no licence entry '" + wanted.getKey() + "'");
                }
                try (var in = zip.getInputStream(entry)) {
                    Files.copy(in, licences.resolve(wanted.getValue()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            System.out.println("  vendored licenses/" + wanted.getValue()
                    + " from " + asset.archiveName());
        }
        if (asset.licenceUrl() != null) {
            Files.writeString(
                    licences.resolve(asset.licenceAs()),
                    AssetCache.fetchText(asset.licenceUrl()),
                    StandardCharsets.UTF_8);
            System.out.println("  vendored licenses/" + asset.licenceAs()
                    + " from " + asset.licenceUrl());
        }
    }

    /// Every resource path this produces, for the test that asserts `:core` and
    /// the build agree about where they land.
    public static Map<String, String> producedResources() {
        var produced = new TreeMap<String, String>();
        for (var asset : Asset.all()) {
            asset.extract().forEach((entry, destination) ->
                    produced.put(destination, asset.name()));
        }
        produced.put("icons/lucide.txt", Asset.LUCIDE.name());
        return produced;
    }
}
