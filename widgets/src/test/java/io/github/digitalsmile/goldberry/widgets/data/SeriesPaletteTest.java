package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// The series palette, read from the theme rather than from a table.
///
/// What is being checked is the *mechanism* as much as the values: that a widget
/// can reach a custom property resolved for its own node, that a rule beats the
/// theme, and that the slots stay in the order the CVD search fixed.
class SeriesPaletteTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// The eight slots as a chart would read them, under `theme` plus `extra`.
    private static List<Integer> slots(Theme theme, String extra) {
        var seen = new ArrayList<Integer>();
        var sheets = new ArrayList<Stylesheet>(List.of(Controls.baseStylesheet(), theme.load()));
        if (extra != null) {
            sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, extra));
        }
        var tree = new ElementTree(new Probe(seen));
        new WidgetRenderer(sheets, TestFont.get()).render(tree);
        return seen;
    }

    /// A widget whose whole job is to read the eight slots during `render`.
    private record Probe(List<Integer> into)
            implements io.github.digitalsmile.goldberry.widget.Widget.Leaf,
                    io.github.digitalsmile.goldberry.widget.style.Styled,
                    io.github.digitalsmile.goldberry.widget.style.Paints {

        @Override
        public String cssType() {
            return "line-chart";
        }

        @Override
        public String id() {
            return "plot";
        }

        @Override
        public Box render(
                io.github.digitalsmile.goldberry.css.ComputedStyle style, List<Box> children, Context context) {
            for (var i = 0; i < SeriesPalette.SLOTS; i++) {
                into.add(SeriesPalette.of(context, i));
            }
            return Box.of().style(style);
        }
    }

    @Test
    @DisplayName("reads eight distinct colours from the dark theme")
    void darkThemeSlots() {
        var slots = slots(Theme.NORD_DARK, null);

        assertEquals(8, slots.size());
        assertEquals(8, Set.copyOf(slots).size(), "eight slots must be eight colours: " + slots);
        assertEquals(0xFF73A340, slots.getFirst(), "slot 1 is the derived nord14 green");
        assertEquals(0xFF06A7A7, slots.getLast(), "slot 8 is the derived nord7 teal");
    }

    @Test
    @DisplayName("the light theme is its own steps, not the dark ones")
    void lightThemeIsNotAFlip() {
        var light = slots(Theme.NORD_LIGHT, null);
        var dark = slots(Theme.NORD_DARK, null);

        assertEquals(8, Set.copyOf(light).size());
        assertEquals(0xFF679732, light.getFirst());
        for (var i = 0; i < 8; i++) {
            assertNotEquals(light.get(i), dark.get(i), "slot " + (i + 1) + " should be stepped per theme, not shared");
        }
    }

    @Test
    @DisplayName("a rule beats the theme, which is the whole reason this is CSS")
    void anApplicationCanOverrideOneSlot() {
        // The property a Java palette table would not have: one chart's first
        // series recoloured by an ordinary rule, and nothing else touched.
        var slots = slots(Theme.NORD_DARK, "#plot { --gb-chart-1: #b48ead }");

        assertEquals(0xFFB48EAD, slots.getFirst(), "the rule wins");
        assertEquals(0xFFC46FB7, slots.get(1), "and the rest are still the theme's");
    }

    @Test
    @DisplayName("falls back to a colour rather than to nothing")
    void noThemeIsStillEightColours() {
        // A chart rendered against no theme at all -- a test, a bare tree -- must
        // draw eight distinguishable series rather than eight black lines.
        var seen = new ArrayList<Integer>();
        var tree = new ElementTree(new Probe(seen));
        new WidgetRenderer(List.of(Controls.baseStylesheet()), TestFont.get()).render(tree);

        assertEquals(8, Set.copyOf(seen).size(), "still eight colours: " + seen);
        for (var colour : seen) {
            assertTrue((colour >>> 24) == 0xFF, "and every one opaque: " + Integer.toHexString(colour));
        }
    }

    @Test
    @DisplayName("does not cycle past the eighth slot")
    void aNinthSeriesIsNotANewHue() {
        var seen = new ArrayList<Integer>();
        var tree = new ElementTree(new Probe(seen));
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        renderer.render(tree);

        // A ninth series repeating slot 8 is a deliberately visible wrong
        // answer: it means the chart needs folding into "Other", and it should
        // look like it.
        assertEquals(seen.get(7), lastSlotFor(renderer, 8), "a ninth series must not wrap to slot 1");
        assertEquals(seen.get(7), lastSlotFor(renderer, 40));
        assertThrows(IllegalArgumentException.class, () -> lastSlotFor(renderer, -1));
    }

    /// Renders a probe that reads exactly one slot, and answers what it read.
    private static int lastSlotFor(WidgetRenderer renderer, int index) {
        var seen = new ArrayList<Integer>();
        renderer.render(new ElementTree(new OneSlot(seen, index)));
        return seen.getFirst();
    }

    private record OneSlot(List<Integer> into, int index)
            implements io.github.digitalsmile.goldberry.widget.Widget.Leaf,
                    io.github.digitalsmile.goldberry.widget.style.Styled,
                    io.github.digitalsmile.goldberry.widget.style.Paints {

        @Override
        public String cssType() {
            return "line-chart";
        }

        @Override
        public Box render(
                io.github.digitalsmile.goldberry.css.ComputedStyle style, List<Box> children, Context context) {
            into.add(SeriesPalette.of(context, index));
            return Box.of().style(style);
        }
    }
}
