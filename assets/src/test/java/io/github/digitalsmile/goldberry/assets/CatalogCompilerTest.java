package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The category tables the showcase groups its sheets by.
///
/// Every input here is a fragment in the upstream's own shape — a Lucide icon
/// file as Lucide writes one, lines of `emoji-test.txt` as Unicode writes them —
/// so a rule that holds here holds for the real files, and the real files'
/// quirks each have a test named after them.
class CatalogCompilerTest {

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("icon categories")
    class Icons {

        @Test
        @DisplayName("are read out of the icon's own file, in the order Lucide wrote them")
        void readsTheArray() {
            var categories = CatalogCompiler.iconCategories(Map.of(
                    "grid-2x2",
                    json("""
                            {
                              "$schema": "../icon.schema.json",
                              "tags": ["table", "rows"],
                              "categories": [
                                "text",
                                "layout",
                                "design"
                              ],
                              "aliases": ["grid-2-x-2"]
                            }
                            """)));

            assertEquals(List.of("text", "layout", "design"), categories.get("grid-2x2"));
        }

        @Test
        @DisplayName("a tag spelled like a category is a tag, not a category")
        void onlyTheKeyMatches() {
            var categories = CatalogCompiler.iconCategories(Map.of(
                    "odd", json("{ \"tags\": [\"categories\"], \"categories\": [\"shapes\"] }")));

            assertEquals(List.of("shapes"), categories.get("odd"));
        }

        @Test
        @DisplayName("an icon with none is filed under 'other', not dropped")
        void uncategorized() {
            var categories = CatalogCompiler.iconCategories(Map.of(
                    "lonely", json("{ \"tags\": [\"x\"] }"),
                    "empty", json("{ \"categories\": [] }")));

            assertEquals(List.of(CatalogCompiler.UNCATEGORIZED), categories.get("lonely"));
            assertEquals(List.of(CatalogCompiler.UNCATEGORIZED), categories.get("empty"));
        }

        @Test
        @DisplayName("the table is one line per icon, in name order, after a comment")
        void table() {
            var table = CatalogCompiler.iconTable(CatalogCompiler.iconCategories(Map.of(
                    "zap", json("{ \"categories\": [\"weather\", \"devices\"] }"),
                    "anchor", json("{ \"categories\": [\"travel\"] }"))));

            var lines = table.lines().filter(line -> !line.startsWith("#")).toList();
            assertEquals(List.of("anchor\ttravel", "zap\tweather,devices"), lines);
            assertTrue(table.startsWith("# Lucide "), table);
        }

        @Test
        @DisplayName("and the archive's own JSON is found by name, SVGs and directories aside")
        void readsTheArchive(@TempDir Path directory) throws IOException {
            var archive = directory.resolve("lucide.zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("icons/"));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("icons/anchor.svg"));
                zip.write(json("<svg/>"));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("icons/anchor.json"));
                zip.write(json("{ \"categories\": [\"travel\"] }"));
                zip.closeEntry();
            }

            var found = PrepareCatalogs.lucideJson(archive);

            assertEquals(1, found.size());
            assertTrue(found.containsKey("anchor"));
        }

        @Test
        @DisplayName("an archive with no metadata at all is an error, not an empty table")
        void noMetadataIsAnError(@TempDir Path directory) throws IOException {
            var archive = directory.resolve("lucide.zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("icons/anchor.svg"));
                zip.write(json("<svg/>"));
                zip.closeEntry();
            }

            assertThrows(IOException.class, () -> PrepareCatalogs.lucideJson(archive));
        }
    }

    @Nested
    @DisplayName("emoji groups")
    class Emoji {

        private static final String SAMPLE = """
                # emoji-test.txt
                # Version: 17.0

                # group: Smileys & Emotion

                # subgroup: face-smiling
                1F600                                                  ; fully-qualified     # 😀 E1.0 grinning face
                1F602                                                  ; fully-qualified     # 😂 E0.6 face with tears of joy

                # subgroup: face-affection
                263A FE0F                                              ; fully-qualified     # ☺️ E0.6 smiling face
                263A                                                   ; unqualified         # ☺ E0.6 smiling face

                # group: People & Body

                # subgroup: hand-fingers-open
                1F44B                                                  ; fully-qualified     # 👋 E0.6 waving hand
                1F44B 1F3FD                                            ; fully-qualified     # 👋🏽 E1.0 waving hand: medium skin tone

                # group: Animals & Nature
                1F431                                                  ; fully-qualified     # 🐱 E0.6 cat face
                1F408 200D 2B1B                                        ; fully-qualified     # 🐈‍⬛ E13.0 black cat
                1F600                                                  ; fully-qualified     # 😀 a duplicate, which is ignored
                """;

        @Test
        @DisplayName("each single code point is in the group it is listed under")
        void groups() {
            var groups = CatalogCompiler.emojiGroups(SAMPLE);

            assertEquals("Smileys & Emotion", groups.get(0x1F600));
            assertEquals("People & Body", groups.get(0x1F44B));
            assertEquals("Animals & Nature", groups.get(0x1F431));
        }

        @Test
        @DisplayName("a code point and FE0F is that code point; anything longer is a sequence and skipped")
        void sequences() {
            var groups = CatalogCompiler.emojiGroups(SAMPLE);

            assertEquals("Smileys & Emotion", groups.get(0x263A), "263A FE0F is 263A on a sheet of code points");
            assertFalse(groups.containsKey(0x1F3FD), "a skin tone applied is not a tile");
            assertFalse(groups.containsKey(0x1F408), "a ZWJ sequence is not a tile either");
        }

        @Test
        @DisplayName("the order is Unicode's emoji order, and the first listing wins")
        void orderAndFirstWins() {
            var groups = CatalogCompiler.emojiGroups(SAMPLE);

            assertEquals(List.of(0x1F600, 0x1F602, 0x263A, 0x1F44B, 0x1F431), List.copyOf(groups.keySet()));
            assertEquals("Smileys & Emotion", groups.get(0x1F600), "not the duplicate's group");
        }

        @Test
        @DisplayName("lines before the first group, and lines that are not data, are ignored")
        void noise() {
            var groups = CatalogCompiler.emojiGroups("""
                    1F600 ; fully-qualified # before any group
                    # group: Symbols
                    not a line at all
                    ZZZZ ; fully-qualified # not hex
                    2764 FE0F ; fully-qualified # ❤️ red heart
                    """);

            assertEquals(Map.of(0x2764, "Symbols"), groups);
        }

        @Test
        @DisplayName("the table is upper-case hex and a group, in the same order")
        void table() {
            var table = CatalogCompiler.emojiTable(CatalogCompiler.emojiGroups(SAMPLE));

            var lines = table.lines().filter(line -> !line.startsWith("#")).toList();
            assertEquals("1F600\tSmileys & Emotion", lines.getFirst());
            assertEquals("1F431\tAnimals & Nature", lines.getLast());
            assertTrue(table.startsWith("# Unicode "), table);
        }
    }
}
