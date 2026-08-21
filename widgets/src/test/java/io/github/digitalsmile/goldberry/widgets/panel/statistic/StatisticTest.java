package io.github.digitalsmile.goldberry.widgets.panel.statistic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `statistic` — §5's labelled number ([ADR-0164]).
///
/// Two claims are worth the test: the widget **never formats**, because a
/// locale-aware number formatted inside the toolkit makes a golden that cannot be
/// reproduced on another machine; and a direction is a **class**, because the
/// colour is the stylesheet's decision and a widget that looked up `--gb-success`
/// itself would be the only one in the catalog doing so.
class StatisticTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a label and a value, and nothing else when nothing else is given")
    void minimal() {
        var tree = new ElementTree(new Statistic("Active users", "12,480"));

        assertEquals(1, Described.of(tree, Statistic.StatisticLabel.class).size());
        assertEquals(1, Described.of(tree, Statistic.StatisticValue.class).size());
        assertTrue(Described.of(tree, Statistic.StatisticUnit.class).isEmpty());
        assertTrue(Described.of(tree, Statistic.StatisticDelta.class).isEmpty());
    }

    /// One reading, two boxes: `128 ms` is not two lines.
    @Test
    @DisplayName("the unit sits inside the value, not beside it as a third part")
    void unitIsInsideTheValue() {
        var tree = new ElementTree(new Statistic("Latency", "128").unit("ms"));

        assertEquals(1, Described.of(tree, Statistic.StatisticUnit.class).size());
        assertEquals(1, Described.first(tree, Statistic.StatisticValue.class).children().size());
    }

    /// A widget that looked up `--gb-success` itself would be the only one in the
    /// catalog doing so.
    @Test
    @DisplayName("a direction is a class on the delta, not a colour")
    void directionIsAClass() {
        var up = new Statistic("Users", "1", null, "+1", Statistic.Direction.UP,
                Attributes.NONE);

        assertEquals(Set.of("up"),
                Described.first(new ElementTree(up), Statistic.StatisticDelta.class).classes());
        assertEquals(Set.of("down"),
                Described.first(new ElementTree(up.delta("-1", Statistic.Direction.DOWN)),
                        Statistic.StatisticDelta.class).classes());
        assertEquals(Set.of(),
                Described.first(new ElementTree(up.delta("0", Statistic.Direction.NONE)),
                        Statistic.StatisticDelta.class).classes(),
                "no direction is no class, so it takes the muted default");
    }

    /// The direction names the **sentiment**, not the arithmetic: latency going
    /// down is success, so a fall can legitimately be `down` on a metric where
    /// falling is what you want. Mapping a leading `-` to danger would colour a
    /// latency improvement red.
    @Test
    @DisplayName("a negative delta can be marked either way, because the caller decides")
    void directionIsSentiment() {
        var improvement = new Statistic("Latency", "128", "ms", "-11 ms",
                Statistic.Direction.DOWN, Attributes.NONE);
        var loss = new Statistic("Revenue", "40k", null, "-11%",
                Statistic.Direction.DOWN, Attributes.NONE);

        assertEquals(improvement.direction(), loss.direction(),
                "the widget cannot tell these apart, and does not try");
    }

    @Test
    @DisplayName("the value is a string, taken as given")
    void valueIsAString() {
        var tree = new ElementTree(new Statistic("Users", "12,480"));

        assertEquals("12,480", Described.first(tree, Statistic.StatisticValue.class).text());
    }

    @Test
    @DisplayName("a direction that is not up, down or none is refused")
    void badDirection() {
        assertThrows(IllegalArgumentException.class, () -> Widgets.inflater().inflate(
                KdlParser.parse("statistic label=\"x\" value=\"1\" delta=\"+1\""
                        + " direction=\"sideways\"").getFirst()));
    }

    @Test
    @DisplayName("a statistic inflates from markup")
    void inflates() {
        var widget = Widgets.inflater().inflate(KdlParser.parse(
                "statistic label=\"Latency\" value=\"128\" unit=\"ms\""
                        + " delta=\"-11 ms\" direction=\"down\"").getFirst());
        var it = assertInstanceOf(Statistic.class, widget);

        assertEquals("Latency", it.label());
        assertEquals("128", it.value());
        assertEquals("ms", it.unit());
        assertEquals(Statistic.Direction.DOWN, it.direction());
    }
}
