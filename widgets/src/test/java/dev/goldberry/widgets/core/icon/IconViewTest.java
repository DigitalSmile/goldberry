package dev.goldberry.widgets.core.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.registry.ActionRegistry;
import dev.goldberry.bind.registry.BindingRegistry;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.markup.Wiring;

/// `icon` — one icon on its own, sized by its box.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#icon).
class IconViewTest {

    private static final int INK = 0xFFFFFFFF;

    private static IconView inflate(String kdl, Icons icons) {
        var wiring = new Wiring(ActionRegistry.lenient(), icons, BindingRegistry.lenient());
        return assertInstanceOf(
                IconView.class,
                Widgets.inflater(wiring).inflate(KdlParser.parse(kdl).getFirst()));
    }

    /// The node a stylesheet selects, which is one of the two parts.
    private static Styled styled(IconView view) {
        return (Styled) new ElementTree(view).root().children().getFirst().widget();
    }

    @Nested
    @DisplayName("finding the icon")
    class Finding {

        @Test
        @DisplayName("a bundled name draws that icon")
        void bundled() {
            var view = new IconView("cloud-upload");

            assertEquals("cloud-upload", view.icon().name());
            assertFalse(view.isMissing());
        }

        @Test
        @DisplayName("a name the set does not have is an empty box with the class missing, not an exception")
        void missing() {
            var view = new IconView("no-such-icon");

            assertTrue(view.isMissing());
            assertEquals(Set.of(IconView.MISSING), styled(view).classes());
        }

        @Test
        @DisplayName("markup looks in the application's registry first, then in the bundled set")
        void registryFirst() {
            var icons = Icons.lenient().bind("home", Icon.bundled("house", 16));

            assertEquals("house", inflate("icon \"home\"", icons).icon().name());
            assertEquals("plus", inflate("icon \"plus\"", icons).icon().name());
            assertTrue(inflate("icon \"nowhere\"", icons).isMissing());
        }

        @Test
        @DisplayName("a strict registry that lacks the name still falls through to the set")
        void strictFallsThrough() {
            assertEquals("plus", inflate("icon \"plus\"", Icons.strict()).icon().name());
        }

        @Test
        @DisplayName("an icon node with no name draws nothing")
        void noName() {
            assertTrue(inflate("icon", Icons.none()).isMissing());
        }
    }

    @Nested
    @DisplayName("what a reader is told")
    class Reading {

        @Test
        @DisplayName("an icon with no name is decorative and has no semantics")
        void decorative() {
            var part = styled(new IconView("plus"));

            assertEquals("icon", part.cssType());
            assertFalse(part instanceof Semantics);
        }

        @Test
        @DisplayName("an icon given a name is a figure with that name")
        void named() {
            var view = inflate("icon \"circle-alert\" name=\"Not signed in\" id=\"warn\"", Icons.none());
            var part = assertInstanceOf(Semantics.class, styled(view));

            assertEquals(Role.FIGURE, part.role());
            assertEquals("Not signed in", part.accessibleName());
            assertEquals("warn", styled(view).id());
        }

        @Test
        @DisplayName("the document's classes land on the node")
        void classes() {
            var view = new IconView("plus").withAttributes(Attributes.NONE.classes("small"));

            assertEquals(Set.of("small"), styled(view).classes());
        }
    }

    @Nested
    @DisplayName("painting")
    class Painting {

        /// Paints `icon` into a `width`×`height` box and hands back the target.
        private static TestFrames.Target painted(Icon icon, int width, int height) {
            var target = TestFrames.of(width, height, 1.0f);
            try {
                IconPaint.paint(target.frame(), new LogicalSize(width, height), icon, INK);
            } finally {
                target.end();
            }
            return target;
        }

        /// The inked rectangle: left, top, right, bottom, exclusive at the far edges.
        private static int[] inked(TestFrames.Target target, int width, int height) {
            int left = width, top = height, right = 0, bottom = 0;
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    if (target.alphaAt(x, y) > 0) {
                        left = Math.min(left, x);
                        top = Math.min(top, y);
                        right = Math.max(right, x + 1);
                        bottom = Math.max(bottom, y + 1);
                    }
                }
            }
            return new int[] {left, top, right, bottom};
        }

        @Test
        @DisplayName("the outline is scaled to the box, whatever size the icon was built at")
        void scaledToTheBox() {
            RendererRequirement.enforce();
            // `square` is a 18-unit square in a 24 grid, from 3 to 21: at 48 it
            // spans 6 to 42, give or take half a stroke.
            var box = inked(painted(Icon.bundled("square", 16), 48, 48), 48, 48);

            assertEquals(6, box[0], 2);
            assertEquals(6, box[1], 2);
            assertEquals(42, box[2], 2);
            assertEquals(42, box[3], 2);
        }

        @Test
        @DisplayName("a box that is not square fits the smaller side and centres along the other")
        void centred() {
            RendererRequirement.enforce();
            var box = inked(painted(Icon.bundled("square", 24), 96, 48), 96, 48);

            // 48 square, centred in 96: from 24 to 72, the square at 30 to 66.
            assertEquals(30, box[0], 2);
            assertEquals(66, box[2], 2);
            assertEquals(6, box[1], 2);
            assertEquals(42, box[3], 2);
        }

        @Test
        @DisplayName("a missing icon renders a plain box with no painter")
        void missingPaintsNothing() {
            assertNull(IconPaint.render(null, ComputedStyle.INITIAL).painting());
        }
    }

    @Test
    @DisplayName("it is a value: two views of the same icon are equal")
    void value() {
        var icon = Icon.bundled("plus", 24);
        Widget one = new IconView(icon);

        assertEquals(one, new IconView(icon));
    }
}
