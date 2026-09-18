package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.ui.Console;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The Navigation screen's console card, driven rather than photographed.
///
/// [ScrollingScreenTest]'s argument exactly: a golden can show that the card
/// opened on its newest line, and everything else this card is *for* only
/// happens when a line arrives. The three statements below are the three G48
/// asked for, made against the real card with the real stylesheet rather than
/// against a viewport built for a test (ADR-0392).
class ConsoleScreenTest {

    /// How near two painted positions count as the same — noise, not tolerance.
    private static final double STILL = 1.0;

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

        /// A virtual clock, so a fade or a glide lands in the frames [#settle]
        /// runs rather than whenever the machine gets there.
        private final Clock.Virtual clock = Clock.virtual();

        Harness() {
            target = TestFrames.of(560, 420, 1.0f, 0);
            fonts = Fonts.bundled();
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
            sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css"));
            renderer = new WidgetRenderer(sheets, fonts).clock(clock);
            tree = new ElementTree(new Console());
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 560, 420));
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
                clock.advance(60);
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

        /// Where the content has been moved to, which is the offset with its
        /// sign turned round.
        double contentTop() {
            var found = new ArrayList<Double>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && "scroll-content".equals(element.type())) {
                    var m = placed.transform();
                    var l = placed.layout();
                    found.add(m.b() * l.left() + m.d() * l.top() + m.f());
                }
            });
            assertEquals(1, found.size(), "expected exactly one scroll-content");
            return found.getFirst();
        }

        /// The top of the log's frame, which everything on screen is measured
        /// against.
        double listTop() {
            return rectOf(byId("console-demo")).top();
        }

        /// The text of the first line drawn wholly inside the frame — the line
        /// the reader's eye is on.
        String firstWholeLine() {
            return lines().values().iterator().next();
        }

        /// Where `line` is drawn.
        double topOf(String line) {
            for (var each : lines().entrySet()) {
                if (each.getValue().equals(line)) {
                    return each.getKey();
                }
            }
            throw new AssertionError("no line on screen reading " + line);
        }

        /// Every log line drawn at or below the top of the frame, by where it
        /// was drawn.
        private SortedMap<Double, String> lines() {
            var top = listTop();
            var found = new TreeMap<Double, String>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && element.widget() instanceof Text text) {
                    var m = placed.transform();
                    var l = placed.layout();
                    var y = m.b() * l.left() + m.d() * l.top() + m.f();
                    if (y >= top) {
                        found.put(y, text.content());
                    }
                }
            });
            return found;
        }

        void wheel(float notches) {
            var list = rectOf(byId("console-demo"));
            router.pointerWheel(list.left() + 40, list.top() + list.size().height() / 2, 0, notches, Modifiers.NONE);
            settle();
        }

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
    @DisplayName("the card opens on its newest line rather than its oldest")
    void opensAtTheEnd() {
        var harness = new Harness();

        assertNotNull(harness.byId("console-demo"), "no log");
        assertNotNull(harness.byId("console-log"), "no toolbar");
        // Twenty-four lines in a frame that holds a handful, so a viewport at
        // offset zero would be drawn with its content's top at the list's top.
        assertTrue(
                harness.contentTop() < harness.listTop() - 50,
                "it opened at the top; the content is at " + harness.contentTop());
    }

    @Test
    @DisplayName("a line logged while you are at the bottom keeps you at the bottom")
    void followsTheEnd() {
        var harness = new Harness();
        var was = harness.contentTop();

        harness.click("console-log");

        assertTrue(harness.contentTop() < was - 1, "the view did not follow the new line");
    }

    @Test
    @DisplayName("but one logged while you are reading history does not move you")
    void readingHistoryIsNotMoved() {
        var harness = new Harness();
        harness.wheel(-2);
        var reading = harness.contentTop();

        harness.click("console-log");

        assertEquals(reading, harness.contentTop(), STILL, "it dragged the reader back down");
    }

    @Test
    @DisplayName("and loading older lines leaves the words you are reading where they are")
    void olderLinesKeepTheLine() {
        var harness = new Harness();
        harness.wheel(-2);
        var line = harness.firstWholeLine();
        var was = harness.topOf(line);
        var offset = harness.contentTop();

        harness.click("console-older");

        // Both halves, because either alone would pass for the wrong reason: the
        // offset has taken up the page that arrived above, and the line the
        // reader was on is drawn exactly where it was.
        assertTrue(harness.contentTop() < offset - 50, "the offset did not take up the lines that arrived above it");
        assertEquals(was, harness.topOf(line), STILL, "the reader's line jumped");
    }
}
