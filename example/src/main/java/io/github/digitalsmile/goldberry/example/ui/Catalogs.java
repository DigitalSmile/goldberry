package io.github.digitalsmile.goldberry.example.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;

/// The two category tables the build compiles for the Icons and Emoji sheets,
/// read once.
///
/// Compiled by `:assets`' `PrepareCatalogs` from Lucide's per-icon metadata and
/// Unicode's `emoji-test.txt`, into this module's own `catalog` package
/// ([ADR-0387]). Read lazily and kept, through a holder per table: a showcase
/// that never opens either sheet never parses either file.
///
/// A table that is missing is an **empty** table and not an exception: the
/// sheets then show everything under one "Other" heading, which is a showcase
/// built without its asset step looking slightly wrong rather than a window
/// that will not open. `CatalogsTest` is what makes sure the real build does
/// not do that.
final class Catalogs {

    /// Where an emoji Unicode files under no group goes, and where every emoji
    /// goes when the table is missing.
    static final String OTHER = "Other";

    private static final String ROOT = "/io/github/digitalsmile/goldberry/example/catalog/";

    private Catalogs() {}

    private static final class Icons {
        private static final SequencedMap<String, List<String>> TABLE = readIcons();
    }

    private static final class Emoji {
        private static final SequencedMap<Integer, String> TABLE = readEmoji();
    }

    /// Each icon's categories, by name, in name order.
    static SequencedMap<String, List<String>> iconCategories() {
        return Icons.TABLE;
    }

    /// Each emoji's Unicode group, by code point, in **Unicode's emoji order** —
    /// which is the order a sheet should show them in, and not code point order.
    static SequencedMap<Integer, String> emojiGroups() {
        return Emoji.TABLE;
    }

    private static SequencedMap<String, List<String>> readIcons() {
        var table = new TreeMap<String, List<String>>();
        for (var line : lines("lucide-categories.txt")) {
            var tab = line.indexOf('\t');
            if (tab > 0) {
                table.put(
                        line.substring(0, tab), List.of(line.substring(tab + 1).split(",")));
            }
        }
        return java.util.Collections.unmodifiableSequencedMap(table);
    }

    private static SequencedMap<Integer, String> readEmoji() {
        var table = new LinkedHashMap<Integer, String>();
        for (var line : lines("emoji-groups.txt")) {
            var tab = line.indexOf('\t');
            if (tab > 0) {
                try {
                    table.putIfAbsent(Integer.parseInt(line.substring(0, tab), 16), line.substring(tab + 1));
                } catch (NumberFormatException e) {
                    // Not a line this file writes; skipped rather than trusted.
                }
            }
        }
        return java.util.Collections.unmodifiableSequencedMap(table);
    }

    /// The table's data lines — comments and blanks dropped — or none when the
    /// file is not there.
    private static List<String> lines(String file) {
        try (var in = Catalogs.class.getResourceAsStream(ROOT + file)) {
            if (in == null) {
                return List.of();
            }
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the " + file + " table could not be read", e);
        }
    }

    /// Every category an icon table names, in name order, and the icons in each
    /// in name order — the Icons sheet's groups.
    ///
    /// An icon in three categories is in three groups: that is what Lucide's own
    /// site does, and a reader looking under "Arrows" for an arrow that is also
    /// a "Navigation" icon should find it there.
    static List<CategorySheet.Group<String>> iconGroups(Map<String, List<String>> table) {
        var byCategory = new TreeMap<String, java.util.ArrayList<String>>();
        for (var icon : table.entrySet()) {
            for (var category : icon.getValue()) {
                byCategory
                        .computeIfAbsent(category, _ -> new java.util.ArrayList<>())
                        .add(icon.getKey());
            }
        }
        return byCategory.entrySet().stream()
                .map(entry -> new CategorySheet.Group<String>(
                        entry.getKey(), entry.getValue().stream().sorted().toList()))
                .toList();
    }

    /// `entries` filed under their Unicode groups, in the groups' own order and
    /// in Unicode's emoji order inside each — with anything Unicode does not
    /// group, in code point order, under [#OTHER] at the end.
    static <E> List<CategorySheet.Group<E>> emojiGroups(
            List<E> entries, java.util.function.ToIntFunction<E> codePoint, SequencedMap<Integer, String> table) {
        var rank = new java.util.HashMap<Integer, Integer>(table.size() * 2);
        var index = 0;
        for (var point : table.keySet()) {
            rank.put(point, index++);
        }
        var byGroup = new LinkedHashMap<String, java.util.ArrayList<E>>();
        for (var group : table.values()) {
            byGroup.putIfAbsent(group, new java.util.ArrayList<>());
        }
        byGroup.put(OTHER, new java.util.ArrayList<>());
        for (var entry : entries) {
            var group = table.getOrDefault(codePoint.applyAsInt(entry), OTHER);
            byGroup.get(group).add(entry);
        }
        // Unicode's order where Unicode has one; after it, in code point order,
        // whatever it does not list.
        java.util.Comparator<E> order = java.util.Comparator.comparingInt(entry -> {
            var point = codePoint.applyAsInt(entry);
            var at = rank.get(point);
            return at != null ? at : table.size() + point;
        });
        return byGroup.entrySet().stream()
                .filter(group -> !group.getValue().isEmpty())
                .map(group -> new CategorySheet.Group<>(
                        group.getKey(), group.getValue().stream().sorted(order).toList()))
                .toList();
    }
}
