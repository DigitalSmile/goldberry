package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Where a slider's marks and its readout actually land, after Yoga has run
/// ([ADR-0080](../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
///
/// [SliderTest] asserts the arithmetic and [SliderGoldenTest] asserts the
/// picture. This is the layer between them, and it exists because the claims the
/// tick marks rest on are **geometric relations between two parts** — a mark
/// under the thumb's centre, a scale that clears the thumb, a groove that does
/// not move when a scale is added. Each of those is a number no stylesheet states
/// and no value assertion can reach: they come out of the flexbox algorithm, and
/// the wrong ones lay out perfectly.
class SliderGeometryTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    /// Every painted rectangle of `content`, laid out `width` wide, by the
    /// element that owns it.
    private List<HitTest.Region> layout(Widget content, int width) {
        target = TestFrames.of(width, 120, 1.0f, 0);
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                /* A column, so `align-items: stretch` gives the
                                   slider the whole width -- a slider in a *row*
                                   collapses to its content, which is not a
                                   slider anyone lays out. */
                                #row { padding: 0; gap: 0; flex-direction: column }
                                """)),
                TestFont.get());
        var tree = new ElementTree(new Widgets.Column(List.of(content),
                new Widgets.Attributes("row", Set.of(), "row")));

        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            regions = HitTest.capture(render);
            root = tree.root();
            return regions;
        }
    }

    private List<HitTest.Region> regions = List.of();
    private Element root;

    /// The rectangle of the first part with this CSS type, in document order.
    private HitTest.Region part(String cssType) {
        return parts(cssType).getFirst();
    }

    private List<HitTest.Region> parts(String cssType) {
        var found = new ArrayList<HitTest.Region>();
        collect(root, cssType, found);
        if (found.isEmpty()) {
            throw new AssertionError("no " + cssType + " was painted");
        }
        return List.copyOf(found);
    }

    private void collect(Element element, String cssType, List<HitTest.Region> found) {
        if (element.widget() instanceof Styled styled && cssType.equals(styled.cssType())) {
            regions.stream()
                    .filter(region -> region.owner() == element)
                    .findFirst()
                    .ifPresent(found::add);
        }
        for (var child : element.children()) {
            collect(child, cssType, found);
        }
    }

    private static double centreX(HitTest.Region region) {
        return region.left() + region.width() / 2;
    }

    /// Where a rectangle was **painted**, which is where it was laid out only
    /// when nothing transformed it.
    ///
    /// A tick mark is moved clear of the thumb by a `transform`, and a transform
    /// deliberately costs no layout (ADR-0068) — so the laid-out rectangle is the
    /// one Yoga produced and says nothing about where the mark ended up. A region
    /// carries the inverse of the matrix it was painted with, for hit testing;
    /// inverting it back is the matrix itself.
    private static double paintedTop(HitTest.Region region) {
        return region.inverse() == null
                ? region.top()
                : region.inverse().invert().mapY(region.left(), region.top());
    }

    private static Slider slider(double fraction, int ticks, String format) {
        return new Slider(0, 100, fraction * 100, 0, ticks, format, null, null, null, false,
                new Widgets.Attributes("s", Set.of(), "s"));
    }

    /// The claim the whole tick layout is built on: a mark names a position the
    /// thumb's **centre** can reach. The row is inset by half a thumb at each end
    /// and the marks sit in zero-width cells, so that the mark's own 2px takes no
    /// part in the spacing — and the check is at the **ends**, because that is
    /// where every version of getting it wrong is furthest out.
    @Test
    @DisplayName("the thumb's centre lands on the first mark at 0 and the last at max")
    void thumbCentresOnTheEndMarks() {
        layout(slider(0, 5, null), 300);
        assertEquals(centreX(part("slider-thumb")), centreX(parts("slider-tick").getFirst()), 0.01,
                "at the minimum the thumb sits on the first mark");

        layout(slider(1, 5, null), 300);
        var marks = parts("slider-tick");
        assertEquals(centreX(part("slider-thumb")), centreX(marks.getLast()), 0.01,
                "and at the maximum, on the last");
    }

    @Test
    @DisplayName("the middle mark is under the thumb at half, on a track of any width")
    void middleMarkIsUnderTheThumb() {
        for (var width : List.of(120, 301, 640)) {
            layout(slider(0.5, 5, null), width);
            assertEquals(centreX(part("slider-thumb")), centreX(parts("slider-tick").get(2)), 0.01,
                    "at " + width + " wide");
        }
    }

    /// A scale under a thumb is a scale you cannot read. The marks hang out of
    /// the bottom of a zero-height row for exactly this, and two pixels of air is
    /// what the stylesheet's 10 buys.
    @Test
    @DisplayName("the marks clear the thumb rather than being drawn under it")
    void marksClearTheThumb() {
        layout(slider(0.5, 5, null), 300);

        var thumb = part("slider-thumb");
        var mark = parts("slider-tick").get(2);

        assertTrue(paintedTop(mark) >= thumb.top() + thumb.height(),
                "the mark is painted at " + paintedTop(mark) + " and the thumb ends at "
                        + (thumb.top() + thumb.height()));
    }

    /// The reason the tick row is zero-height rather than four pixels tall. A
    /// scale that pushed the groove up would put two sliders in one settings
    /// column at different heights, which reads as a layout bug and is one.
    @Test
    @DisplayName("adding a scale does not move the groove")
    void theGrooveDoesNotMove() {
        layout(slider(0.5, 0, null), 300);
        var plain = part("slider-groove");

        layout(slider(0.5, 5, null), 300);
        var ticked = part("slider-groove");

        assertEquals(plain.top(), ticked.top(), 0.01);
        assertEquals(plain.height(), ticked.height(), 0.01);
    }

    /// What [Slider#localPart()] is for, measured rather than argued: the label
    /// takes its width off the track, so the track and the control are two
    /// different rectangles and the value is a position along the first.
    @Test
    @DisplayName("a value label shortens the track and leaves the groove where it is")
    void theLabelShortensTheTrack() {
        layout(slider(0.5, 0, null), 300);
        var plainTrack = part("slider-track");
        var plainGroove = part("slider-groove");

        layout(slider(0.5, 0, "%.0f"), 300);
        var labelled = part("slider-track");
        var label = part("slider-value");

        assertEquals(plainTrack.width() - label.width() - 8, labelled.width(), 0.01,
                "the label's width and the control's 8px gap come off the track");
        assertEquals(plainGroove.top(), part("slider-groove").top(), 0.01,
                "and nothing moves vertically");
    }

    /// The label is fixed-width so that the number inside it cannot resize the
    /// track under a finger that is dragging it — one digit or three, the same
    /// box.
    @Test
    @DisplayName("the track is the same length whatever the label says")
    void theLabelDoesNotBreathe() {
        layout(slider(0.09, 0, "%.0f"), 300);
        var narrow = part("slider-track").width();

        layout(slider(1, 0, "%.0f"), 300);

        assertEquals(narrow, part("slider-track").width(), 0.01, "9 and 100 leave the same track");
    }
}
