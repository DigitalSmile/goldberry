package io.github.digitalsmile.goldberry.assets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/// Compiles the **categories** of the bundled icons and emoji into two small
/// tables — what the showcase's sheets are grouped by.
///
/// Neither upstream ships the answer in a shape worth shipping on. Lucide keeps
/// an icon's categories in a JSON file beside each SVG — 1544 files, most of
/// their bytes tags and contributors — and Unicode keeps an emoji's group in
/// `emoji-test.txt`, 650 KB of sequences the sheet never shows. What a sheet
/// needs is one line per entry saying where it goes, so that is what this
/// writes, and the reasons ADR-0033 gave for compiling the icons apply as they
/// stand: compiled at build time, pinned by checksum, never committed.
///
/// Pure functions of their input, so every rule here is a test rather than a
/// comment saying it works — which is why `:assets` is Java at all.
public final class CatalogCompiler {

    /// Where an icon with no categories at all goes. Lucide has four.
    public static final String UNCATEGORIZED = "other";

    /// `"categories": [ … ]`, and the array's body.
    ///
    /// A pattern rather than a JSON parser, and deliberately: the files are
    /// Lucide's own, generated, one shape, and a parser would be a dependency
    /// of a build tool for one array of strings. The pattern is anchored on the
    /// key so that a tag that happens to be called "categories" in a *value*
    /// cannot match.
    private static final Pattern CATEGORIES =
            Pattern.compile("\"categories\"\\s*:\\s*\\[([^\\]]*)]");

    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private CatalogCompiler() {}

    // ------------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------------

    /// Each icon's categories, by icon name, in name order.
    ///
    /// The categories keep the order Lucide wrote them in, which is the order
    /// its own site lists them. An icon whose file names none is filed under
    /// [#UNCATEGORIZED] rather than dropped: a sheet grouped by category must
    /// still show every icon somewhere.
    ///
    /// @param json icon name → the bytes of its `.json` file
    public static SequencedMap<String, List<String>> iconCategories(Map<String, byte[]> json) {
        Objects.requireNonNull(json, "json");
        var categories = new TreeMap<String, List<String>>();
        for (var icon : json.entrySet()) {
            var text = new String(icon.getValue(), java.nio.charset.StandardCharsets.UTF_8);
            var found = new ArrayList<String>();
            var array = CATEGORIES.matcher(text);
            if (array.find()) {
                var strings = STRING.matcher(array.group(1));
                while (strings.find()) {
                    var category = strings.group(1).strip();
                    if (!category.isEmpty() && !found.contains(category)) {
                        found.add(category);
                    }
                }
            }
            categories.put(icon.getKey(), found.isEmpty() ? List.of(UNCATEGORIZED) : List.copyOf(found));
        }
        return categories;
    }

    /// The icon table: `name<TAB>category,category`, one icon per line, after a
    /// comment saying where it came from.
    public static String iconTable(SequencedMap<String, List<String>> categories) {
        var out = new StringBuilder(categories.size() * 32);
        out.append("# Lucide ").append(Asset.LUCIDE.version()).append(" icon categories. ISC licence: licenses/lucide.txt\n");
        out.append("# <name>\\t<category>[,<category>...], in the order Lucide lists them.\n");
        for (var icon : categories.entrySet()) {
            out.append(icon.getKey()).append('\t').append(String.join(",", icon.getValue())).append('\n');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------------
    // Emoji
    // ------------------------------------------------------------------------

    /// Which group each **single-code-point** emoji is in, in Unicode's own
    /// emoji order.
    ///
    /// ## What counts as single
    ///
    /// A line whose sequence is one code point, or one code point and
    /// `U+FE0F` — the variation selector that asks for the picture. `☺️` is
    /// written `263A FE0F` and is the same entry on a sheet of code points as
    /// `263A` is. Anything longer is a sequence — a family, a flag, a skin tone
    /// applied — and a sheet built from a font's `cmap` has no tile for it.
    ///
    /// ## The order
    ///
    /// Insertion order is the file's, which is **Unicode's emoji order**: the
    /// grinning face before the tears of joy, the cat before the lion. That is
    /// the order a picker is meant to show, and the reason a group's sheet
    /// should not be sorted by code point.
    ///
    /// The first line for a code point wins, so its fully-qualified form — which
    /// the file writes first — is what decides its group.
    ///
    /// @param emojiTest the text of `emoji-test.txt`
    public static SequencedMap<Integer, String> emojiGroups(String emojiTest) {
        Objects.requireNonNull(emojiTest, "emojiTest");
        var groups = new LinkedHashMap<Integer, String>();
        String group = null;
        for (var raw : emojiTest.split("\n", -1)) {
            var line = raw.strip();
            if (line.startsWith("# group:")) {
                group = line.substring("# group:".length()).strip();
                continue;
            }
            if (line.isEmpty() || line.startsWith("#") || group == null) {
                continue;
            }
            var semicolon = line.indexOf(';');
            if (semicolon < 0) {
                continue;
            }
            var codePoint = single(line.substring(0, semicolon));
            if (codePoint >= 0) {
                groups.putIfAbsent(codePoint, group);
            }
        }
        return groups;
    }

    /// The one code point `sequence` stands for, or `-1` when it is a real
    /// sequence.
    private static int single(String sequence) {
        var parts = sequence.strip().split("\\s+");
        if (parts.length == 0 || parts.length > 2 || parts[0].isEmpty()) {
            return -1;
        }
        if (parts.length == 2 && !parts[1].equalsIgnoreCase("FE0F")) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[0], 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /// The emoji table: `HEX<TAB>group`, one code point per line, in Unicode's
    /// order, after a comment saying where it came from.
    public static String emojiTable(SequencedMap<Integer, String> groups) {
        var out = new StringBuilder(groups.size() * 24);
        out.append("# Unicode ").append(Asset.UNICODE_EMOJI.version())
                .append(" emoji groups, from emoji-test.txt. Unicode licence: licenses/unicode.txt\n");
        out.append("# <code point in hex>\\t<group>, in Unicode's emoji order.\n");
        for (var entry : groups.entrySet()) {
            out.append(Integer.toHexString(entry.getKey()).toUpperCase(java.util.Locale.ROOT))
                    .append('\t')
                    .append(entry.getValue())
                    .append('\n');
        }
        return out.toString();
    }
}
