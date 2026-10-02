package dev.goldberry.widgets.controls.button;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.Property;
import dev.goldberry.css.Theme;
import dev.goldberry.css.value.Transform;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;

/// The design system's entrance for `button[float]`: in with `opacity` and
/// `scale` 0.9→1, on `base`, and out on `fast`. A first frame starts no
/// transition, so the float shipped without its entrance until `@starting-style`
/// gave it a style to arrive from; this holds that the arrival still runs.
///
/// Read more: [Motion](https://goldberry.dev/docs/guide/design-system.html#motion).
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
    @DisplayName("and it leaves on fast, the entrance reversed, when `leaving` is put on it")
    void leaves() {
        var leaving = Property.of(false);
        var slot = new FloatSlot(floating(), leaving);
        var tree = new ElementTree(slot);
        renderer.render(tree);
        clock.advance(200);
        assertEquals(1, renderer.render(tree).opacity(), 1e-9);

        leaving.set(true);
        tree.flush();
        var start = renderer.render(tree);
        clock.advance(100);
        var gone = renderer.render(tree);

        assertEquals(1, start.opacity(), 1e-9, "a transition starts from where the button is");
        assertEquals(0, gone.opacity(), 1e-9);
        assertEquals(0.9, scaleOf(gone), 1e-9);
        assertFalse(renderer.isAnimating(), "and is done in 100 ms, faster than the 160 it came in on");
    }

    @Test
    @DisplayName("a button that does not float appears at rest, as every button always has")
    void anOrdinaryButtonDoesNotEnter() {
        var box = renderer.render(new ElementTree(new Button("Save", () -> {})));

        assertEquals(1, box.opacity(), 1e-9);
        assertFalse(renderer.isAnimating());
    }
}
