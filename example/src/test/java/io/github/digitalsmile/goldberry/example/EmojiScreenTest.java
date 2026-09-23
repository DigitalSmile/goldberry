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
import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.EmojiScreen;
import io.github.digitalsmile.goldberry.example.ui.IconsScreen;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.FaceCoverage;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.controls.chip.Chip;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialog;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The Emoji screen, driven rather than photographed — [ADR-0386].
///
/// `GalleryGoldenTest` shows what it looks like, and through a font book so the
/// glyphs are the face's own. What a picture cannot show is where the list came
/// from: that it is the **font's** contents rather than a list somebody typed,
/// that searching it matches Unicode's names, and that the face this application
/// added is the one behind it.
class EmojiScreenTest {

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
        private final ShowcaseModel model;
        private final ShowcaseModel.Actions actions;

        Harness(int width, int height) {
            this(width, height, "");
        }

        /// The same, with a query already typed.
        ///
        /// Set **before** the tree is built, which is what `FrameBudgetTest`
        /// does with the icon sheet and for the same reason: the screen reads the
        /// query in `build`, and the subscription that rebuilds it on a keystroke
        /// is the binding runtime's — woven in an image, reflective in a jar, and
        /// not what this test is about.
        Harness(int width, int height, String query) {
            target = TestFrames.of(width, height, 1.0f, 0);
            fonts = Fonts.bundled();
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
            sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
            renderer = new WidgetRenderer(sheets, fonts);

            var showcase = new Showcase();
            model = modelOf(showcase, ShowcaseModel.class);
            actions = modelOf(showcase, ShowcaseModel.Actions.class);
            actions.setEmojiQuery(query);
            tree = new ElementTree(new EmojiScreen(model, actions), host.host);
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

        /// The sheet's rows of tiles — the `icon-row` in each `list-row` that
        /// is not a heading.
        List<Element> tileRows() {
            return byType("list-row").stream()
                    .map(row -> row.children().getFirst())
                    .filter(row -> row.classes().contains("icon-row"))
                    .toList();
        }

        List<String> headings() {
            return byType("sheet-heading").stream()
                    .map(heading -> heading.children().getFirst().widget().toString())
                    // `SheetHeadingLabel[text=Arrows  ·  200]`, read back to
                    // what is on screen — the record is the showcase's own and
                    // package-private.
                    .map(label -> label.substring(label.indexOf("text=") + 5, label.length() - 1))
                    .toList();
        }

        /// The tiles' Unicode names, in the order the tree holds them — which is
        /// reading order, a row at a time.
        List<String> names() {
            return byType("emoji-tile").stream()
                    .map(tile -> tile.children().getLast().widget().toString())
                    .toList();
        }

        void choose(String id) {
            var chip = byId(id);
            assertNotNull(chip, "no chip " + id);
            ((Chip) chip.widget()).onPress().run();
            settle();
        }

        void click(Element element) {
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
            var rect = found.getFirst();
            var x = rect.left() + rect.size().width() / 2;
            var y = rect.top() + rect.size().height() / 2;
            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            settle();
        }

        void settle() {
            for (var i = 0; i < 4; i++) {
                frame();
            }
        }

        Element byId(String id) {
            return find(tree.root(), id);
        }

        List<Element> byType(String type) {
            var found = new ArrayList<Element>();
            collect(tree.root(), type, found);
            return found;
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
    @DisplayName("the sheet is the face's own contents")
    class Contents {

        @Test
        @DisplayName("the showcase has the emoji artifact, which is what makes this screen possible")
        void theFaceIsThere() {
            // `:example` depends on `:emoji`, and this is the assertion that says
            // so: without the artifact the sheet is empty and the screen says why
            // instead of drawing a sheet of blanks (ADR-0384).
            assertTrue(BundledAssets.hasEmojiFont(), "goldberry-emoji is on the showcase's module path");
        }

        @Test
        @DisplayName("the sheet is built from the face, and the face parsed")
        void everyTileIsInTheFace() {
            var harness = new Harness(1200, 900);
            var covered = FaceCoverage.codePoints(BundledAssets.font(BundledFont.EMOJI));

            var tiles = harness.byType("emoji-tile");
            assertFalse(tiles.isEmpty(), "the sheet built no tiles");
            for (var tile : tiles) {
                var glyph = tile.children().getFirst();
                assertEquals("emoji-glyph", glyph.type());
            }

            // What this holds, stated honestly. `EmojiScreen.entries()` builds its
            // list *from* `FaceCoverage.codePoints`, so "every tile is a character
            // the face has" is true by construction and a per-tile comparison
            // would assert the same call against itself. What can still go wrong
            // is the face not parsing at all — a `cmap` this reader does not
            // understand returns an empty array and the screen draws an empty
            // sheet — so that is the assertion, and the display name above says
            // what the construction gives for free.
            //
            // (This comment used to claim the per-tile comparison was being made.
            // It was not, and it cannot be from here: the tile's character sits
            // on a package-private record, and making it public for a test would
            // be a worse trade than saying so — the 2026-09-18 review, §11.3.)
            assertTrue(covered.length > 1000, "the face reported " + covered.length + " code points");
        }

        @Test
        @DisplayName("the count says how many, and the note names the face and its licence")
        void theCreditIsOnScreen() {
            var harness = new Harness(1200, 900);

            assertNotNull(harness.byId("emoji-count"));
            // What an About box would say, where a reader can see it. Noto's OFL
            // does not require it the way OpenMoji's CC BY-SA did (ADR-0384), and
            // the screen says it anyway (ADR-0456). Read off the widget rather than out of its `toString`: what is on
            // screen is the `Text`'s content, and a record's printed form is a
            // debugging convenience that may stop containing it (the 2026-09-18
            // review, §11.3).
            var note = harness.byType("text").stream()
                    .map(element -> element.widget())
                    .filter(Text.class::isInstance)
                    .map(widget -> ((Text) widget).content())
                    .filter(content -> content != null && content.contains("Noto Color Emoji"))
                    .findFirst();
            assertTrue(note.isPresent(), "the screen names the face somewhere a reader can see");
            assertTrue(note.orElseThrow().contains("Open Font License"), note.orElseThrow());
        }
    }

    @Nested
    @DisplayName("searching")
    class Searching {

        @Test
        @DisplayName("matches Unicode's own names")
        void byName() {
            var all = new Harness(1200, 900).byType("emoji-tile").size();
            var found = new Harness(1200, 900, "hourglass").byType("emoji-tile").size();

            assertTrue(found > 0, "\"hourglass\" is two characters in this face");
            assertTrue(found < all, found + " of " + all + " — a search that matches everything matches nothing");
        }

        @Test
        @DisplayName("and the hex a bug report quotes")
        void byCodePoint() {
            // The other way somebody looks for an emoji: they have `U+231B` and
            // want to know what it is.
            var harness = new Harness(1200, 900, "231b");

            assertEquals(1, harness.byType("emoji-tile").size(), "U+231B is HOURGLASS and nothing else");
        }

        @Test
        @DisplayName("a query nothing matches says so rather than drawing an empty sheet")
        void noMatch() {
            var harness = new Harness(1200, 900, "definitely-not-an-emoji");

            assertTrue(harness.byType("emoji-tile").isEmpty());
            assertNotNull(harness.byId("emoji-empty"));
        }
    }

    @Nested
    @DisplayName("the groups are Unicode's")
    class Groups {

        @Test
        @DisplayName("the sheet is in Unicode's emoji order, not code point order")
        void unicodeOrder() {
            // Code point order would open on U+231A WATCH. Unicode's opens on the
            // grinning face, which is what a picker is meant to show first.
            var harness = new Harness(1200, 900);

            assertTrue(
                    harness.headings().getFirst().startsWith("Smileys & Emotion"),
                    harness.headings().toString());
            assertTrue(
                    harness.names().getFirst().contains("grinning face"),
                    harness.names().getFirst());
        }

        @Test
        @DisplayName("one chip per group, All first, and 'Other' last for what Unicode groups nowhere")
        void aChipPerGroup() {
            var chips = new Harness(1200, 900)
                    .byType("chip").stream().map(Element::id).toList();

            assertEquals("emoji-category-all", chips.getFirst());
            assertTrue(chips.contains("emoji-category-smileys-emotion"), chips.toString());
            assertTrue(chips.contains("emoji-category-animals-nature"), chips.toString());
            assertTrue(chips.contains("emoji-category-flags"), chips.toString());
        }

        @Test
        @DisplayName("choosing a group shows that group alone, in Unicode's order within it")
        void choosingNarrows() {
            var harness = new Harness(1200, 900);

            harness.choose("emoji-category-animals-nature");

            assertEquals(1, harness.headings().size(), harness.headings().toString());
            assertTrue(
                    harness.headings().getFirst().contains("Animals & Nature"),
                    harness.headings().getFirst());
            assertTrue(
                    harness.names().getFirst().contains("monkey face"),
                    harness.names().getFirst());
            var count = ((Text) harness.byId("emoji-count").widget()).content();
            assertTrue(count.endsWith(" in Animals & Nature"), count);
        }

        @Test
        @DisplayName("a search inside a group finds only that group's")
        void searchWithinAGroup() {
            var harness = new Harness(1200, 900, "cat");
            var everywhere = harness.names().size();

            harness.choose("emoji-category-animals-nature");

            assertTrue(harness.names().size() <= everywhere);
            assertTrue(
                    harness.names().stream().allMatch(name -> name.contains("cat")),
                    harness.names().toString());
            assertEquals(1, harness.headings().size());
        }
    }

    @Nested
    @DisplayName("pressing a tile")
    class Specimens {

        @Test
        @DisplayName("opens the emoji at five sizes, and in a line of text at five more")
        void opensTheSizes() {
            var harness = new Harness(1200, 900);

            harness.click(harness.byType("emoji-tile").getFirst());

            var dialog = (Dialog) harness.host.last();
            assertEquals("emoji-specimen", dialog.attributes().id());
            assertEquals("Grinning face", dialog.title(), "titled with Unicode's name");

            var contents = new ElementTree(dialog);
            contents.flush();
            var glyphs = new ArrayList<Element>();
            collect(contents.root(), "specimen-emoji", glyphs);
            assertEquals(5, glyphs.size(), "five sizes");
            assertEquals(
                    List.of("size-16", "size-24", "size-32", "size-48", "size-64"),
                    glyphs.stream()
                            .map(glyph -> glyph.classes().iterator().next())
                            .toList(),
                    "each styled at its own size");

            var lines = new ArrayList<Element>();
            collect(contents.root(), "text", lines);
            var samples = lines.stream()
                    .filter(line -> line.classes().contains("specimen-sample"))
                    .map(line -> ((Text) line.widget()).content())
                    .toList();
            assertEquals(5, samples.size(), "five lines of text");
            assertTrue(samples.stream().allMatch(line -> line.contains("😀")), "each with the emoji in it: " + samples);

            // And how to write it: a KDL block and a Java block, each naming it.
            for (var id : List.of("emoji-specimen-kdl", "emoji-specimen-java")) {
                var block = find(contents.root(), id);
                assertNotNull(block, "no " + id);
                assertTrue(
                        block.children().stream()
                                .map(line -> ((Text) line.widget()).content())
                                .anyMatch(line -> line.contains("😀")),
                        id + " writes the emoji");
            }
        }
    }

    @Nested
    @DisplayName("the layout is the icon sheet's")
    class Layout {

        @Test
        @DisplayName("the rows reflow to the window, on the same rule")
        void reflow() {
            // Shared rather than reimplemented: one arithmetic, two sheets
            // (ADR-0386).
            var wide = new Harness(1200, 900);
            var columns = wide.tileRows().getFirst().children();

            assertEquals(IconsScreen.columnsFor(columns.size() > 0 ? measuredWidth(wide) : 0), columns.size());
        }

        /// What the sheet was laid out at, which is what the column count came
        /// from.
        private double measuredWidth(Harness harness) {
            var sheet = harness.byType("icon-sheet");
            assertFalse(sheet.isEmpty(), "the emoji sheet reuses the icon sheet's measured box");
            var found = new double[1];
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() == sheet.getFirst()) {
                    found[0] = placed.layout().width();
                }
            });
            return found[0];
        }
    }
}
