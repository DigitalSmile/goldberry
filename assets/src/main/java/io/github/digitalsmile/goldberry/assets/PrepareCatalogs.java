package io.github.digitalsmile.goldberry.assets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.zip.ZipFile;

/// Writes the category tables [CatalogCompiler] builds into a directory of
/// resources — the showcase's, which is the one application that groups its
/// sheets.
///
/// ```
/// PrepareCatalogs <cache-dir> <output-dir>
/// ```
///
/// Its own `main` rather than a flag on [PrepareAssets], because the two answer
/// different questions about the same archives: [PrepareAssets] puts an asset in
/// a jar, and this reads *about* one. The Lucide archive is fetched by both and
/// cached once.
public final class PrepareCatalogs {

    /// The icon table's file name under the output directory.
    public static final String ICON_TABLE = "lucide-categories.txt";

    /// The emoji table's file name under the output directory.
    public static final String EMOJI_TABLE = "emoji-groups.txt";

    private PrepareCatalogs() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: PrepareCatalogs <cache-dir> <output-dir>");
            System.exit(2);
            return;
        }
        var cache = new AssetCache(Path.of(args[0]));
        var output = Path.of(args[1]);
        Files.createDirectories(output);

        write(output.resolve(ICON_TABLE), CatalogCompiler.iconTable(
                CatalogCompiler.iconCategories(lucideJson(cache.fetch(Asset.LUCIDE)))));
        write(output.resolve(EMOJI_TABLE), CatalogCompiler.emojiTable(
                CatalogCompiler.emojiGroups(Files.readString(cache.fetch(Asset.UNICODE_EMOJI), StandardCharsets.UTF_8))));
    }

    /// Every `icons/<name>.json` in the Lucide archive, by name.
    static HashMap<String, byte[]> lucideJson(Path archive) throws IOException {
        var json = new HashMap<String, byte[]>(2048);
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var file = entry.getName();
                if (entry.isDirectory() || !file.endsWith(".json")) {
                    continue;
                }
                var name = file.substring(file.lastIndexOf('/') + 1, file.length() - ".json".length());
                try (var in = zip.getInputStream(entry)) {
                    json.put(name, in.readAllBytes());
                }
            }
        }
        if (json.isEmpty()) {
            throw new IOException("the Lucide archive carried no icon metadata, so there is nothing to group by");
        }
        return json;
    }

    private static void write(Path target, String table) throws IOException {
        Files.writeString(target, table, StandardCharsets.UTF_8);
        System.out.println("  catalog -> " + target.getFileName() + " (" + table.lines().count() + " lines)");
    }
}
