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
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.text.font.FaceCoverage;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
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
            tree = new ElementTree(new EmojiScreen(model, actions));
            render = RenderTree.create();
            settle();
        }

        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
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
            // instead of drawing 1205 blanks (ADR-0384).
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
        @DisplayName("the count says how many, and the note carries the credit CC BY-SA asks for")
        void theCreditIsOnScreen() {
            var harness = new Harness(1200, 900);

            assertNotNull(harness.byId("emoji-count"));
            // The obligation the artifact carries, met where a reader can see it —
            // which is the whole reason the face is an artifact (ADR-0384).
            // Read off the widget rather than out of its `toString`: what is on
            // screen is the `Text`'s content, and a record's printed form is a
            // debugging convenience that may stop containing it (the 2026-09-18
            // review, §11.3).
            var note = harness.byType("text").stream()
                    .map(element -> element.widget())
                    .filter(Text.class::isInstance)
                    .map(widget -> ((Text) widget).content())
                    .filter(content -> content != null && content.contains("OpenMoji"))
                    .findFirst();
            assertTrue(note.isPresent(), "the screen names OpenMoji somewhere a reader can see");
            assertTrue(note.orElseThrow().contains("CC BY-SA"), note.orElseThrow());
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
    @DisplayName("the layout is the icon sheet's")
    class Layout {

        @Test
        @DisplayName("the rows reflow to the window, on the same rule")
        void reflow() {
            // Shared rather than reimplemented: one arithmetic, two sheets
            // (ADR-0386).
            var wide = new Harness(1200, 900);
            var columns =
                    wide.byType("list-row").getFirst().children().getFirst().children();

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
