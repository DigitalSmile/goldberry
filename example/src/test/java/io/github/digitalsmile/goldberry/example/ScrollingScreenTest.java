package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.example.ui.Scrolling;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The sixth gallery screen, driven rather than photographed.
///
/// `GalleryGoldenTest` can only show that it lays out: a thumb has faded by the
/// time anything is painted, a sticky header at rest is a header, and a tour has
/// not been started. Everything this screen is *for* only happens when something
/// scrolls, which is what this does.
class ScrollingScreenTest {

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

        Harness() {
            target = TestFrames.of(900, 560, 1.0f, 0);
            fonts = Fonts.bundled();
            var sheets = new ArrayList<Stylesheet>(
                    Controls.stylesheets(Theme.NORD_DARK, io.github.digitalsmile.goldberry.widgets.Density.REGULAR));
            sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
            renderer = new WidgetRenderer(sheets, fonts);
            tree = new ElementTree(new Scrolling(() -> { }));
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 900, 560));
            settle();
        }

        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        void settle() {
            for (var i = 0; i < 6; i++) {
                frame();
            }
        }

        Element byId(String id) {
            return find(tree.root(), id);
        }

        /// Where a node is painted, after every transform above it.
        LogicalRect rectOf(Element element) {
            var found = new ArrayList<LogicalRect>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() == element) {
                    var m = placed.transform();
                    var l = placed.layout();
                    found.add(LogicalRect.of(
                            (float) (m.a() * l.left() + m.c() * l.top() + m.e()),
                            (float) (m.b() * l.left() + m.d() * l.top() + m.f()),
                            l.width(), l.height()));
                }
            });
            assertEquals(1, found.size(), "expected exactly one box for that element");
            return found.getFirst();
        }

        /// Turns the wheel over the middle of the list.
        void wheel(float lines) {
            var list = rectOf(byId("scroll-demo"));
            router.pointerWheel(list.left() + 100,
                    list.top() + list.size().height() / 2, 0, lines, Modifiers.NONE);
            settle();
        }

        /// Clicks a jump button.
        void click(String id) {
            var rect = rectOf(byId(id));
            var x = rect.left() + rect.size().width() / 2;
            var y = rect.top() + rect.size().height() / 2;
            router.pointerMoved(x, y);
            router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1, Modifiers.NONE);
            settle();
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

    @Test
    @DisplayName("the screen builds its viewport, its four sticky headers and its toolbar")
    void structure() {
        var harness = new Harness();

        assertNotNull(harness.byId("scroll-demo"), "no list");
        assertNotNull(harness.byId("jump-bar"), "no toolbar");
        for (var section : Scrolling.SECTIONS) {
            assertNotNull(harness.byId("section-" + section.toLowerCase()),
                    "no affix for " + section);
        }
    }

    @Test
    @DisplayName("the first header lifts and sticks as its section scrolls under it")
    void headerSticks() {
        var harness = new Harness();
        var first = harness.byId("section-beginnings");
        var list = harness.rectOf(harness.byId("scroll-demo"));
        assertFalse(first.hasState(PseudoClass.AFFIXED), "it was affixed before anything moved");

        harness.wheel(6);

        assertTrue(first.hasState(PseudoClass.AFFIXED),
                ":affixed did not come on when the header lifted");
        // Still inside the viewport, which is the whole promise: the hole has
        // scrolled away and the header has not.
        var header = harness.rectOf(first.children().getFirst().children().getFirst());
        assertTrue(header.top() >= list.top() - 1,
                "the pinned header left the top of the list; it is at " + header.top());
    }

    @Test
    @DisplayName("a jump button brings a section that is far below into view")
    void jumpReveals() {
        var harness = new Harness();
        var list = harness.rectOf(harness.byId("scroll-demo"));

        harness.click("jump-endings");

        // The last section starts about forty rows down, so this is a scroll of
        // most of the document -- and the affix has to end up inside the list.
        var endings = harness.rectOf(harness.byId("section-endings"));
        assertTrue(endings.top() < list.top() + list.size().height() + 1,
                "the last section is still below the fold, at " + endings.top());
    }

    @Test
    @DisplayName("the jump buttons keep working, however many times they are pressed")
    void jumpsRepeatedly() {
        var harness = new Harness();
        var list = harness.rectOf(harness.byId("scroll-demo"));

        // Reported as "the buttons stop working after a few clicks". Alternating
        // ends is the case: each press has somewhere to go, so a press that does
        // nothing is a press that was dropped rather than one already satisfied.
        for (var round = 1; round <= 4; round++) {
            harness.click("jump-endings");
            var endings = harness.rectOf(harness.byId("section-endings"));
            var r = round;
            assertTrue(endings.top() < list.top() + list.size().height() + 1,
                    () -> "round " + r + ": Endings never arrived; it is at " + endings.top());

            harness.click("jump-beginnings");
            var beginnings = harness.rectOf(harness.byId("section-beginnings"));
            assertTrue(beginnings.top() >= list.top() - 1
                            && beginnings.top() < list.top() + list.size().height() + 1,
                    () -> "round " + r + ": Beginnings never came back; it is at "
                            + beginnings.top());
        }
    }

    @Test
    @DisplayName("a jump acts once, so the user can scroll away from it afterwards")
    void jumpDoesNotHold() {
        var harness = new Harness();
        harness.click("jump-endings");
        var afterJump = harness.rectOf(harness.byId("section-endings")).top();

        harness.wheel(-4);

        assertTrue(harness.rectOf(harness.byId("section-endings")).top() > afterJump + 10,
                "the jump dragged the list back rather than letting go");
    }
}
