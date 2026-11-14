package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.value.ViewTransition;
import dev.goldberry.motion.Easing;
import dev.goldberry.paint.Box;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The timing a named box moves with, which the renderer puts on its name: the
/// box's own `transform` transition, the default otherwise, and none under
/// reduced motion.
@DisplayName("a view transition's timing")
class ViewTransitionTimingTest {

    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style);
        }
    }

    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    private ViewTransition timing(String css, boolean reduced) {
        var renderer = new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)), font)
                .reducedMotion(reduced);
        return renderer.render(new ElementTree(new Plate(Attributes.NONE)))
                .transform()
                .viewTransition();
    }

    @Test
    @DisplayName("is the box's own transition for transform")
    void fromTheTransition() {
        var timed = timing("plate { view-transition-name: tile; transition: transform 400ms linear }", false);

        assertEquals(new ViewTransition("tile", 400, Easing.LINEAR), timed);
    }

    @Test
    @DisplayName("is View Transitions' default when the box transitions nothing, or something else")
    void byDefault() {
        assertEquals(ViewTransition.named("tile"), timing("plate { view-transition-name: tile }", false));
        assertEquals(
                ViewTransition.named("tile"),
                timing("plate { view-transition-name: tile; transition: opacity 400ms linear }", false));
    }

    @Test
    @DisplayName("is instant when motion is reduced, whatever the sheet says")
    void reduced() {
        assertEquals(
                0,
                timing("plate { view-transition-name: tile; transition: transform 400ms }", true)
                        .durationMillis());
        assertEquals(0, timing("plate { view-transition-name: tile }", true).durationMillis());
    }

    @Test
    @DisplayName("is nothing for a box with no name")
    void unnamed() {
        assertNull(timing("plate { transition: transform 400ms }", false));
    }
}
