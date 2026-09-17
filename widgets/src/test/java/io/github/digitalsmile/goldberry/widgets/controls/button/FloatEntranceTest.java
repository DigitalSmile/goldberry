package io.github.digitalsmile.goldberry.widgets.controls.button;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// `design-system.md` §1.7's `button[float]`: in with `opacity` and `scale`
/// 0.9→1, on `base` — the entrance ADR-0347 could not build and `@starting-style`
/// can ([ADR-0352]).
class FloatEntranceTest {

    private Clock.Virtual clock;
    private WidgetRenderer renderer;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        clock = Clock.virtual();
        renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get()).clock(clock);
    }

    private static Button floating() {
        return new Button("Compose", () -> {}).withAttributes(Attributes.NONE.classes(Button.FLOAT));
    }

    private static double scaleOf(Box box) {
        if (box.transform().isNone()) {
            return 1;
        }
        if (box.transform().functions().getFirst() instanceof Transform.Function.Scale(var x, var _)) {
            return x;
        }
        throw new AssertionError("expected a scale, got " + box.transform());
    }

    @Test
    @DisplayName("a floating button's first frame is transparent and nine tenths of its size")
    void entersFromTheStartingStyle() {
        var box = renderer.render(new ElementTree(floating()));

        assertEquals(0, box.opacity(), 1e-9);
        assertEquals(0.9, scaleOf(box), 1e-9);
        assertTrue(renderer.isAnimating());
    }

    @Test
    @DisplayName("and it arrives on base, 160 ms, at full size and full strength")
    void arrives() {
        var tree = new ElementTree(floating());
        renderer.render(tree);
        clock.advance(80);
        var midway = renderer.render(tree);
        clock.advance(80);
        var arrived = renderer.render(tree);

        assertTrue(midway.opacity() > 0 && midway.opacity() < 1, "midway is between: " + midway.opacity());
        assertEquals(1, arrived.opacity(), 1e-9);
        assertEquals(1, scaleOf(arrived), 1e-9);
        assertFalse(renderer.isAnimating());
    }

    @Test
    @DisplayName("a button that does not float appears at rest, as every button always has")
    void anOrdinaryButtonDoesNotEnter() {
        var box = renderer.render(new ElementTree(new Button("Save", () -> {})));

        assertEquals(1, box.opacity(), 1e-9);
        assertFalse(renderer.isAnimating());
    }
}
