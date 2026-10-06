package dev.goldberry.widgets.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.text.Text;

/// A panel placed over a `stack` with `position: absolute` and sized by
/// `max-width` lays its wrapped text out line under line, as it does sized by
/// `width`.
///
/// The scene is a game's log panel, written the way it
/// was reported: a column at `top: 20px; left: 16px` holding a line that
/// wraps at 220 and the line after it.
///
/// Read more: [Stack](https://goldberry.dev/docs/layout/stack.html).
@DisplayName("an absolute panel in a stack")
class StackMaxWidthTest {

    private static final String LONG = "The opponent plays Slave Hunter on their melee row.";
    private static final String NEXT = "Round 2.";

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// Where the two lines were laid out, the long one first.
    private static List<HitTest.Region> lines(String sizing) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "#log { position: absolute; top: 20px; left: 16px; " + sizing + ": 220px; }"));
        var log = new Column(
                List.of(new Text(LONG, Attributes.NONE.id("long")), new Text(NEXT, Attributes.NONE.id("next"))),
                Attributes.NONE.id("log"));
        try (var session =
                Offscreen.of(300, 160).stylesheets(sheets).session(new Stack(List.of(log), Attributes.NONE))) {
            session.frame();
            return List.of(region(session.regions(), "long"), region(session.regions(), "next"));
        }
    }

    private static HitTest.Region region(List<HitTest.Region> regions, String id) {
        return regions.stream()
                .filter(region -> region.owner() instanceof Element element && id.equals(element.id()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("lays the lines out as a panel sized by width does: the long one wrapped, the next under it")
    void asWidthDoes() {
        var capped = lines("max-width");
        var sized = lines("width");
        for (var i = 0; i < 2; i++) {
            assertEquals(sized.get(i).top(), capped.get(i).top(), 0.5, "line " + i + " top");
            assertEquals(sized.get(i).height(), capped.get(i).height(), 0.5, "line " + i + " height");
        }
        var wrapped = capped.get(0);
        var next = capped.get(1);
        assertTrue(wrapped.height() > next.height() * 1.5, "the long line wrapped: " + wrapped);
        assertEquals(wrapped.top() + wrapped.height(), next.top(), 1.0, "and the next starts under it");
    }
}
