package io.github.digitalsmile.goldberry.example.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.assets.BundledAssets;

/// The grouping both sheets share, and the tables it is fed from — without a
/// renderer, because every rule here is arithmetic over lists.
class CategorySheetTest {

    private static final List<CategorySheet.Group<String>> GROUPS = List.of(
            new CategorySheet.Group<>("arrows", List.of("arrow-down", "arrow-up", "move")),
            new CategorySheet.Group<>("navigation", List.of("compass", "move")),
            new CategorySheet.Group<>("weather", List.of("cloud")));

    @Nested
    @DisplayName("narrowing")
    class Narrowing {

        @Test
        @DisplayName("All keeps every group, and a search drops the groups it empties")
        void allAndSearch() {
            assertEquals(GROUPS, CategorySheet.narrow(GROUPS, CategorySheet.ALL, name -> true));

            var moved = CategorySheet.narrow(GROUPS, CategorySheet.ALL, name -> name.contains("move"));
            assertEquals(
                    List.of("arrows", "navigation"),
                    moved.stream().map(CategorySheet.Group::name).toList(),
                    "weather has no 'move' in it and has no heading");
        }

        @Test
        @DisplayName("a category keeps that group alone")
        void oneCategory() {
            var arrows = CategorySheet.narrow(GROUPS, "navigation", name -> true);
            assertEquals(List.of(GROUPS.get(1)), arrows);
        }

        @Test
        @DisplayName("an item in two groups is counted once")
        void distinct() {
            assertEquals(5, CategorySheet.distinct(GROUPS), "'move' is in two groups and is one icon");
        }
    }

    @Nested
    @DisplayName("rows")
    class Rows {

        @Test
        @DisplayName("each group is a heading, then its items in rows of the column count")
        void headingsThenTiles() {
            var rows = CategorySheet.rows(GROUPS, 2, name -> name);

            assertEquals(7, rows.size(), "3 headings, 2 + 1 + 1 rows of tiles");
            var heading = assertInstanceOf(CategorySheet.Heading.class, rows.get(0));
            assertEquals("arrows", heading.group());
            assertEquals(3, heading.count());
            var first = assertInstanceOf(CategorySheet.Tiles.class, rows.get(1));
            assertEquals(List.of("arrow-down", "arrow-up"), first.items());
            var last = assertInstanceOf(CategorySheet.Tiles.class, rows.get(2));
            assertEquals(List.of("move"), last.items(), "the last row of a group holds what is left");
        }

        @Test
        @DisplayName("every row has its own id, even an item heading a row in two groups")
        void idsAreUnique() {
            var rows = CategorySheet.rows(GROUPS, 1, name -> name);
            var ids = rows.stream().map(CategorySheet.SheetRow::id).toList();

            assertEquals(ids.size(), ids.stream().distinct().count(), "ids repeat: " + ids);
        }

        @Test
        @DisplayName("a sheet has a column")
        void noColumns() {
            assertThrows(IllegalArgumentException.class, () -> CategorySheet.rows(GROUPS, 0, name -> name));
        }
    }

    @Test
    @DisplayName("a category reads as a word, and its chip id is a slug of it")
    void labelsAndSlugs() {
        assertEquals("Food & beverage", CategorySheet.label("food-beverage"));
        assertEquals("Arrows", CategorySheet.label("arrows"));
        assertEquals("Smileys & Emotion", CategorySheet.label("Smileys & Emotion"), "already written for reading");

        assertEquals("food-beverage", CategorySheet.slug("food-beverage"));
        assertEquals("smileys-emotion", CategorySheet.slug("Smileys & Emotion"));
        assertEquals("all", CategorySheet.slug(""));
    }

    @Nested
    @DisplayName("the compiled tables")
    class Tables {

        @Test
        @DisplayName("the icon table covers every bundled icon, so none falls under 'other' by accident")
        void iconsAreAllCategorized() {
            var table = Catalogs.iconCategories();

            assertEquals(BundledAssets.iconNames().size(), table.size(), "one line per bundled icon");
            assertTrue(table.keySet().containsAll(BundledAssets.iconNames()), "and the same names");
            assertEquals(List.of("text", "design"), table.get("a-arrow-down"), "in Lucide's own order");
        }

        @Test
        @DisplayName("the emoji table is Unicode's groups, in Unicode's order")
        void emojiGroups() {
            var table = Catalogs.emojiGroups();

            assertTrue(table.size() > 1000, "only " + table.size() + " emoji grouped");
            assertEquals(0x1F600, table.firstEntry().getKey(), "Unicode's order starts at the grinning face");
            assertEquals("Smileys & Emotion", table.get(0x1F600));
            assertEquals("Animals & Nature", table.get(0x1F98A), "the fox");
        }

        @Test
        @DisplayName("emoji are filed in Unicode's order, and anything ungrouped goes under Other, last")
        void filing() {
            var table = new LinkedHashMap<Integer, String>();
            table.put(0x1F602, "Smileys & Emotion");
            table.put(0x1F600, "Smileys & Emotion");
            table.put(0x1F431, "Animals & Nature");

            var groups = Catalogs.emojiGroups(List.of(0x1F431, 0x1F1E6, 0x1F600, 0x1F602), point -> point, table);

            assertEquals(
                    List.of("Smileys & Emotion", "Animals & Nature", Catalogs.OTHER),
                    groups.stream().map(CategorySheet.Group::name).toList());
            assertEquals(
                    List.of(0x1F602, 0x1F600), groups.getFirst().items(), "the table's order, not code point order");
            assertEquals(List.of(0x1F1E6), groups.getLast().items());
        }

        @Test
        @DisplayName("icons are filed under each of their categories, alphabetically")
        void iconFiling() {
            var groups = Catalogs.iconGroups(Map.of(
                    "move", List.of("arrows", "navigation"),
                    "arrow-up", List.of("arrows")));

            assertEquals(
                    List.of(
                            new CategorySheet.Group<>("arrows", List.of("arrow-up", "move")),
                            new CategorySheet.Group<>("navigation", List.of("move"))),
                    groups);
        }
    }
}
