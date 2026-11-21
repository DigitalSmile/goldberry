package dev.goldberry.css.compositing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;

/// `backdrop-filter`, computed: the `filter` functions, kept apart from
/// `filter`, and no group made for them.
@DisplayName("backdrop-filter, computed")
class BackdropFilterStyleTest {

    private static Compositing compositing(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        for (var declaration : CssParser.parse("x { " + css + " }").getFirst().declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return ComputedStyle.of(Map.copyOf(map), CssLength.Context.DEFAULT)
                .presence()
                .compositing();
    }

    @Test
    @DisplayName("reads the filter functions in the order written")
    void list() {
        var compositing = compositing("backdrop-filter: blur(24px) saturate(110%)");
        assertEquals(
                List.of(new FilterFunction.Blur(24), new FilterFunction.Saturate(1.1)), compositing.backdropFilter());
        assertTrue(compositing.filter().isEmpty(), "and leaves filter alone");
        assertTrue(compositing.hasBackdrop());
    }

    @Test
    @DisplayName("does not make the box a group")
    void noGroup() {
        assertFalse(compositing("backdrop-filter: blur(4px)").needsLayer());
        assertTrue(compositing("backdrop-filter: blur(4px); filter: blur(1px)").needsLayer());
    }

    @Test
    @DisplayName("reads past the box by three radii, and the colour functions not at all")
    void reach() {
        assertArrayEquals(
                new double[] {12, 12, 12, 12},
                compositing("backdrop-filter: blur(4px)").backdropReach());
        assertArrayEquals(
                new double[4], compositing("backdrop-filter: grayscale(1)").backdropReach());
        assertArrayEquals(
                new double[4], compositing("backdrop-filter: blur(4px)").reach(), "it draws nothing past");
    }

    @Test
    @DisplayName("takes the -webkit- name too, and none is nothing")
    void spellings() {
        assertEquals(
                List.of(new FilterFunction.Blur(2)),
                compositing("-webkit-backdrop-filter: blur(2px)").backdropFilter());
        assertFalse(compositing("backdrop-filter: none").hasBackdrop());
        assertTrue(ComputedStyle.isProperty("backdrop-filter"));
    }

    @Test
    @DisplayName("is dropped whole for a function it cannot read")
    void dropped() {
        assertFalse(compositing("backdrop-filter: url(#soften)").hasBackdrop());
    }
}
