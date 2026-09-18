package io.github.digitalsmile.goldberry.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
            System.err.println("usage: PrepareAssets <cache-dir> <resource-dir> [<licenses-dir>]"
                    + " [--only=a,b] [--root=a/b/c]");
            System.exit(2);
            return;
        }
        var cache = new AssetCache(Path.of(args[0]));
        var resources = Path.of(args[1]).resolve(root(args));
        var licences = args.length > 2 && !args[2].startsWith("--") ? Path.of(args[2]) : null;
        var wanted = selection(args);

        Files.createDirectories(resources);

        for (var asset : Asset.all()) {
            if (!wanted.contains(asset.name())) {
                continue;
            }
            var archive = cache.fetch(asset);
            extract(asset, archive, resources);
            if (licences != null) {
                vendorLicence(cache, asset, archive, licences);
            }
        }

        if (wanted.contains(Asset.LUCIDE.name())) {
            compileIcons(cache.fetch(Asset.LUCIDE), resources);
        }
    }

    /// Where inside the jar the assets land — [#RESOURCE_ROOT], or the `--root=`
    /// path.
    ///
    /// A module may **not** share a package with another one, and a directory of
    /// resources is a package to the module system exactly as a directory of
    /// classes is. `:core` and `:emoji` both writing `…goldberry.assets.fonts`
    /// therefore produced two modules containing one package, which is a
    /// `LayerInstantiationException` at start-up and is invisible on a class path
    /// — so every test passed and the application would not open ([ADR-0387]).
    ///
    /// So each module names a root inside its **own** package, and `--root=` is
    /// how a build script says which.
    private static String root(String[] args) {
        for (var argument : args) {
            if (argument.startsWith("--root=")) {
                return argument.substring("--root=".length());
            }
        }
        return RESOURCE_ROOT;
    }

    /// Which assets to prepare — everything, or the `--only=` list.
    ///
    /// Two modules fetch assets now rather than one: `:core` takes the faces and
    /// the icons, and `:emoji` takes OpenMoji alone, because the font that
    /// carries an attribution obligation is an artifact an application opts into
    /// ([ADR-0384]). The selection is by name so that a build script says which
    /// assets it means rather than an index into a list.
    private static java.util.Set<String> selection(String[] args) {
        for (var argument : args) {
            if (argument.startsWith("--only=")) {
                var names = java.util.Set.of(argument.substring("--only=".length()).split(","));
                var known = Asset.all().stream().map(Asset::name).collect(java.util.stream.Collectors.toSet());
                for (var name : names) {
                    if (!known.contains(name)) {
                        throw new IllegalArgumentException(
                                "there is no asset called '" + name + "'; they are " + known);
                    }
                }
                return names;
            }
        }
        return Asset.all().stream().map(Asset::name).collect(java.util.stream.Collectors.toSet());
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
    private static void vendorLicence(AssetCache cache, Asset asset, Path archive, Path licences) throws IOException {
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
                    cache.fetchText(asset.licenceUrl()),
                    StandardCharsets.UTF_8);
            System.out.println("  vendored licenses/" + asset.licenceAs()
                    + " from " + asset.licenceUrl());
        }
    }
}
