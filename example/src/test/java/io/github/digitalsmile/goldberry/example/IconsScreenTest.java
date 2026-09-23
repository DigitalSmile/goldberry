package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.IconsScreen;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.controls.chip.Chip;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialog;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The Icons screen, **driven** rather than photographed ([ADR-0309]) — its
/// reflow, its scrolling, its category chips and the dialog a tile opens.
///
/// `GalleryGoldenTest` shows what the sheet looks like at two widths, and that is
/// most of what a picture can prove here. It cannot prove the two things this
/// screen is actually for:
///
/// - **It scrolls.** A thumb has faded by the time a golden is taken, so the only
///   evidence in a picture is content running off the bottom — which is also what
///   a screen that does *not* scroll looks like.
/// - **It reflows.** A narrow picture shows four columns, but not that the count
///   came from a measurement rather than from a constant somebody changed.
class IconsScreenTest {

    private TestFrames.Target target;
    private RenderTree render;
    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        if (render != null) {
            render.close();
            render = null;
        }
        if (target != null) {
            target.end();
            target = null;
        }
        if (fonts != null) {
            fonts.close();
            fonts = null;
        }
    }

    private final class Harness {

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();
        private final RecordingHost host = new RecordingHost();

        Harness(int width, int height) {
            target = TestFrames.of(width, height, 1.0f, 0);
            fonts = Fonts.bundled();
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
            sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
            renderer = new WidgetRenderer(sheets, fonts);

            var showcase = new Showcase();
            var model = modelOf(showcase, ShowcaseModel.class);
            var actions = modelOf(showcase, ShowcaseModel.Actions.class);
            tree = new ElementTree(new IconsScreen(model, actions), host.host);
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, width, height));
            settle();
        }

        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        /// Six frames, which is more than the one `Measured` costs and cheap
        /// insurance against a settle that takes two.
        void settle() {
            for (var i = 0; i < 6; i++) {
                frame();
            }
        }

        Element byId(String id) {
            return find(tree.root(), id);
        }

        /// Every element of `type` in the tree.
        List<Element> byType(String type) {
            var found = new ArrayList<Element>();
            collect(tree.root(), type, found);
            return found;
        }

        /// How many columns the grid came out with.
        ///
        /// Counted off the **element tree** rather than read from the state,
        /// because what is under test is what a reader sees rather than what the
        /// screen believes — and off a *row*, because the sheet is a virtualized
        /// list of rows since [ADR-0316] and the tiles no longer all exist.
        ///
        /// The **first** row, and every row is padded out to the column count
        /// with empty cells, so this counts the row's children rather than the
        /// tiles in it: a part-full last row would answer a different number, and
        /// the count under test is the layout rule rather than the model.
        int columns() {
            var rows = tileRows();
            assertTrue(!rows.isEmpty(), "the sheet built no rows of tiles at all");
            return rows.getFirst().children().size();
        }

        /// The sheet's rows of tiles, in order — the `icon-row` inside each
        /// `list-row` that is not a heading.
        List<Element> tileRows() {
            return byType("list-row").stream()
                    .map(row -> row.children().getFirst())
                    .filter(row -> row.classes().contains("icon-row"))
                    .toList();
        }

        /// The group headings the sheet built, as they read.
        List<String> headings() {
            return byType("sheet-heading").stream()
                    .map(heading -> heading.children().getFirst().widget().toString())
                    // `SheetHeadingLabel[text=Arrows  ·  200]`, read back to
                    // what is on screen — the record is the showcase's own and
                    // package-private.
                    .map(label -> label.substring(label.indexOf("text=") + 5, label.length() - 1))
                    .toList();
        }

        /// Presses the chip with `id`, as a click would.
        void choose(String id) {
            var chip = byId(id);
            assertNotNull(chip, "no chip " + id);
            ((Chip) chip.widget()).onPress().run();
            settle();
        }

        /// What the count beside the field says.
        String count() {
            return ((Text) byId("icon-count").widget()).content();
        }

        /// Clicks the middle of `element`.
        void click(Element element) {
            var rect = rectOf(element);
            var x = rect.left() + rect.size().width() / 2;
            var y = rect.top() + rect.size().height() / 2;
            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            settle();
        }

        LogicalRect rectOf(Element element) {
            var found = new ArrayList<LogicalRect>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() == element) {
                    var m = placed.transform();
                    var l = placed.layout();
                    found.add(LogicalRect.of(
                            (float) (m.a() * l.left() + m.c() * l.top() + m.e()),
                            (float) (m.b() * l.left() + m.d() * l.top() + m.f()),
                            l.width(),
                            l.height()));
                }
            });
            assertEquals(1, found.size(), "expected exactly one box for that element");
            return found.getFirst();
        }

        /// Turns the wheel over the middle of the sheet.
        void wheel(float lines) {
            var viewport = rectOf(byId("icon-viewport"));
            router.pointerWheel(
                    viewport.left() + viewport.size().width() / 2,
                    viewport.top() + viewport.size().height() / 2,
                    0,
                    lines,
                    Modifiers.NONE);
            settle();
        }
    }

    private static <T> T modelOf(Showcase showcase, Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    private static Element find(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collect(Element from, String type, List<Element> into) {
        if (type.equals(from.type())) {
            into.add(from);
        }
        from.children().forEach(child -> collect(child, type, into));
    }

    @Nested
    @DisplayName("the column count follows the window")
    class Reflow {

        /// The arithmetic on its own, which is the whole of the layout rule.
        @Test
        @DisplayName("as many whole tiles as fit, and never fewer than one")
        void theArithmetic() {
            // 152 + 8 per column, less the last column's gap.
            assertEquals(1, IconsScreen.columnsFor(152));
            assertEquals(1, IconsScreen.columnsFor(300), "319 is two tiles' worth less a point");
            assertEquals(2, IconsScreen.columnsFor(312));
            assertEquals(7, IconsScreen.columnsFor(1112));

            // A window narrower than one tile still draws something, because a
            // masonry refuses a column count below one.
            assertEquals(1, IconsScreen.columnsFor(10));

            // A width of zero is **not** a narrow window — it is "no frame has
            // said yet", which is what the first build sees. The default is the
            // honest answer there, and answering 1 would make every sheet open
            // as a single column and reflow on the next frame.
            assertEquals(IconsScreen.DEFAULT_COLUMNS, IconsScreen.columnsFor(0));
            assertEquals(IconsScreen.DEFAULT_COLUMNS, IconsScreen.columnsFor(Double.NaN));
        }

        @Test
        @DisplayName("a wide window gets more columns than a narrow one")
        void widerIsMore() {
            var wide = new Harness(1200, 900).columns();
            tearDown();
            setUp();
            var narrow = new Harness(720, 900).columns();

            assertTrue(wide > narrow, "1200 gave " + wide + " columns and 720 gave " + narrow);
        }

        /// The measurement is what decides it, not the default.
        ///
        /// `DEFAULT_COLUMNS` is seven and a 720-wide window fits four, so a screen
        /// that never read its width would come out at seven here — and the
        /// picture would look plausible, because a row with more tiles than fit
        /// simply makes them narrower.
        @Test
        @DisplayName("and the narrow one is what the width says, not the default")
        void theWidthDecides() {
            var narrow = new Harness(720, 900);

            assertTrue(
                    narrow.columns() < IconsScreen.DEFAULT_COLUMNS,
                    "720 came out at " + narrow.columns() + ", which is the default rather than a measurement");
        }

        @Test
        @DisplayName("every column is a whole tile wide")
        void noColumnIsClipped() {
            var harness = new Harness(1200, 900);
            var tiles = harness.byType("icon-tile");

            // Twenty rather than a hundred: the sheet is virtualized now, so what
            // exists is the window and not the model ([ADR-0316]). A number in the
            // twenties still means several full rows were built.
            assertTrue(tiles.size() > 20, "the sheet built " + tiles.size() + " tiles, which is not a sheet");
            // The tiles in a row share it equally, so a count that over-fills
            // would make every tile narrower than the one the caption was
            // measured for.
            var first = harness.rectOf(tiles.getFirst());
            assertTrue(
                    first.size().width() >= 152,
                    "a tile came out " + first.size().width() + " wide, so the column count over-fills the row");
        }

        /// **The sheet is a window on the model, not the model.** What this screen
        /// cost before [ADR-0316] was 4709 elements whatever a reader could see;
        /// what it costs now follows the viewport, which is the whole of that
        /// record.
        ///
        /// Asserted as a ratio against the model rather than against a number,
        /// so it holds at any window size: 1544 names and a window that shows
        /// fewer than a tenth of them.
        @Test
        @DisplayName("and only the tiles in the viewport are built")
        void onlyTheWindowIsBuilt() {
            var harness = new Harness(1200, 900);
            var tiles = harness.byType("icon-tile").size();

            assertTrue(
                    tiles * 10 < 1544,
                    "the sheet built " + tiles + " of 1544 tiles, which is not a window onto the model");
            // **And the sheet is still as tall as all 1544 of them.** That is what
            // the spacers are for (ADR-0213) and it is the property a reader
            // actually sees: a virtualized list whose height followed its window
            // would have a thumb that grew as you scrolled into it.
            var sheet = harness.rectOf(harness.byType("icon-sheet").getFirst());
            // Headings and tiles alike: a heading is a row of the same pitch.
            var rows = IconsScreen.rowsFor("", "", harness.columns());
            assertEquals(
                    rows * IconsScreen.ROW_PITCH,
                    sheet.size().height(),
                    1.0,
                    "the sheet is " + sheet.size().height() + " tall for " + rows
                            + " rows, so the spacers do not add up to the model");
        }
    }

    @Nested
    @DisplayName("the sheet scrolls")
    class Scrolling {

        /// **The evidence a golden cannot give.** 1544 tiles is far taller than
        /// any window, so the content has to overflow the viewport — and if it
        /// does not, the screen is not scrolling, it is running off the bottom.
        @Test
        @DisplayName("the content is taller than the viewport it is in")
        void itOverflows() {
            var harness = new Harness(1200, 900);

            var viewport = harness.rectOf(harness.byId("icon-viewport"));
            var sheet = harness.rectOf(harness.byType("icon-sheet").getFirst());

            assertTrue(
                    sheet.size().height() > viewport.size().height(),
                    "the sheet is " + sheet.size().height() + " in a "
                            + viewport.size().height()
                            + " viewport, so nothing can scroll — content told to grow is exactly"
                            + " as tall as what it is in");
        }

        @Test
        @DisplayName("and a thumb is drawn for it")
        void aThumbAppears() {
            var harness = new Harness(1200, 900);

            // Faded by the time a golden is taken, which is why this is asserted
            // on the tree rather than on a picture.
            assertTrue(!harness.byType("scroll-thumb").isEmpty(), "no thumb, so the viewport thinks it fits");
        }

        @Test
        @DisplayName("the wheel moves the icons and leaves the field alone")
        void theWheelScrolls() {
            var harness = new Harness(1200, 900);
            var firstTile = harness.byType("icon-tile").getFirst();
            var before = harness.rectOf(firstTile);
            var field = harness.rectOf(harness.byId("icon-search"));

            // Positive is downwards, which is `ScrollingScreenTest`'s convention
            // and the wheel's: the content moves up under a still viewport.
            harness.wheel(6);

            assertTrue(
                    harness.rectOf(firstTile).top() < before.top(),
                    "the first tile did not move, so the wheel reached nothing");
            // The scroll is around the masonry and not around the screen, which
            // is the whole reason the search field is usable on a long sheet.
            assertEquals(
                    field.top(),
                    harness.rectOf(harness.byId("icon-search")).top(),
                    0.5,
                    "the search field scrolled away with the icons");
        }
    }

    @Test
    @DisplayName("the sheet opens on the first category, alphabetical, reading across the row")
    void readingOrder() {
        var harness = new Harness(1200, 900);
        var tiles = harness.byType("icon-tile");
        assertNotNull(tiles);

        assertTrue(
                harness.headings().getFirst().startsWith("Accessibility"),
                "the first heading is the first category: " + harness.headings());

        // The first row is the first names of that category in order. Read off
        // the painted rectangles rather than the element order.
        var firstRow = new ArrayList<Element>();
        var top = harness.rectOf(tiles.getFirst()).top();
        for (var tile : tiles) {
            if (Math.abs(harness.rectOf(tile).top() - top) < 0.5) {
                firstRow.add(tile);
            }
        }
        firstRow.sort(java.util.Comparator.comparingDouble(
                tile -> harness.rectOf(tile).left()));

        var names = firstRow.stream()
                .map(tile -> tile.children().getFirst())
                .map(name -> name.widget().toString())
                .toList();
        assertTrue(names.size() > 1, "only one tile on the first row");
        assertEquals(names.stream().sorted().toList(), names, "a group reads alphabetically across the row");
        assertTrue(names.getFirst().contains("text=accessibility"), "and starts at its top: " + names.getFirst());
    }

    @Nested
    @DisplayName("the categories")
    class Categories {

        @Test
        @DisplayName("are Lucide's own, one chip each, with All first and chosen")
        void aChipPerCategory() {
            var harness = new Harness(1200, 900);
            var chips = harness.byType("chip");
            var categories = IconsScreen.categories();

            assertTrue(categories.size() > 30, "Lucide has over forty categories, and this read " + categories);
            assertEquals(categories.size() + 1, chips.size(), "one chip per category, and All");
            assertEquals("icon-category-all", chips.getFirst().id());
            assertTrue(((Chip) chips.getFirst().widget()).selected(), "the sheet opens on All");
            assertFalse(harness.headings().isEmpty(), "and the sheet opens under headings");
            assertNotNull(harness.byId("icon-category-arrows"));
            assertNotNull(harness.byId("icon-category-food-beverage"), "a hyphenated id is a slug of it");
        }

        @Test
        @DisplayName("choosing one narrows the sheet to it, under one heading")
        void choosingNarrows() {
            var harness = new Harness(1200, 900);

            harness.choose("icon-category-arrows");

            assertEquals(1, harness.headings().size(), "one category, one heading: " + harness.headings());
            assertTrue(
                    harness.headings().getFirst().startsWith("Arrows"),
                    harness.headings().getFirst());
            assertTrue(((Chip) harness.byId("icon-category-arrows").widget()).selected());
            assertTrue(harness.count().endsWith(" in Arrows"), harness.count());

            // And the sheet is exactly as tall as that one category.
            var sheet = harness.rectOf(harness.byType("icon-sheet").getFirst());
            assertEquals(
                    IconsScreen.rowsFor("arrows", "", harness.columns()) * IconsScreen.ROW_PITCH,
                    sheet.size().height(),
                    1.0);
        }

        @Test
        @DisplayName("pressing it again, or pressing All, puts every category back")
        void allComesBack() {
            var harness = new Harness(1200, 900);
            var everything = harness.count();

            harness.choose("icon-category-arrows");
            harness.choose("icon-category-arrows");
            assertEquals(everything, harness.count(), "pressing the chosen chip takes the filter off");

            harness.choose("icon-category-weather");
            harness.choose("icon-category-all");
            assertEquals(everything, harness.count(), "and All is every category");
            assertTrue(harness.headings().size() > 1);
        }

        @Test
        @DisplayName("an icon in several categories is under each of them, and counted once")
        void iconsRepeatAcrossCategories() {
            var groups = IconsScreen.categories().size();
            // Lucide files most icons under two or three categories, so the
            // grouped sheet has more tile rows than a flat one would — and the
            // count beside the field still says 1544.
            var flat = (int) Math.ceil(1544.0 / IconsScreen.DEFAULT_COLUMNS);
            assertTrue(
                    IconsScreen.rowsFor("", "", IconsScreen.DEFAULT_COLUMNS) > flat + groups,
                    "the grouped sheet should repeat icons under each of their categories");
            assertEquals("1544 icons", new Harness(1200, 900).count());
        }
    }

    @Nested
    @DisplayName("pressing a tile")
    class Specimens {

        @Test
        @DisplayName("opens a dialog of that icon at five sizes")
        void opensTheSizes() {
            var harness = new Harness(1200, 900);
            var tile = harness.byType("icon-tile").getFirst();
            var name = tile.children().getFirst().widget().toString();

            harness.click(tile);

            var dialog = (Dialog) harness.host.last();
            assertEquals("icon-specimen", dialog.attributes().id());
            assertTrue(name.contains("text=" + dialog.title() + "]"), dialog.title() + " is the tile pressed: " + name);

            var contents = new ElementTree(dialog);
            contents.flush();
            var cells = new ArrayList<Element>();
            collect(contents.root(), "specimen-cell", cells);
            assertEquals(5, cells.size(), "five sizes");
            var icons = new ArrayList<Element>();
            collect(contents.root(), "specimen-icon", icons);
            assertEquals(5, icons.size(), "an icon in each");
        }

        @Test
        @DisplayName("and a tile is a focusable button, so the keyboard can open it too")
        void aTileIsAButton() {
            var harness = new Harness(1200, 900);
            var tile = harness.byType("icon-tile").getFirst().widget();

            assertTrue(((Handles) tile).isFocusable(), "Tab reaches a tile");
            assertEquals(Role.BUTTON, ((Semantics) tile).role());
            assertNotNull(((Semantics) tile).accessibleName(), "and it announces its name");
        }
    }
}
