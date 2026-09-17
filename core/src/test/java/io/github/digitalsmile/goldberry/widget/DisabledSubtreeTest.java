package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A disabled container disables what is inside it — [ADR-0379].
///
/// `core-widgets.md`'s widget contract has always said so, and the router has
/// enforced the *input* half since ADR-0077: a button inside a disabled `form`
/// takes no click, whatever it says about itself. What it did not reach was the
/// cascade, so the same button was drawn as though it were available — and a
/// stylesheet could say nothing about a disabled subtree, because nothing in one
/// matched `:disabled`.
class DisabledSubtreeTest {

    /// A container that is disabled and draws nothing of its own — a `form`, or
    /// a `group-box`, neither of which is built yet.
    private record Panel(boolean disabled, List<Widget> children, Attributes attributes)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "panel";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public boolean isDisabled() {
            return disabled;
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }

    /// A control that knows nothing about its container, which is the point: it
    /// is not a button's business to know that the form around it is off.
    private record Control(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "control";
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

    private static final int GREY = 0xFF808080;

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

    private static WidgetRenderer renderer(Font font) {
        return new WidgetRenderer(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                        control { background: #ffffff }
                        control:disabled { background: #808080 }
                        panel:disabled { opacity: 0.45 }
                        :disabled :disabled { opacity: 1 }
                        """)), font);
    }

    /// The element under `root` with `type`, in walk order.
    private static Element find(Element root, String type) {
        if (type.equals(root.type())) {
            return root;
        }
        for (var child : root.children()) {
            var found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("a control inside a disabled container is drawn disabled")
    void disabledContainerReachesTheCascade() {
        var tree = new ElementTree(new Panel(true, List.of(new Control(Attributes.NONE)), Attributes.NONE));
        var box = renderer(font).render(tree);

        var control = find(tree.root(), "control");
        assertTrue(control.hasState(PseudoClass.DISABLED), "the control matches `:disabled`");
        assertEquals(GREY, box.children().getFirst().background(), "so its `:disabled` rule applied");
    }

    @Test
    @DisplayName("and the fade belongs to the outermost of them, because opacity multiplies")
    void theFadeIsNotAppliedTwice() {
        var tree = new ElementTree(new Panel(true, List.of(new Control(Attributes.NONE)), Attributes.NONE));
        var box = renderer(font).render(tree);

        assertEquals(0.45, box.opacity(), 0.001, "the container fades");
        assertEquals(1.0, box.children().getFirst().opacity(), 0.001, "and the control inside it does not fade again");
    }

    @Test
    @DisplayName("a container that is not disabled leaves its children alone")
    void enabledContainerChangesNothing() {
        var tree = new ElementTree(new Panel(false, List.of(new Control(Attributes.NONE)), Attributes.NONE));
        var box = renderer(font).render(tree);

        assertFalse(find(tree.root(), "control").hasState(PseudoClass.DISABLED));
        assertEquals(0xFFFFFFFF, box.children().getFirst().background());
    }

    @Test
    @DisplayName("the flag is taken back when the container is enabled again")
    void itIsNotSticky() {
        // A pseudo-class that was set and never cleared is the classic way an
        // inherited state goes wrong: the form is enabled again and its controls
        // stay grey for ever.
        var tree = new ElementTree(new Panel(true, List.of(new Control(Attributes.NONE)), Attributes.NONE));
        var renderer = renderer(font);
        renderer.render(tree);

        var enabled = new ElementTree(new Panel(false, List.of(new Control(Attributes.NONE)), Attributes.NONE));
        renderer.render(enabled);
        assertFalse(find(enabled.root(), "control").hasState(PseudoClass.DISABLED));
    }
}
