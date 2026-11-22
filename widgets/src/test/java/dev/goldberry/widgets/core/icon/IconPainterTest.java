package dev.goldberry.widgets.core.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;

/// An icon's box is drawn through a painter that is a value, so an icon that
/// did not change is a box that did not change, however often the tree around
/// it is rendered.
class IconPainterTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(200, 200, 1.0f, 0);
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
    }

    @Test
    @DisplayName("two renders of one icon in one colour hand out equal painters")
    void equalPainters() {
        var first = IconPaint.render(Icon.bundled("plus", 16), ComputedStyle.INITIAL);
        var second = IconPaint.render(Icon.find("plus", 16).orElseThrow(), ComputedStyle.INITIAL);
        assertEquals(first.painting(), second.painting());
        assertNotEquals(
                first.painting(),
                IconPaint.render(Icon.bundled("minus", 16), ComputedStyle.INITIAL)
                        .painting());
    }

    @Test
    @DisplayName("a dozen icons rendered again damage nothing, and one that changed damages only itself")
    void iconsRenderedAgainAreNotDamage() {
        try (var render = RenderTree.create()) {
            render.update(target.frame(), board(-1));
            render.damage(target.frame());

            // The same board built again from scratch, as an animation elsewhere
            // in the window makes every frame: nothing here moved.
            render.update(target.frame(), board(-1));
            assertEquals(List.of(), render.damage(target.frame()));

            // One icon's colour changes: one rectangle, the size of that icon.
            render.update(target.frame(), board(3));
            var damage = render.damage(target.frame());
            assertEquals(1, damage.size(), damage.toString());
            var only = damage.getFirst();
            assertTrue(only.width() <= 16 && only.height() <= 16, only.toString());
        }
    }

    /// Twelve icons down a column, the one at `odd` in another colour.
    private static Box board(int odd) {
        var icons = new Box[12];
        for (var i = 0; i < icons.length; i++) {
            var style = ComputedStyle.INITIAL.color(i == odd ? 0xFFFF0000 : 0xFF000000);
            icons[i] = IconPaint.render(Icon.bundled("plus", 16), style).size(Length.points(16), Length.points(16));
        }
        return Box.of()
                .size(Length.points(200), Length.points(200))
                .direction(FlexDirection.COLUMN)
                .children(icons);
    }
}
