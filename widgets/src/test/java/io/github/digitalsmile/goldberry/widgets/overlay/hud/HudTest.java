package io.github.digitalsmile.goldberry.widgets.overlay.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.FrameStats;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// `hud` — the numbers, where they come from, and the two ways it can be wrong.
///
/// The interesting half is not the arithmetic ([io.github.digitalsmile.goldberry.FrameRingTest]
/// covers that): it is that the numbers arrive on the **render context** rather
/// than in the widget, which is what lets a bare `hud` node in a document show
/// live figures and lets this test show figures it chose.
class HudTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static WidgetRenderer renderer(FrameStats stats) {
        return new WidgetRenderer(
                        List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, "")),
                        TestFont.get())
                .frames(stats);
    }

    /// The text of every reading in a rendered HUD, in order.
    private static List<String> readings(Box box) {
        return box.children().stream().map(child -> child.text().paragraph().text()).toList();
    }

    @Test
    @DisplayName("a bare hud shows the rate and the paint time")
    void defaultReadings() {
        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500))
                .render(new ElementTree(new Hud()));

        assertEquals(List.of("60 fps", "paint 2.1 ms"), readings(box));
    }

    /// The one that would have been easy to get wrong: `frame` is `1000 / fps`,
    /// so a HUD showing both by default would spend a third of its width
    /// restating its first number.
    @Test
    @DisplayName("the frame interval is available but not shown unless asked for")
    void frameIsOptional() {
        assertEquals(List.of(Reading.FPS, Reading.PAINT), Hud.DEFAULT);

        var box = renderer(FrameStats.of(60, 16.7, 2.1, 500))
                .render(new ElementTree(new Hud(Reading.FPS, Reading.FRAME, Reading.PAINT)));

        assertEquals(List.of("60 fps", "16.7 ms", "paint 2.1 ms"), readings(box));
    }

    /// A zero is a measurement. A HUD rendered with no frame loop over it has not
    /// measured anything, and saying `0 fps` there would be a claim about a loop
    /// that is not being observed at all.
    @Test
    @DisplayName("no frame loop reads as dashes, not as zero")
    void noLoopIsNotZero() {
        var box = renderer(FrameStats.none())
                .render(new ElementTree(new Hud(Reading.FPS, Reading.FRAME, Reading.PAINT)));

        assertEquals(List.of("— fps", "— ms", "paint —"), readings(box));

        // A loop that genuinely stopped dead is a different thing and reads
        // differently, which is the distinction the dashes exist for.
        var stalled = renderer(FrameStats.of(0, 0, 0, 900))
                .render(new ElementTree(new Hud(Reading.FPS)));
        assertEquals(List.of("0 fps"), readings(stalled));
    }

    /// §5's rule about a locale-formatted number inside the toolkit, applied to
    /// numbers that are the toolkit's own: they are formatted the one way that is
    /// the same on every machine, or the golden images below are a lottery.
    @Test
    @DisplayName("the numbers are formatted in the root locale, whatever the machine's is")
    void rootLocale() {
        var previous = Locale.getDefault();
        try {
            // A locale whose decimal separator is a comma, which is what would
            // turn `16.7 ms` into `16,7 ms` on a CI runner in Berlin.
            Locale.setDefault(Locale.GERMANY);
            var box = renderer(FrameStats.of(60, 16.7, 2.1, 500))
                    .render(new ElementTree(new Hud(Reading.FRAME)));

            assertEquals(List.of("16.7 ms"), readings(box));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("each reading is selectable on its own")
    void readingsAreParts() {
        var hud = new Hud(Reading.FPS, Reading.PAINT);
        var children = hud.children();

        assertEquals("hud", hud.cssType());
        assertEquals(2, children.size());
        assertEquals("hud-reading", ((Styled) children.getFirst()).cssType());
        assertTrue(((Styled) children.getFirst()).classes().contains("fps"));
        assertTrue(((Styled) children.get(1)).classes().contains("paint"));
    }

    @Test
    @DisplayName("a document writes `hud`, with or without a list of readings")
    void fromKdl() {
        var bare = Widgets.inflater().inflate(KdlParser.parse("hud").getFirst());
        assertInstanceOf(Hud.class, bare);
        assertEquals(Hud.DEFAULT, ((Hud) bare).readings());

        var chosen = Widgets.inflater()
                .inflate(KdlParser.parse("hud readings=\"fps frame\" class=\"dim\"").getFirst());
        assertEquals(List.of(Reading.FPS, Reading.FRAME), ((Hud) chosen).readings());
        assertTrue(((Hud) chosen).classes().contains("dim"));
    }

    @Test
    @DisplayName("a reading nobody has heard of is a refusal that names the ones there are")
    void refusesAnUnknownReading() {
        var refused = assertThrows(IllegalArgumentException.class,
                () -> Widgets.inflater().inflate(KdlParser.parse("hud readings=\"gpu\"").getFirst()));

        assertTrue(refused.getMessage().contains("fps"), refused.getMessage());
    }

    @Test
    @DisplayName("an empty list of readings is the default, not an empty hud")
    void emptyIsTheDefault() {
        assertEquals(Hud.DEFAULT, new Hud(List.of(), Attributes.NONE).readings());
    }
}
