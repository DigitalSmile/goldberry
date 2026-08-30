package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.Notifications;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.core.Column;

/// The Notifications screen, driven rather than photographed.
///
/// `GalleryGoldenTest` can show four banners sitting there. It cannot show the
/// screen's actual subject, which is that **a banner arrives when an application
/// describes one and goes when it stops** — the reason this screen is Java and
/// the reason a `message` takes no `bind=`
/// ([ADR-0175](../../../../../../book/src/adr/0175-a-banner-says-its-kind-twice.md)).
///
/// So this presses the buttons.
class NotificationsScreenTest {

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

    /// Tall enough that every banner this test spawns is laid out and painted —
    /// a node clipped out of the frame has no hit region, and the × would then
    /// be unclickable for a reason that has nothing to do with the widget.
    private static final int HEIGHT = 1400;

    private final class Harness {

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();

        Harness() {
            target = TestFrames.of(900, HEIGHT, 1.0f, 0);
            fonts = Fonts.bundled();
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
            sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
            renderer = new WidgetRenderer(sheets, fonts);
            // The three cards as the Overlays screen's wall offers them, in a
            // plain column rather than a `masonry`: what this test asserts is
            // which banners are described, and a wall would put them in three
            // columns without changing one of those answers (ADR-0222).
            tree = new ElementTree(
                    new Column(Notifications.cards().toArray(io.github.digitalsmile.goldberry.widget.Widget[]::new)));
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 900, HEIGHT));
            settle();
        }

        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        /// Enough frames for a rebuild, a re-layout and a 160ms arrival to be
        /// over — the banners transform while they arrive, and a hit region
        /// taken mid-flight is a region in the wrong place.
        void settle() {
            for (var i = 0; i < 8; i++) {
                frame();
            }
        }

        Element byId(String id) {
            return find(tree.root(), id);
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

        void click(String id) {
            var element = byId(id);
            assertNotNull(element, "nothing with id " + id + " is on the screen");
            clickAt(rectOf(element));
        }

        /// The × of the banner with this id, which has no id of its own: it is a
        /// part, and a part is styleable and not constructible (ADR-0065). It is
        /// found by walking the banner's subtree for the node a stylesheet calls
        /// `message-dismiss`.
        void dismiss(String bannerId) {
            var banner = byId(bannerId);
            assertNotNull(banner, "no banner with id " + bannerId);
            var cross = findByCssType(banner, "message-dismiss");
            assertNotNull(cross, bannerId + " has no ×");
            clickAt(rectOf(cross));
        }

        private void clickAt(LogicalRect rect) {
            var x = rect.left() + rect.size().width() / 2;
            var y = rect.top() + rect.size().height() / 2;
            router.pointerMoved(x, y);
            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            settle();
        }

        /// How many banners the screen is describing right now.
        long banners() {
            return count(tree.root(), "message");
        }
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

    private static Element findByCssType(Element from, String cssType) {
        if (from.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                && cssType.equals(styled.cssType())) {
            return from;
        }
        for (var child : from.children()) {
            var found = findByCssType(child, cssType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static long count(Element from, String cssType) {
        var here = from.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                        && cssType.equals(styled.cssType())
                ? 1L
                : 0L;
        for (var child : from.children()) {
            here += count(child, cssType);
        }
        return here;
    }

    /// The five the screen always shows: four kinds and §4's summary.
    private static final long RESIDENT = 5;

    @Test
    @DisplayName("the screen shows the four kinds, a summary, and nothing spawned")
    void structure() {
        var harness = new Harness();

        assertEquals(RESIDENT, harness.banners());
        assertNotNull(harness.byId("notice-kinds"), "no kinds");
        assertNotNull(harness.byId("notice-summary"), "no error summary");
        assertNotNull(harness.byId("notice-bar"), "no spawning bar");
        assertNotNull(harness.byId("notice-empty"), "no empty state");
    }

    @Test
    @DisplayName("a button spawns a banner, and the empty line goes")
    void spawning() {
        var harness = new Harness();

        harness.click("spawn-warning");

        assertEquals(RESIDENT + 1, harness.banners(), "the button described no banner");
        assertNotNull(harness.byId("notice-1"), "the spawned banner has no element");
        assertNull(harness.byId("notice-empty"), "\"Nothing yet\" is still there with something there");
    }

    /// The half `overlays.kdl` could not demonstrate: a `dismiss=` in a document
    /// can only *report*, because what described the banner is the document and a
    /// document does not change. Here the list described it, so the list can stop.
    @Test
    @DisplayName("the × takes the banner away, because the list is what described it")
    void dismissing() {
        var harness = new Harness();
        harness.click("spawn-info");
        assertEquals(RESIDENT + 1, harness.banners());

        harness.dismiss("notice-1");

        assertEquals(RESIDENT, harness.banners(), "the × did not remove it");
        assertNull(harness.byId("notice-1"));
        assertNotNull(harness.byId("notice-empty"), "the empty line did not come back");
    }

    /// The reason a spawned banner's id is its number rather than its position.
    ///
    /// Dismissing from the middle must not hand the removed element to the banner
    /// below it — which is what reconciliation by position does, and what would
    /// show up as the wrong banner disappearing.
    @Test
    @DisplayName("dismissing the middle of three leaves the other two, by key")
    void dismissingFromTheMiddle() {
        var harness = new Harness();
        harness.click("spawn-info");
        harness.click("spawn-warning");
        harness.click("spawn-danger");
        assertEquals(RESIDENT + 3, harness.banners());

        harness.dismiss("notice-2");

        assertEquals(RESIDENT + 2, harness.banners());
        assertNotNull(harness.byId("notice-1"), "the first went instead");
        assertNull(harness.byId("notice-2"), "the middle one is still here");
        assertNotNull(harness.byId("notice-3"), "the last went instead");
    }

    @Test
    @DisplayName("Clear all empties the list, and the numbers do not come round again")
    void clearing() {
        var harness = new Harness();
        harness.click("spawn-info");
        harness.click("spawn-success");

        harness.click("clear-notices");
        assertEquals(RESIDENT, harness.banners());

        // A counter and not `size()`: the next one is 3, so nothing inherits the
        // key — or the element — of a banner that has been dismissed.
        harness.click("spawn-info");
        assertNotNull(harness.byId("notice-3"), "the numbering restarted");
        assertTrue(harness.banners() == RESIDENT + 1);
    }
}
