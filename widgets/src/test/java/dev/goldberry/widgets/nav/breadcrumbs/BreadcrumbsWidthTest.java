package dev.goldberry.widgets.nav.breadcrumbs;

import static dev.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.overflow.OverflowLog;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.text.Paragraph;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;

/// **A trail of two whose last crumb is wider than the row** ends that crumb in
/// a `…`, where it used to paint past the trail's edge.
///
/// The overflow menu counts crumbs and cannot answer one crumb too wide, and
/// `crumb { flex-shrink: 0 }` then guaranteed an overrun. What changed is that
/// the current crumb gives way and its label carries the crumb's `nowrap` and
/// `ellipsis` — and what must not change is that the crumbs before it keep their
/// whole names.
class BreadcrumbsWidthTest {

    private static final String FIRST = "Chat";

    /// A channel name nothing at this width fits.
    private static final String CURRENT = "#platform-announcements-and-the-rest";

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        OverflowLog.forget();
    }

    @AfterEach
    void tearDown() {
        OverflowLog.forget();
    }

    /// Every text box of a two-crumb trail laid out `width` wide.
    private static List<Placed> textBoxes(int width) {
        var target = TestFrames.of(width, 48, 1.0f, 0);
        var page = new Column(
                List.of(new Breadcrumbs(new Crumb(FIRST, () -> {}), new Crumb(CURRENT, () -> {}))), id("page"));
        var tree = new ElementTree(page);
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#page { width: " + width + "px }"));
        var renderer = new WidgetRenderer(sheets, TestFont.get());
        var found = new ArrayList<Placed>();
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            render.forEachPlacedBox(placed -> {
                if (placed.box().text() != null) {
                    found.add(new Placed(placed.box(), placed.layout().width()));
                }
            });
        }
        return List.copyOf(found);
    }

    private record Placed(Box box, float width) {

        String text() {
            return box.text().paragraph().text();
        }

        double natural() {
            return box.text().paragraph().layout(Paragraph.UNCONSTRAINED).width();
        }
    }

    private static Placed label(List<Placed> boxes, String text) {
        return boxes.stream()
                .filter(placed -> text.equals(placed.text()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(text + " was never drawn"));
    }

    @Test
    @DisplayName("two crumbs in 200px report no overrun")
    void noOverrun() {
        textBoxes(200);

        assertEquals(List.of(), OverflowLog.reported(), "the trail painted past its own edge");
    }

    @Test
    @DisplayName("and the current crumb's label is cut, with the mark that says so")
    void theCurrentCrumbEllipsizes() {
        var current = label(textBoxes(200), CURRENT);

        assertTrue(
                current.box().text().flow().ellipsises(), "`crumb { white-space: nowrap; text-overflow: ellipsis }`");
        // Narrower than its text with an ellipsising flow is a line that ends in
        // the mark: `Paragraph` draws up to the last grapheme that leaves room
        // for `…` and then the `…`.
        assertTrue(
                current.width() < current.natural(),
                () -> "the label is " + current.width() + " wide against a natural " + current.natural());
        assertTrue(current.width() > 0, "and something of the name is still there");
    }

    @Test
    @DisplayName("while the crumb before it keeps its whole name")
    void earlierCrumbsKeepTheirWidth() {
        var first = label(textBoxes(200), FIRST);

        assertTrue(
                first.width() >= first.natural(),
                () -> FIRST + " is " + first.width() + " wide against a natural " + first.natural());
    }

    @Test
    @DisplayName("a trail with room for its names cuts nothing")
    void aWideTrailIsUnchanged() {
        var current = label(textBoxes(600), CURRENT);

        // At least its natural width rather than exactly it: Yoga places a box on
        // the pixel grid, so a label that measures 254.9 is laid out in 256. What
        // matters is the direction -- a cut would be under.
        assertTrue(current.width() >= current.natural(), "nothing was taken away");
        assertEquals(List.of(), OverflowLog.reported());
    }
}
